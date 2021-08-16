FROM openjdk:8-jre-alpine

RUN apk update && apk add bash && rm -rf /var/lib/apk/* /var/cache/apk/*

VOLUME /app/config
VOLUME /app/data
EXPOSE 9009

ENV JAVA_OPTS=""
ENV CONF_FILE_PATH="/app/config/config.yaml"
ENV DATA_DIR_PATH="/app/data"
ENV DATABASE_TYPE="postgresql"
ENV DATABASE_CONNECTION="//db/gridify-server?user=gridify-server&password=gridify-server"

WORKDIR "/app"
ENTRYPOINT [ "/app/start" ]

ADD start.sh /app/start
ADD gridifyd /app/bin/gridifyd
ADD gridifyd.jar /app/lib/gridifyd.jar
