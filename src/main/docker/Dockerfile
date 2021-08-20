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

WORKDIR "/app"
ENTRYPOINT [ "/app/entrypoint" ]

ARG DIST_FILE
ADD $DIST_FILE.tar /app
COPY entrypoint .
RUN mv $DIST_FILE/* . && rmdir $DIST_FILE
