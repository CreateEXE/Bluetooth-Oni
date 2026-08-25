#!/bin/bash

#
# Copyright 2015 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

##############################################################################
##
##  Gradle start up script for UN*X
##
##############################################################################

# Attempt to set APP_HOME
# Resolve links: $0 may be a link
PRG="$0"
# Need this for daisy-chained symlinks.
while
    APP_HOME=$( cd "${PRG%/*}" && pwd -P )
    PRG=$( readlink "$PRG" ) || break
    [[ "$PRG" != /* ]] && PRG="$APP_HOME/$PRG"
do
    continue
done

# This is normally unused
# shellcheck disable=SC2034
APP_NAME="Gradle"
APP_HOME=$( cd "${APP_HOME}" && pwd -P ) || exit

# Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
DEFAULT_JVM_OPTS='-Xmx64m -Xms64m'

# Use the maximum available, or set MAX_FD != maximum.
MAX_FD=maximum

warn() {
    echo "$*" >&2
}

die() {
    echo
    echo "$*"
    echo
    exit 1
}

# OS specific support (must be 'true' or 'false').
cygwin=false
msys=false
darwin=false
nonstop=false
case "$( uname )" in
    CYGWIN* )
        cygwin=true
        ;;
    Darwin* )
        darwin=true
        ;;
    MSYS* | MINGW* )
        msys=true
        ;;
    NONSTOP* )
        nonstop=true
        ;;
esac

# Determine the Java command to use to start the JVM.
if [[ -n "$JAVA_HOME" ]] && [[ -x "$JAVA_HOME/bin/java" ]]; then
    javaexe="$JAVA_HOME/bin/java"
elif type java > /dev/null 2>&1; then
    javaexe=java
else
    die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH."
fi

if [[ -z "$JAVA_HOME" ]] ; then
    warn "JAVA_HOME environment variable is not set"
fi

javaVersion=$("$javaexe" -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
if [[ $javaVersion -lt 11 ]] ; then
    die "ERROR: Java 11 or higher is required to run Gradle. You are currently using Java $javaVersion."
fi

# Increase the maximum file descriptors if we can.
if ! "$cygwin" && ! "$msys" ; then
    case $MAX_FD in
        /*)
            ulimit -n "$MAX_FD"
            warn "Increased the maximum file descriptors to $MAX_FD."
            ;;
        *)
            warn "Could not query maximum file descriptor limit: $MAX_FD"
            ;;
    esac
fi

# For Darwin, add options to specify how the application appears in the dock
if $darwin; then
    DEFAULT_JVM_OPTS="$DEFAULT_JVM_OPTS -XX:+UseStringDeduplication"
fi

# Collect all arguments for the java command, stacking in reverse order:
#   * args from the command line
#   * the main class name
#   * -classpath
#   * -D...system properties
#   * all other JVM args
# Determine the initial maximum memory to use if it's not explicitly set.
# shellcheck disable=SC2153
if [[ -z "$GRADLE_OPTS" ]]; then
    GRADLE_OPTS="$DEFAULT_JVM_OPTS"
else
    GRADLE_OPTS="$DEFAULT_JVM_OPTS $GRADLE_OPTS"
fi

# Escape application args
save() {
    local i
    for i in "$@"; do
        printf %s\\n "$i" | sed "s/'/'\\\\''/g;1s/^/'/;\$s/\$/' \\"
    done
    echo " "
}
APP_ARGS=$(save "$@")
eval "set -- $APP_ARGS"

#
# Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
#
DEFAULT_JVM_OPTS='-Xmx64m -Xms64m'

# Collect all arguments for the java command;
#   * $DEFAULT_JVM_OPTS, $JAVA_OPTS, and $GRADLE_OPTS can contain fragments of shell commands/options
#   * put the unquoted value of DEFAULT_JVM_OPTS, JAVA_OPTS and GRADLE_OPTS in the command line, so we lose quoting and unquoted value
#   * We pretend to be a Java application by setting a classname built from the jar filename
# shellcheck disable=SC2086,SC2081
exec "$javaexe" $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
