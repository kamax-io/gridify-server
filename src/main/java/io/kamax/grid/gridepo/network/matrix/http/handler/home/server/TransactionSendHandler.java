/*
 * Gridepo - Grid Data Server
 * Copyright (C) 2021 Kamax Sarl
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

package io.kamax.grid.gridepo.network.matrix.http.handler.home.server;

import com.google.gson.JsonObject;
import io.kamax.grid.gridepo.Gridepo;
import io.kamax.grid.gridepo.core.channel.state.ChannelEventAuthorization;
import io.kamax.grid.gridepo.http.handler.Exchange;
import io.kamax.grid.gridepo.network.matrix.core.base.ServerSession;
import io.kamax.grid.gridepo.network.matrix.http.json.ServerTransaction;
import io.kamax.grid.gridepo.util.KxLog;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandles;
import java.util.List;

public class TransactionSendHandler extends AuthenticatedServerApiHandler {

    private static final Logger log = KxLog.make(MethodHandles.lookup().lookupClass());

    public TransactionSendHandler(Gridepo g) {
        super(g);
    }

    @Override
    protected void handle(ServerSession session, Exchange ex) {
        String txnId = ex.getQueryParameter("txnId");
        ServerTransaction txn = ex.parseJsonTo(ServerTransaction.class);
        List<ChannelEventAuthorization> auths = session.push(txn);
        log.debug("Processed Server {} Transaction {}", session.getDomain(), txnId);

        ex.respond(new JsonObject());
    }

}