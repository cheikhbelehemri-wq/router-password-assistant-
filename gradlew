#!/usr/bin/env sh

##############################################################################
##
##  Gradle start up script for UN*X
##
##############################################################################

PRG="$0"

while [ -h "$PRG" ]; do
    ls=`ls -ld "$PRG"`
    link=`expr "$ls" : '.*-> \(.*\)$'`
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=`dirname "$PRG"`"/$link"
    fi
done

SAVED="`pwd`"
CDPATH=""
APP_HOME="`dirname "$PRG"`"
APP_HOME="`cd "$APP_HOME" && pwd`"
cd "$SAVED"

APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`

DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

if [ -z "$JAVA_HOME" ] ; then
    JAVACMD="java"
else
    JAVACMD="$JAVA_HOME/bin/java"
fi

if [ -f "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" ]; then
    CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
    exec "$JAVACMD" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
else
    exec gradle "$@"
fi
