#!/usr/bin/env bash

if [[ -n "$CONF_FILE_PATH" ]] && [ ! -f "$CONF_FILE_PATH" ]; then
    echo "Generating config file $CONF_FILE_PATH"
    touch "CONF_FILE_PATH"

    if [[ -n "$DATA_DIR_PATH" ]]; then
        echo "Setting Data path to $DATA_DIR_PATH"
        echo "storage:" >> "$CONF_FILE_PATH"
        echo "  data: '$DATA_DIR_PATH'" >> "$CONF_FILE_PATH"
    fi

    if [[ -n "$DATABASE_CONNECTION" ]]; then
        echo "Setting Database configuration"
        echo "  database:" >> "$CONF_FILE_PATH"
        echo "    type: '$DATABASE_TYPE'" >> "$CONF_FILE_PATH"
        echo "    connection: '$DATABASE_CONNECTION'" >> "$CONF_FILE_PATH"
        echo >> "$CONF_FILE_PATH"
    fi

    echo "Configuration done!"
    echo
fi

exec java -jar /app/lib/gridifyd.jar -c "$CONF_FILE_PATH"
