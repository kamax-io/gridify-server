#
# Gridify Server
# Copyright (C) 2021 Kamax Sarl
#
# https://www.kamax.io/
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as
# published by the Free Software Foundation, either version 3 of the
# License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program.  If not, see <http://www.gnu.org/licenses/>.
#
FROM openjdk:8-jre-alpine

RUN apk update && apk add bash curl jq && rm -rf /var/lib/apk/* /var/cache/apk/*

VOLUME /app/config
VOLUME /app/data

EXPOSE 9229
EXPOSE 9339
EXPOSE 9449

ENV JAVA_OPTS=""
ENV CONF_FILE_PATH="/app/config/config.yaml"
ENV DATA_DIR_PATH="/app/data"
ENV DATABASE_TYPE="postgresql"
ENV DATABASE_CONNECTION="//db/gridifyd?user=gridifyd&password=gridifyd"
ENV PATH=/app/bin:$PATH

WORKDIR "/app"
ENTRYPOINT [ "/app/entrypoint" ]

copy app .
COPY entrypoint .
