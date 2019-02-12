/*
 * Gridepo - Grid Data Server
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

package io.kamax.grid.gridepo.http.handler.matrix;

import io.kamax.grid.gridepo.http.handler.Exchange;
import io.kamax.grid.gridepo.http.handler.SaneHandler;
import io.kamax.grid.gridepo.util.GsonUtil;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;

public class SyncHandler extends SaneHandler {

    @Override
    protected void handle(Exchange exchange) {
        String since = StringUtils.defaultIfBlank(exchange.getQueryParameter("since"), "park");
        if (StringUtils.equals("park", since)) {
            Instant target = Instant.now().plusSeconds(30);
            while (target.isAfter(Instant.now())) {
                try {
                    Thread.sleep(1000L);
                    System.out.println(".");

                    if (Thread.currentThread().isInterrupted()) {
                        System.out.println("Interrupted");
                        break;
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
        exchange.respond(GsonUtil.makeObj("next_batch", "park"));
    }

}
