#!/usr/bin/env sh

##############################################################################
##
##  Gradle start up script for UN*X
##
##############################################################################

# Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
DEFAULT_JVM_OPTS=""

APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")

# Resolve links - $0 may be a softlink
PRG="$0"
while [ -h "$PRG" ] ; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=`dirname "$PRG"`/"$link"
  fi
done

PRGDIR=`dirname "$PRG"`
EXECUTABLE="$PRGDIR/gradle"

CLASSPATH=$CLASSPATH

# Determine Java command to use
if [ -n "$JAVA_HOME" ] ; then
  JAVA_HOME="$JAVA_HOME"
  JAVACMD="$JAVA_HOME/bin/java"
else
  JAVACMD=`which java`
fi

if [ ! -x "$JAVACMD" ] ; then
  echo "ERROR: JAVA_HOME is not set correctly or java is not executable."
  exit 1
fi

exec "$JAVACMD" $DEFAULT_JVM_OPTS -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
