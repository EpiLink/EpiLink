#!/bin/sh

CONFIG_PATH=config/$CONFIG_FILE

echo "=> Starting EpiLink"
echo "> Selected config file $CONFIG_PATH"

if ! [ -f $CONFIG_PATH ]; then
  echo "! Can't find the given config files !"

  echo "You must mount a volume to the /var/run/epilink/config folder and put a configuration file in it."
  echo "Then, put its path in the 'CONFIG_FILE' environment variable"

  exit 1
fi

if [ "$VERBOSE" = true ]; then
  ARGS=-v
fi

echo

bin/epilink-backend $ARGS $CONFIG_PATH