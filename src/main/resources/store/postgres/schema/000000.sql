CREATE TABLE config
(
    name text NOT NULL,
    data jsonb,
    CONSTRAINT config_name_uq UNIQUE (name)
);

CREATE TABLE entities
(
    lid bigserial NOT NULL,
    id text NOT NULL,
    type text DEFAULT NULL,
    isLocal boolean NOT NULL,
    CONSTRAINT entities_id_uq UNIQUE (id)
);
CREATE INDEX entities_id_idx
    ON entities(id);
CREATE INDEX entities_type_idx
    ON entities(type);

CREATE TABLE entity_routes
(
    lid bigserial NOT NULL,
    target text NOT NULL,
    notBefore timestamp NOT NULL,
    notAfter timestamp NOT NULL,
    revoked boolean NOT NULL DEFAULT false,
    eventSet jsonb NOT NULL,
    eventUnset jsonb DEFAULT NULL
);

CREATE TABLE domains
(
    lid bigserial NOT NULL,
    network text NOT NULL,
    domain text NOT NULL,
    host text NOT NULL,
    config jsonb,
    properties jsonb,
    CONSTRAINT d_ids UNIQUE (network, host)
);

CREATE TABLE channels
(
    lid bigserial NOT NULL,
    network text NOT NULL,
    id text NOT NULL,
    version text,
    CONSTRAINT c_ids UNIQUE (id, network)
);
CREATE INDEX IF NOT EXISTS c_id_idx
    ON channels(id);
CREATE INDEX IF NOT EXISTS c_net_idx
    ON channels(network);

CREATE TABLE channel_events
(
    lid bigserial NOT NULL,
    channel_lid bigint NOT NULL,
    id text NOT NULL,
    meta jsonb NOT NULL,
    data jsonb,
    CONSTRAINT c_ev_gid UNIQUE(id,channel_lid)
);
CREATE INDEX IF NOT EXISTS c_ev_id_idx
    ON channel_events(id);
CREATE INDEX IF NOT EXISTS c_ev_cid_idx
    ON channel_events(channel_lid);

CREATE TABLE channel_states
(
    lid bigserial NOT NULL,
    channel_lid bigint NOT NULL
);

CREATE TABLE channel_state_data
(
    state_lid bigint NOT NULL,
    event_lid bigint NOT NULL
);

CREATE TABLE channel_event_states
(
    event_lid bigint NOT NULL,
    state_lid bigint NOT NULL
);

CREATE TABLE channel_event_stream
(
    sid bigserial NOT NULL,
    lid bigint NOT NULL
);

CREATE TABLE channel_extremities_backward
(
    channel_lid bigint NOT NULL,
    event_lid bigint NOT NULL
);

CREATE TABLE channel_extremities_forward
(
    channel_lid bigint NOT NULL,
    event_lid bigint NOT NULL
);

CREATE TABLE channel_aliases
(
    network text NOT NULL,
    channel_alias text NOT NULL,
    channel_id text NOT NULL,
    server_id text NOT NULL,
    auto boolean NOT NULL,
    CONSTRAINT c_adr_alias UNIQUE(channel_alias)
);

CREATE TABLE destination_stream_positions
(
    destination_type text NOT NULL,
    destination_id text NOT NULL,
    scope text,
    stream_id bigint NOT NULL,
    CONSTRAINT dest_stream_pos UNIQUE (destination_type, destination_id, scope)
);

CREATE TABLE identity_users
(
    lid bigserial NOT NULL,
    id text NOT NULL,
    CONSTRAINT id_user_id UNIQUE (id)
);
CREATE INDEX IF NOT EXISTS id_user_lid_idx
    ON identity_users(lid);
CREATE INDEX IF NOT EXISTS id_user_id_idx
    ON identity_users(id);

CREATE TABLE user_access_tokens
(
    user_lid bigint NOT NULL,
    token text NOT NULL,
    CONSTRAINT u_token UNIQUE (token)
);

CREATE TABLE identity_user_credentials
(
    user_lid bigint NOT NULL,
    type text NOT NULL,
    salt text,
    data text NOT NULL,
    CONSTRAINT id_u_creds UNIQUE(user_lid, type)
);

CREATE TABLE identity_user_store_links
(
    user_lid bigint NOT NULL,
    type text NOT NULL,
    id text NOT NULL,
    CONSTRAINT id_u_store_links UNIQUE(user_lid, type, id)
);

CREATE TABLE identity_user_threepids
(
    user_lid bigint NOT NULL,
    medium text NOT NULL,
    address text NOT NULL,
    CONSTRAINT id_u_tpid UNIQUE(user_lid, medium, address)
);
