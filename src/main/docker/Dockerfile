FROM openjdk:8-jre-alpine

RUN apk update && apk add bash && rm -rf /var/lib/apk/* /var/cache/apk/*

VOLUME /etc/gridify/server
VOLUME /var/gridify/server
EXPOSE 9009

ENV JAVA_OPTS=""
ENV CONF_FILE_PATH="/etc/gridify/server/config.yaml"
ENV DATA_DIR_PATH="/var/lib/gridify/server"
ENV DATABASE_TYPE="postgresql"
ENV DATABASE_CONNECTION="//db/gridify-server?user=gridify-server&password=gridify-server"

CMD [ "/app/start.sh" ]

ADD start.sh /app/start.sh
ADD gridifyd.jar /app/gridifyd.jar
