#!/bin/sh
# Maven Wrapper script

set -e

# Find base directory
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
cd "$SCRIPT_DIR"

# Maven wrapper properties
MAVEN_WRAPPER_PROPERTIES="$SCRIPT_DIR/.mvn/wrapper/maven-wrapper.properties"

if [ -f "$MAVEN_WRAPPER_PROPERTIES" ]; then
    MAVEN_VERSION=$(grep "distributionUrl" "$MAVEN_WRAPPER_PROPERTIES" | sed 's/.*apache-maven-\([0-9.]*\).*/\1/')
fi

# Default Maven version
MAVEN_VERSION=${MAVEN_VERSION:-3.9.6}

# Maven home
MAVEN_HOME="$HOME/.m2/wrapper/dists/apache-maven-$MAVEN_VERSION"

# Download Maven if not present
if [ ! -d "$MAVEN_HOME" ]; then
    echo "Downloading Maven $MAVEN_VERSION..."
    mkdir -p "$MAVEN_HOME"
    curl -sL "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$MAVEN_VERSION/apache-maven-$MAVEN_VERSION-bin.tar.gz" | tar xz -C "$MAVEN_HOME" --strip-components=1
fi

# Run Maven
exec "$MAVEN_HOME/bin/mvn" "$@"
