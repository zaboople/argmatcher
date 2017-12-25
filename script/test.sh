#!/bin/bash
cd $(dirname $0)/..
echo $PATH
ant compile || exit 1
java -Xmx128M -classpath build "$@"

