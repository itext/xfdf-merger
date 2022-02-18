#!/bin/sh


# Script to generate jars with the current date in them

generate_version() {
xmllint --shell pom.xml <<EOF | grep -E -v '^/|^ ' | sed "s/SNAPSHOT/$(date --utc +%Y%m%d)/"
setns ns=http://maven.apache.org/POM/4.0.0
cat /ns:project/ns:properties/ns:revision/text()
EOF
}

VERSION_STR=$(generate_version)

echo "Building version $VERSION_STR..."
mvn package "-Drevision=$VERSION_STR"