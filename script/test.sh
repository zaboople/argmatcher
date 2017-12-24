cd $(dirname $0)/..
ant compile || exit 1
java -Xmx128M -classpath build "$@"

