/*
 * Gridify Server
 * Copyright (C) 2019 Kamax Sarl
 *
 * https://www.kamax.io/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.kamax.gridify.server.core.store.postgres;

import com.google.gson.JsonElement;
import io.kamax.gridify.server.config.StorageConfig;
import io.kamax.gridify.server.core.auth.Credentials;
import io.kamax.gridify.server.core.auth.SecureCredentials;
import io.kamax.gridify.server.core.channel.ChannelDao;
import io.kamax.gridify.server.core.channel.event.ChannelEvent;
import io.kamax.gridify.server.core.identity.*;
import io.kamax.gridify.server.core.identity.store.local.LocalAuthIdentityStore;
import io.kamax.gridify.server.core.store.*;
import io.kamax.gridify.server.exception.ObjectNotFoundException;
import io.kamax.gridify.server.util.GsonUtil;
import io.kamax.gridify.server.util.KxLog;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PostgreSQLDataStore implements DataStore, IdentityStore {

    private static final Logger log = KxLog.make(MethodHandles.lookup().lookupClass());

    private interface ConnFunction<T, R> {

        R run(T connection) throws SQLException;

    }

    private interface ConnConsumer<T> {

        void run(T conn) throws SQLException;

    }

    private interface StmtFunction<T, R> {

        R run(T stmt) throws SQLException;
    }

    private interface StmtConsumer<T> {

        void run(T stmt) throws SQLException;
    }

    private final SqlConnectionPool pool;

    public PostgreSQLDataStore(StorageConfig cfg) {
        this(new SqlConnectionPool(cfg));
    }

    private PostgreSQLDataStore(SqlConnectionPool pool) {
        this.pool = pool;
        withConnConsumer(conn -> conn.isValid(1000));
        log.info("Connected");

        withConnConsumer(conn -> {
            Statement stmt = conn.createStatement();
            stmt.execute("CREATE TABLE IF NOT EXISTS schema (version bigint NOT NULL)");
            conn.setAutoCommit(false);

            long version = getSchemaVersion();
            log.info("Schema version: {}", version);

            try (InputStream elIs = PostgreSQLDataStore.class.getResourceAsStream("/store/postgres/schema")) {
                if (Objects.isNull(elIs)) {
                    throw new IllegalStateException("No schema found for PostgreSQL. Please report this issue.");
                }

                List<String> schemas = new ArrayList<>();
                schemas.add("000000.sql");
                schemas.add("000001.sql");
                //LineIterator it = IOUtils.lineIterator(elIs, StandardCharsets.UTF_8);
                Iterator<String> it = schemas.listIterator();
                log.debug("Schemas auto-discovery:");
                while (it.hasNext()) {
                    String sql = it.next();
                    if (!StringUtils.endsWith(sql, ".sql")) {
                        continue;
                    }
                    log.info("Processing schema update: {}", sql);
                    String[] els = StringUtils.substringBeforeLast(sql, ".").split("-", 2);
                    if (els.length < 1) {
                        log.warn("Skipping invalid schema update name format: {}", sql);
                    }

                    long elV = Long.parseLong(els[0]);
                    log.debug("Schema {}: version {}", sql, elV);
                    if (elV <= version) {
                        log.debug("Skipping {}", sql);
                        continue;
                    }

                    try (InputStream schemaIs = PostgreSQLDataStore.class.getResourceAsStream("/store/postgres/schema/" + sql)) {
                        String update = IOUtils.toString(Objects.requireNonNull(schemaIs), StandardCharsets.UTF_8);
                        stmt.execute(update);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    if (stmt.executeUpdate("INSERT INTO schema (version) VALUES (" + elV + ")") != 1) {
                        throw new RuntimeException("Could not update schema version");
                    }

                    log.info("Updated schema to version {}", elV);
                }
            } catch (IOException e) {
                log.warn("Schema autodiscovery failed");
            }
            conn.commit();
            log.info("DB schema version: {}", getSchemaVersion());
        });
    }

    private <R> R withConnFunction(ConnFunction<Connection, R> function) {
        try (Connection conn = pool.get()) {
            conn.setAutoCommit(true);
            return function.run(conn);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void withConnConsumer(ConnConsumer<Connection> consumer) {
        try (Connection conn = pool.get()) {
            consumer.run(conn);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private <R> R withStmtFunction(String sql, Connection conn, StmtFunction<PreparedStatement, R> function) {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            return function.run(stmt);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private <R> R withStmtFunction(String sql, StmtFunction<PreparedStatement, R> function) {
        return withConnFunction(conn -> withStmtFunction(sql, conn, function));
    }

    private void withStmtConsumer(String sql, StmtConsumer<PreparedStatement> c) {
        withConnConsumer(conn -> withStmtConsumer(sql, conn, c));
    }

    private void withStmtConsumer(String sql, Connection conn, StmtConsumer<PreparedStatement> c) {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            c.run(stmt);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void withTransaction(Consumer<Connection> c) {
        withConnConsumer(conn -> {
            try {
                conn.setAutoCommit(false);
                c.accept(conn);
                conn.setAutoCommit(true);
            } catch (RuntimeException e) {
                conn.rollback();
                throw e;
            }
        });
    }

    private <R> R withTransactionFunction(Function<Connection, R> c) {
        return withConnFunction(conn -> {
            try {
                conn.setAutoCommit(false);
                R v = c.apply(conn);
                conn.setAutoCommit(true);
                return v;
            } catch (RuntimeException e) {
                conn.rollback();
                throw e;
            }
        });
    }

    private long getSchemaVersion() {
        return withStmtFunction("SELECT * FROM schema ORDER BY version DESC LIMIT 1", stmt -> {
            ResultSet rSet = stmt.executeQuery();
            if (!rSet.next()) {
                return -1L;
            }

            return rSet.getLong("version");
        });
    }

    private Optional<ChannelDao> findChannel(ResultSet rSet) throws SQLException {
        if (!rSet.next()) {
            return Optional.empty();
        }

        ChannelDao dao = makeChannel(rSet);
        return Optional.of(dao);
    }

    private ChannelDao makeChannel(ResultSet rSet) throws SQLException {
        return new ChannelDao(rSet.getLong("lid"), rSet.getString("network"), rSet.getString("id"), rSet.getString("version"));
    }

    @Override
    public void setConfig(String id, JsonElement value) {
        String sql = "INSERT INTO config (name, data) VALUES (?,?::jsonb) " +
                "ON CONFLICT ON CONSTRAINT config_name_uq DO UPDATE SET data = EXCLUDED.data";
        withStmtConsumer(sql, stmt -> {
            stmt.setString(1, id);
            stmt.setString(2, GsonUtil.toJson(value));
            int rc = stmt.executeUpdate();
            if (rc != 1) {
                throw new IllegalStateException("Config item " + id + ": DB updated " + rc + " rows. 1 expected");
            }
        });
    }

    @Override
    public JsonElement getConfig(String id) {
        String sql = "SELECT data FROM config WHERE name = ?";
        return withStmtFunction(sql, stmt -> {
            stmt.setString(1, id);
            try (ResultSet rSet = stmt.executeQuery()) {
                if (!rSet.next()) {
                    throw new ObjectNotFoundException("Config", id);
                }

                String data = rSet.getString(1);
                return GsonUtil.parse(data);
            }
        });
    }

    @Override
    public DomainDao saveDomain(DomainDao dao) {
        if (Objects.isNull(dao.getLocalId())) {
            return insertDomain(dao);
        } else {
            updateDomain(dao);
            return dao;
        }
    }

    @Override
    public void deleteDomain(DomainDao dao) {
        String sql = "DELETE FROM domains WHERE network = ? AND domain = ?";
        withStmtConsumer(sql, stmt -> {
            stmt.setString(1, dao.getNetwork());
            stmt.setString(2, dao.getDomain());
            int rc = stmt.executeUpdate();
            if (rc != 1) {
                throw new IllegalStateException("Domain # " + dao.getLocalId() + ": DB deleted " + rc + " rows. 1 expected");
            }
        });
    }

    public DomainDao insertDomain(DomainDao dao) {
        String sql = "INSERT INTO domains (network,domain,host,config,properties) VALUES (?,?,?,?::jsonb,?::jsonb) RETURNING lid";
        long lid = withStmtFunction(sql, stmt -> {
            stmt.setString(1, dao.getNetwork());
            stmt.setString(2, dao.getDomain());
            stmt.setString(3, dao.getHost());
            stmt.setString(4, GsonUtil.toJson(dao.getProperties()));
            stmt.setString(5, GsonUtil.toJson(dao.getProperties()));
            try (ResultSet rSet = stmt.executeQuery()) {
                if (!rSet.next()) {
                    throw new IllegalStateException("Inserted domain " + dao.getNetwork() + ":" + dao.getDomain() + " in stream but got no SID back");
                }
                return rSet.getLong(1);
            }
        });
        dao.setLocalId(lid);
        return dao;
    }

    public void updateDomain(DomainDao dao) {
        String sql = "UPDATE domains SET host = ?, config = ?::jsonb, properties = ?::jsonb WHERE lid = ?";
        withStmtConsumer(sql, stmt -> {
            stmt.setString(1, dao.getHost());
            stmt.setString(2, GsonUtil.toJson(dao.getConfig()));
            stmt.setString(3, GsonUtil.toJson(dao.getProperties()));
            stmt.setLong(4, dao.getLocalId());
            int rc = stmt.executeUpdate();
            if (rc != 1) {
                throw new IllegalStateException("Domain # " + dao.getLocalId() + ": DB updated " + rc + " rows. 1 expected");
            }
        });
    }

    @Override
    public List<DomainDao> listDomains(String network) {
        String sql = "SELECT * FROM domains WHERE network = ?";
        return withStmtFunction(sql, stmt -> {
            stmt.setString(1, network);
            try (ResultSet rSet = stmt.executeQuery()) {
                List<DomainDao> daos = new ArrayList<>();
                while (rSet.next()) {
                    DomainDao dao = new DomainDao();
                    dao.setLocalId(rSet.getLong("lid"));
                    dao.setNetwork(rSet.getString("network"));
                    dao.setDomain(rSet.getString("domain"));
                    dao.setHost(rSet.getString("host"));
                    dao.setConfig(GsonUtil.parseObj(rSet.getString("config")));
                    dao.setProperties(GsonUtil.parseObj(rSet.getString("properties")));
                    daos.add(dao);
                }
                return daos;
            }
        });
    }

    @Override
    public List<ChannelDao> listChannels() {
        String sql = "SELECT * FROM channels";
        return withStmtFunction(sql, stmt -> {
            List<ChannelDao> channels = new ArrayList<>();

            ResultSet rSet = stmt.executeQuery();
            while (rSet.next()) {
                channels.add(new ChannelDao(rSet.getLong("lid"), rSet.getString("network"), rSet.getString("id"), rSet.getString("version")));
            }

            return channels;
        });
    }

    @Override
    public List<ChannelDao> listChannels(String network) {
        String sql = "SELECT * FROM channels WHERE network = ?";
        return withStmtFunction(sql, stmt -> {
            stmt.setString(1, network);

            List<ChannelDao> channels = new ArrayList<>();
            ResultSet rSet = stmt.executeQuery();
            while (rSet.next()) {
                channels.add(new ChannelDao(rSet.getLong("lid"), rSet.getString("network"), rSet.getString("id"), rSet.getString("version")));
            }

            return channels;
        });
    }

    @Override
    public Optional<ChannelDao> findChannel(long cLid) {
        String sql = "SELECT * FROM channels WHERE lid = ?";
        return withStmtFunction(sql, stmt -> {
            stmt.setLong(1, cLid);
            return findChannel(stmt.executeQuery());
        });
    }

    @Override
    public Optional<ChannelDao> findChannel(String network, String networkId) {
        String sql = "SELECT * FROM channels WHERE network = ? AND id = ?";
        return withStmtFunction(sql, stmt -> {
            stmt.setString(1, network);
            stmt.setString(2, networkId);
            return findChannel(stmt.executeQuery());
        });
    }

    @Override
    public long addToStream(long eLid) {
        String sql = "INSERT INTO channel_event_stream (lid) VALUES (?) RETURNING sid";
        return withStmtFunction(sql, stmt -> {
            stmt.setLong(1, eLid);
            try (ResultSet rSet = stmt.executeQuery()) {
                if (!rSet.next()) {
                    throw new IllegalStateException("Inserted event " + eLid + " in stream but got no SID back");
                }
                return rSet.getLong(1);
            }
        });
    }

    @Override
    public long getStreamPosition() {
        String sql = "SELECT MAX(sid) FROM channel_event_stream";
        return withStmtFunction(sql, stmt -> {
            ResultSet rSet = stmt.executeQuery();
            if (!rSet.next()) {
                return 0L;
            }

            return rSet.getLong(1);
        });
    }

    @Override
    public ChannelDao saveChannel(ChannelDao ch) {
        String sql = "INSERT INTO channels (network,id,version) VALUES (?,?,?) RETURNING lid";
        return withStmtFunction(sql, stmt -> {
            stmt.setString(1, ch.getNetwork());
            stmt.setString(2, ch.getId());
            stmt.setString(3, ch.getVersion());
            ResultSet rSet = stmt.executeQuery();

            if (!rSet.next()) {
                throw new IllegalStateException("Inserted channel " + ch.getId() + " but got no LID back");
            }

            long sid = rSet.getLong(1);
            return new ChannelDao(sid, ch.getNetwork(), ch.getId(), ch.getVersion());
        });
    }

    private long insertEvent(ChannelEvent ev) {
        String sql = "INSERT INTO channel_events (id,channel_lid,meta,extra,data) VALUES (?,?,?::jsonb,?::jsonb,?::jsonb) RETURNING lid";
        return withStmtFunction(sql, stmt -> {
            stmt.setString(1, ev.getId());
            stmt.setLong(2, ev.getChannelSid());
            stmt.setString(3, GsonUtil.toJson(ev.getMeta()));
            stmt.setString(4, GsonUtil.toJson(ev.getExtra()));
            stmt.setString(5, GsonUtil.toJson(ev.getData()));
            ResultSet rSet = stmt.executeQuery();
            if (!rSet.next()) {
                throw new IllegalStateException("Inserted channel event but got no LID back");
            }

            return rSet.getLong("lid");
        });
    }

    private void updateEvent(ChannelEvent ev) {
        String sql = "UPDATE channel_events SET meta = ?::jsonb, extra = ?::jsonb WHERE lid = ?";
        withStmtConsumer(sql, stmt -> {
            stmt.setString(1, GsonUtil.toJson(ev.getMeta()));
            stmt.setString(2, GsonUtil.toJson(ev.getExtra()));
            stmt.setLong(3, ev.getLid());
            int rc = stmt.executeUpdate();
            if (rc != 1) {
                throw new IllegalStateException("Channel Event # " + ev.getLid() + ": DB updated " + rc + " rows. 1 expected");
            }
        });
    }

    @Override
    public ChannelEvent saveEvent(ChannelEvent ev) {
        if (ev.hasLid()) {
            updateEvent(ev);
        } else {
            long sid = insertEvent(ev);
            ev.setLid(sid);
        }

        return ev;
    }

    @Override
    public ChannelEvent getEvent(String cId, String eId) throws IllegalStateException {
        return findEvent(cId, eId).orElseThrow(() -> new ObjectNotFoundException("Event", eId));
    }

    @Override
    public ChannelEvent getEvent(long eSid) {
        return findEvent(eSid).orElseThrow(() -> new ObjectNotFoundException("Event", Long.toString(eSid)));
    }

    @Override
    public String getEventId(long eLid) {
        return withStmtFunction("SELECT id FROM channel_events WHERE lid = ?", stmt -> {
            stmt.setLong(1, eLid);
            ResultSet rSet = stmt.executeQuery();
            if (!rSet.next()) {
                throw new ObjectNotFoundException("Event", Long.toString(eLid));
            }

            return rSet.getString("id");
        });
    }

    @Override
    public long getEventTid(long cLid, String eId) {
        String sql = "SELECT * FROM channel_events e LEFT JOIN channel_event_stream s ON s.lid = e.lid WHERE e.channel_lid = ? AND e.id = ?";
        return withStmtFunction(sql, stmt -> {
            stmt.setLong(1, cLid);
            stmt.setString(2, eId);
            ResultSet rSet = stmt.executeQuery();
            if (!rSet.next()) {
                throw new ObjectNotFoundException("Event", eId);
            }

            return make(rSet).getSid();
        });
    }

    @Override
    public Optional<Long> findEventLid(String cId, String eId) throws ObjectNotFoundException {
        String sql = "SELECT e.lid FROM channels c JOIN channel_events e ON e.channel_lid = c.lid WHERE c.id = ? AND e.id = ?";
        return withStmtFunction(sql, stmt -> {
            stmt.setString(1, cId);
            stmt.setString(2, eId);
            ResultSet rSet = stmt.executeQuery();
            if (!rSet.next()) {
                return Optional.empty();
            }

            return Optional.of(rSet.getLong("lid"));
        });
    }

    @Override
    public List<ChannelDao> searchForRoomsInUserEvents(String network, String type, String stateKey) {
        String sql = String.join(" ",
                "SELECT DISTINCT c.*",
                "FROM channel_events e",
                "JOIN channels c ON e.channel_lid = c.lid",
                "WHERE network = ?",
                "  AND meta->>'processed' = 'true'",
                "  AND meta->>'allowed' = 'true'",
                "  AND DATA->>'type' = ?",
                "  AND DATA->>'state_key' = ?");

        return withStmtFunction(sql, stmt -> {
            stmt.setString(1, network);
            stmt.setString(2, type);
            stmt.setString(3, stateKey);
            try (ResultSet rSet = stmt.executeQuery()) {
                List<ChannelDao> rooms = new ArrayList<>();
                while (rSet.next()) {
                    rooms.add(makeChannel(rSet));
                }
                return rooms;
            }
        });
    }

    @Override
    public List<ChannelEvent> getNext(long lastSid, long amount) {
        String sql = "SELECT * FROM channel_event_stream s JOIN channel_events e ON s.lid = e.lid WHERE s.sid > ? ORDER BY s.sid ASC LIMIT ?";
        return withStmtFunction(sql, stmt -> {
            stmt.setLong(1, lastSid);
            stmt.setLong(2, amount);
            ResultSet rSet = stmt.executeQuery();

            List<ChannelEvent> events = new ArrayList<>();
            while (rSet.next()) {
                events.add(make(rSet));
            }
            return events;
        });
    }

    @Override
    public List<ChannelEvent> getTimelineNext(long cLid, long lastTid, long amount) {
        String sql = "SELECT * FROM channel_event_stream s JOIN channel_events e ON s.eLid = e.lid WHERE e.channel_lid = ? AND s.sid > ? ORDER BY s.sid ASC LIMIT ?";
        return withStmtFunction(sql, stmt -> {
            stmt.setLong(1, cLid);
            stmt.setLong(2, lastTid);
            stmt.setLong(3, amount);
            ResultSet rSet = stmt.executeQuery();

            List<ChannelEvent> events = new ArrayList<>();
            while (rSet.next()) {
                events.add(make(rSet));
            }
            return events;
        });
    }

    @Override
    public List<ChannelEvent> getTimelinePrevious(long cLid, long lastTid, long amount) {
        String sql = "SELECT * FROM channel_event_stream s JOIN channel_events e ON s.lid = e.lid WHERE e.channel_lid = ? AND s.sid < ? ORDER BY s.sid DESC LIMIT ?";
        return withStmtFunction(sql, stmt -> {
            stmt.setLong(1, cLid);
            stmt.setLong(2, lastTid);
            stmt.setLong(3, amount);
            ResultSet rSet = stmt.executeQuery();

            List<ChannelEvent> events = new ArrayList<>();
            while (rSet.next()) {
                events.add(make(rSet));
            }
            return events;
        });
    }

    private ChannelEvent make(ResultSet rSet) throws SQLException {
        long cLid = rSet.getLong("channel_lid");
        long sid = rSet.getLong("lid");
        String id = rSet.getString("id");
        ChannelEventMeta meta = GsonUtil.parse(rSet.getString("meta"), ChannelEventMeta.class);
        ChannelEvent ev = new ChannelEvent(cLid, sid, id, meta);
        if (ev.getMeta().isPresent()) {
            ev.setData(GsonUtil.parseObj(rSet.getString("data")));
        }
        try {
            ev.setSid(rSet.getLong(rSet.findColumn("sid")));
        } catch (SQLException e) {
            if (log.isTraceEnabled()) {
                log.debug("No column sid");
                log.trace("SID request", e);
            }
        }
        return ev;
    }

    @Override
    public Optional<ChannelEvent> findEvent(String cId, String eId) {
        String sqlChIdToSid = "SELECT lid FROM channels WHERE id = ?";
        String sql = "SELECT * FROM channel_events WHERE id = ? and channel_lid = (" + sqlChIdToSid + ")";
        return withStmtFunction(sql, stmt -> {
            stmt.setString(1, eId);
            stmt.setString(2, cId);
            ResultSet rSet = stmt.executeQuery();
            if (!rSet.next()) {
                return Optional.empty();
            }

            return Optional.of(make(rSet));
        });
    }

    @Override
    public List<ChannelEvent> findEvents(String network, String eventId) {
        return withStmtFunction("SELECT * FROM channel_events ce JOIN channels c ON c.lid = ce.channel_lid WHERE c.network = ? AND ce.id = ?", stmt -> {
            stmt.setString(1, network);
            stmt.setString(2, eventId);
            try (ResultSet rSet = stmt.executeQuery()) {
                List<ChannelEvent> events = new ArrayList<>();
                while (rSet.next()) {
                    events.add(make(rSet));
                }
                return events;
            }
        });
    }

    @Override
    public Optional<ChannelEvent> findEvent(long eLid) {
        return withStmtFunction("SELECT * FROM channel_events ce LEFT JOIN channel_event_stream ces ON ces.lid = ce.lid WHERE ce.lid = ?", stmt -> {
            stmt.setLong(1, eLid);
            ResultSet rSet = stmt.executeQuery();
            if (!rSet.next()) {
                return Optional.empty();
            }

            return Optional.of(make(rSet));
        });
    }

    private void updateExtremities(String type, long cLid, List<Long> toRemove, List<Long> toAdd) {
        withTransaction(conn -> {
            withStmtConsumer("DELETE FROM channel_extremities_" + type + " WHERE event_lid = ?", conn, stmt -> {
                for (long eLid : toRemove) {
                    stmt.setLong(1, eLid);
                    stmt.addBatch();
                }
                stmt.executeBatch();
            });

            withStmtConsumer("INSERT INTO channel_extremities_" + type + " (channel_lid,event_lid) VALUES (?,?)", conn, stmt -> {
                for (long eLid : toAdd) {
                    stmt.setLong(1, cLid);
                    stmt.setLong(2, eLid);
                    stmt.addBatch();
                }
                stmt.executeBatch();
            });
        });
    }

    private List<Long> getExtremities(String type, long cLid) {
        return withStmtFunction("SELECT event_lid FROM channel_extremities_" + type + " WHERE channel_lid = ?", stmt -> {
            stmt.setLong(1, cLid);
            ResultSet rSet = stmt.executeQuery();

            List<Long> extremities = new ArrayList<>();
            while (rSet.next()) {
                extremities.add(rSet.getLong("event_lid"));
            }
            return extremities;
        });
    }

    @Override
    public void updateBackwardExtremities(long cLid, List<Long> toRemove, List<Long> toAdd) {
        updateExtremities("backward", cLid, toRemove, toAdd);
    }

    @Override
    public List<Long> getBackwardExtremities(long cLid) {
        return getExtremities("backward", cLid);
    }

    @Override
    public void updateForwardExtremities(long cLid, List<Long> toRemove, List<Long> toAdd) {
        updateExtremities("forward", cLid, toRemove, toAdd);
    }

    @Override
    public List<Long> getForwardExtremities(long cLid) {
        return getExtremities("forward", cLid);
    }

    @Override
    public long insertIfNew(long cLid, ChannelStateDao state) {
        if (Objects.nonNull(state.getSid()) && state.getSid() > 0) {
            return state.getSid();
        }

        String sql = "INSERT INTO channel_states (channel_lid) VALUES (?) RETURNING lid";
        String evSql = "INSERT INTO channel_state_data (state_lid,event_lid) VALUES (?,?)";

        return withTransactionFunction(conn -> {
            long sSid = withStmtFunction(sql, conn, stmt -> {
                stmt.setLong(1, cLid);
                ResultSet rSet = stmt.executeQuery();
                if (!rSet.next()) {
                    throw new IllegalStateException("Inserted state for channel " + cLid + " but got no LID back");
                }

                return rSet.getLong("lid");
            });

            withStmtConsumer(evSql, conn, stmt -> {
                for (long eSid : state.getEvents().stream().map(ChannelEvent::getLid).collect(Collectors.toList())) {
                    stmt.setLong(1, sSid);
                    stmt.setLong(2, eSid);
                    stmt.addBatch();
                }
                stmt.executeBatch();
            });

            return sSid;
        });
    }

    @Override
    public ChannelStateDao getState(long sid) {
        return withConnFunction(conn -> {
            String evSql = "SELECT e.* from channel_state_data s LEFT JOIN channel_events e ON e.lid = s.event_lid WHERE s.state_lid = ?";
            List<ChannelEvent> events = withStmtFunction(evSql, conn, stmt -> {
                List<ChannelEvent> list = new ArrayList<>();
                stmt.setLong(1, sid);
                ResultSet rSet = stmt.executeQuery();
                while (rSet.next()) {
                    list.add(make(rSet));
                }
                return list;
            });

            return new ChannelStateDao(sid, events);
        });
    }

    @Override
    public void map(long evLid, long stateSid) {
        withStmtConsumer("INSERT INTO channel_event_states (event_lid,state_lid) VALUES (?,?)", stmt -> {
            stmt.setLong(1, evLid);
            stmt.setLong(2, stateSid);
            int rc = stmt.executeUpdate();
            if (rc != 1) {
                throw new IllegalStateException("Channel Event " + evLid + " state: DB inserted " + rc + " rows. 1 expected");
            }
        });
    }

    @Override
    public ChannelStateDao getStateForEvent(long eLid) {
        String sql = "SELECT state_lid FROM channel_event_states WHERE event_lid = ?";
        return getState(withStmtFunction(sql, stmt -> {
            stmt.setLong(1, eLid);
            ResultSet rSet = stmt.executeQuery();
            if (!rSet.next()) {
                throw new ObjectNotFoundException("State for Event", eLid);
            }

            return rSet.getLong("state_lid");
        }));
    }

    @Override
    public boolean hasUsername(String username) {
        return findUserByTreePid(new GenericThreePid("g.id.local.username", username)).isPresent();
    }

    @Override
    public long getUserCount() {
        return withStmtFunction("SELECT COUNT(*) as total FROM identity_users", stmt -> {
            ResultSet rSet = stmt.executeQuery();
            if (!rSet.next()) {
                throw new IllegalStateException("Expected one row for count, but got none");
            }

            return rSet.getLong("total");
        });
    }

    @Override
    public long addUser(String id) {
        String sql = "INSERT INTO identity_users (id) VALUES (?) RETURNING lid";

        return withStmtFunction(sql, stmt -> {
            stmt.setString(1, id);
            ResultSet rSet = stmt.executeQuery();
            if (!rSet.next()) {
                throw new IllegalStateException("Inserted user " + id + " but got no LID back");
            }

            return rSet.getLong("lid");
        });
    }

    @Override
    public void addCredentials(long userLid, Credentials credentials) {
        SecureCredentials secCreds = SecureCredentials.from(credentials);

        String sql = "INSERT INTO identity_user_credentials (user_lid, type, data) VALUES (?,?,?)";
        withStmtConsumer(sql, stmt -> {
            stmt.setLong(1, userLid);
            stmt.setString(2, secCreds.getType());
            stmt.setString(3, secCreds.getData());
            int rc = stmt.executeUpdate();
            if (rc != 1) {
                throw new IllegalStateException("User " + userLid + " credentials state: DB inserted " + rc + " rows. 1 expected");
            }
        });
    }

    @Override
    public SecureCredentials getCredentials(long userLid, String type) {
        String sql = "SELECT * FROM identity_user_credentials WHERE user_lid = ? and type = ?";
        return withStmtFunction(sql, stmt -> {
            stmt.setLong(1, userLid);
            stmt.setString(2, type);
            ResultSet rSet = stmt.executeQuery();
            if (!rSet.next()) {
                throw new ObjectNotFoundException("Credentials of type " + type + " for user LID " + userLid);
            }

            return new SecureCredentials(rSet.getString("type"), rSet.getString("salt"), rSet.getString("data"));
        });
    }

    @Override
    public Optional<UserDao> findUser(long lid) {
        return withStmtFunction("SELECT * FROM identity_users WHERE lid = ?", stmt -> {
            stmt.setLong(1, lid);
            ResultSet rSet = stmt.executeQuery();
            if (!rSet.next()) {
                return Optional.empty();
            }

            UserDao dao = new UserDao();
            dao.setLid(rSet.getLong("lid"));
            dao.setId(rSet.getString("id"));

            return Optional.of(dao);
        });
    }

    @Override
    public Optional<UserDao> findUser(String id) {
        return withStmtFunction("SELECT * FROM identity_users WHERE id = ?", stmt -> {
            stmt.setString(1, id);
            ResultSet rSet = stmt.executeQuery();
            if (!rSet.next()) {
                return Optional.empty();
            }

            UserDao dao = new UserDao();
            dao.setLid(rSet.getLong("lid"));
            dao.setId(rSet.getString("id"));

            return Optional.of(dao);
        });
    }

    @Override
    public Optional<UserDao> findUserByStoreLink(ThreePid storeId) {
        String sql = "SELECT user_lid FROM identity_user_store_links WHERE type = ? AND id = ?";
        return withStmtFunction(sql, stmt -> {
            stmt.setString(1, storeId.getMedium());
            stmt.setString(2, storeId.getAddress());
            ResultSet rSet = stmt.executeQuery();
            if (!rSet.next()) {
                return Optional.empty();
            }

            return findUser(rSet.getLong("user_lid"));
        });
    }

    @Override
    public Optional<UserDao> findUserByTreePid(ThreePid tpid) {
        return withStmtFunction("SELECT u.* FROM identity_user_threepids tpids JOIN identity_users u ON u.lid = tpids.user_lid WHERE medium = ? AND address = ?", stmt -> {
            stmt.setString(1, tpid.getMedium());
            stmt.setString(2, tpid.getAddress());
            ResultSet rSet = stmt.executeQuery();
            if (!rSet.next()) {
                return Optional.empty();
            }

            UserDao dao = new UserDao();
            dao.setLid(rSet.getLong("lid"));
            dao.setId(rSet.getString("id"));
            return Optional.of(dao);
        });
    }

    @Override
    public boolean hasUserAccessToken(String token) {
        return withStmtFunction("SELECT * FROM user_access_tokens WHERE token = ?", stmt -> {
            stmt.setString(1, token);
            ResultSet rSet = stmt.executeQuery();
            return rSet.next();
        });
    }

    @Override
    public void insertUserAccessToken(long uLid, String token) {
        withStmtConsumer("INSERT INTO user_access_tokens (user_lid, token) VALUES (?,?)", stmt -> {
            stmt.setLong(1, uLid);
            stmt.setString(2, token);
            int rc = stmt.executeUpdate();
            if (rc < 1) {
                throw new IllegalStateException("User Access token insert: DB inserted " + rc + " row(s). 1 expected");
            }
        });
    }

    @Override
    public void deleteUserAccessToken(String token) {
        withStmtConsumer("DELETE FROM user_access_tokens WHERE token = ?", stmt -> {
            stmt.setString(1, token);
            int rc = stmt.executeUpdate();
            if (rc < 1) {
                throw new ObjectNotFoundException("User Access Token", "<REDACTED>");
            }
        });
    }

    @Override
    public Optional<String> lookupChannelAlias(String network, String alias) {
        return withStmtFunction("SELECT * FROM channel_aliases WHERE network = ? AND channel_alias = ?", stmt -> {
            stmt.setString(1, network);
            stmt.setString(2, alias);
            ResultSet rSet = stmt.executeQuery();
            if (!rSet.next()) {
                return Optional.empty();
            }

            return Optional.of(rSet.getString("channel_id"));
        });
    }

    @Override
    public Set<String> findChannelAlias(String network, String networkId, String origin) {
        return withStmtFunction("SELECT * FROM channel_aliases WHERE network = ? AND channel_id = ? AND server_id = ?", stmt -> {
            stmt.setString(1, network);
            stmt.setString(2, networkId);
            stmt.setString(3, origin);
            ResultSet rSet = stmt.executeQuery();

            Set<String> aliases = new HashSet<>();
            while (rSet.next()) {
                aliases.add(rSet.getString("channel_alias"));
            }
            return aliases;
        });
    }

    @Override
    public void setAliases(String network, String networkId, String origin, Set<String> aliases) {
        withTransaction(conn -> {
            withStmtConsumer("DELETE FROM channel_aliases WHERE network = ? AND server_id = ?", stmt -> {
                stmt.setString(1, network);
                stmt.setString(2, origin);
                stmt.executeUpdate();
            });
            withStmtConsumer("INSERT INTO channel_aliases (network, channel_id, server_id, channel_alias,auto) VALUES (?,?,?,?, true)", stmt -> {
                for (String alias : aliases) {
                    stmt.setString(1, network);
                    stmt.setString(2, networkId);
                    stmt.setString(3, origin);
                    stmt.setString(4, alias);
                    stmt.addBatch();
                }
                stmt.executeBatch();
            });
        });
    }

    @Override
    public void unmap(String network, String networkId) {
        withStmtConsumer("DELETE FROM channel_aliases WHERE network = ? AND channel_alias = ?", stmt -> {
            stmt.setString(1, network);
            stmt.setString(2, networkId);
            int rc = stmt.executeUpdate();
            if (rc < 1) {
                throw new IllegalStateException("Channel Alias to ID mapping: DB deleted " + rc + " rows. >= 1 expected");
            }
        });
    }

    @Override
    public void linkUserToStore(long userLid, ThreePid storeId) {
        String sql = "INSERT INTO identity_user_store_links (user_lid, type, id) VALUES (?,?,?)";
        withStmtConsumer(sql, stmt -> {
            stmt.setLong(1, userLid);
            stmt.setString(2, storeId.getMedium());
            stmt.setString(3, storeId.getAddress());
            int rc = stmt.executeUpdate();
            if (rc < 1) {
                throw new IllegalStateException("User LID to Store ID insert: DB inserted " + rc + " rows. 1 expected");
            }
        });
    }

    @Override
    public Set<ThreePid> listThreePid(long userLid) {
        Set<ThreePid> list = new HashSet<>();

        String sql = "SELECT * FROM identity_user_threepids WHERE user_lid = ?";
        withStmtConsumer(sql, stmt -> {
            stmt.setLong(1, userLid);
            try (ResultSet rSet = stmt.executeQuery()) {
                while (rSet.next()) {
                    String medium = rSet.getString("medium");
                    String address = rSet.getString("address");
                    list.add(new GenericThreePid(medium, address));
                }
            }
        });

        return list;
    }

    @Override
    public Set<ThreePid> listThreePid(long userLid, String mediumFilter) {
        Set<ThreePid> list = new HashSet<>();

        String sql = "SELECT * FROM identity_user_threepids WHERE user_lid = ? AND medium = ?";
        withStmtConsumer(sql, stmt -> {
            stmt.setLong(1, userLid);
            stmt.setString(2, mediumFilter);
            try (ResultSet rSet = stmt.executeQuery()) {
                while (rSet.next()) {
                    String medium = rSet.getString("medium");
                    String address = rSet.getString("address");
                    list.add(new GenericThreePid(medium, address));
                }
            }
        });

        return list;
    }

    @Override
    public void addThreePid(long userLid, ThreePid tpid) {
        String sql = "INSERT INTO identity_user_threepids(user_lid, medium, address) VALUES (?,?,?)";
        withStmtConsumer(sql, stmt -> {
            stmt.setLong(1, userLid);
            stmt.setString(2, tpid.getMedium());
            stmt.setString(3, tpid.getAddress());
            int rc = stmt.executeUpdate();
            if (rc < 1) {
                throw new IllegalStateException("User 3PID insert: DB inserted " + rc + " rows. 1 expected");
            }
        });
    }

    @Override
    public void removeThreePid(long userLid, ThreePid tpid) {
        String sql = "DELETE FROM identity_user_threepids WHERE user_lid = ? AND medium = ? AND address = ?";
        withStmtConsumer(sql, stmt -> {
            stmt.setLong(1, userLid);
            stmt.setString(2, tpid.getMedium());
            stmt.setString(3, tpid.getAddress());
            int rc = stmt.executeUpdate();
            if (rc < 1) {
                throw new IllegalStateException("User 3PID delete: DB deleted " + rc + " rows. 1 expected");
            }
        });
    }

    @Override
    public void setStreamIdForDestination(String destinationType, String destination, String scope, long streamId) {
        String sql = "INSERT INTO destination_stream_positions (destination_type,destination_id,scope,stream_id) VALUES (?,?,?,?) " +
                "ON CONFLICT ON CONSTRAINT dest_stream_pos DO UPDATE SET stream_id = EXCLUDED.stream_id " +
                "WHERE destination_stream_positions.stream_id < EXCLUDED.stream_id";

        withStmtConsumer(sql, stmt -> {
            stmt.setString(1, destinationType);
            stmt.setString(2, destination);
            stmt.setString(3, scope);
            stmt.setLong(4, streamId);

            int rc = stmt.executeUpdate();
            if (rc != 1) {
                throw new IllegalStateException("Set stream ID for destination: DB set " + rc + " rows. 1 expected");
            }
        });
    }

    // Identity store stuff
    @Override
    public String getType() {
        return "postgres";
    }

    @Override
    public AuthIdentityStore forAuth() {
        return new LocalAuthIdentityStore(this);
    }

    @Override
    public ProfileIdentityStore forProfile() {
        return uid -> Optional.empty();
    }

}
