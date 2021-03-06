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

import org.apache.commons.lang3.StringUtils;

import java.time.Instant;

public class ChannelEventMeta {

    private boolean seed;
    private String receivedFrom;
    private Instant receivedAt;
    private String fetchedFrom;
    private Instant fetchedAt;
    private boolean processed;
    private Instant processedOn;
    private boolean present;
    private boolean valid;
    private String validReason;
    private boolean allowed;
    private Long orderMajor;
    private Long orderMinor;

    public boolean isSeed() {
        return seed;
    }

    public void setSeed(boolean seed) {
        this.seed = seed;
    }

    public String getReceivedFrom() {
        return receivedFrom;
    }

    public void setReceivedFrom(String receivedFrom) {
        this.receivedFrom = receivedFrom;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    public String getFetchedFrom() {
        return fetchedFrom;
    }

    public void setFetchedFrom(String fetchedFrom) {
        this.fetchedFrom = fetchedFrom;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(Instant fetchedAt) {
        this.fetchedAt = fetchedAt;
    }

    public boolean isProcessed() {
        return processed;
    }

    public void setProcessed(boolean processed) {
        this.processed = processed;
    }

    public Instant getProcessedOn() {
        return processedOn;
    }

    @Deprecated // TODO remove when replaced with processed on ChannelEvent object
    public void setProcessedOn(Instant processedOn) {
        this.processedOn = processedOn;
        setProcessed(true);
    }

    public boolean isPresent() {
        return present;
    }

    public void setPresent(boolean present) {
        this.present = present;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public String getValidReason() {
        return validReason;
    }

    public void setValidReason(String validReason) {
        this.validReason = StringUtils.isBlank(validReason) ? null : validReason;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public void setAllowed(boolean allowed) {
        this.allowed = allowed;
    }

    public Long getOrderMajor() {
        return orderMajor;
    }

    public void setOrderMajor(Long orderMajor) {
        this.orderMajor = orderMajor;
    }

    public Long getOrderMinor() {
        return orderMinor;
    }

    public void setOrderMinor(Long orderMinor) {
        this.orderMinor = orderMinor;
    }

}
