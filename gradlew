#!/usr/bin/env sh

##############################################################################
## Gradle wrapper script
##############################################################################

APP_HOME=$(cd "$(dirname "$0")"; pwd -P)

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Java command
if [ -n "$JAVA_HOME" ] ; then
  JAVACMD="$JAVA_HOME/bin/java"
else
  JAVACMD=java
fi

if [ ! -x "$JAVACMD" ] ; then
  echo "ERROR: Java not found"
  exit 1
fi

exec "$JAVACMD" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
