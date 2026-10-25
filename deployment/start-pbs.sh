#!/bin/bash

# Starts pbs in the foreground. Invoked from deployment/bin by `just start`.
#
# Secrets are NOT in the repo: they live in <deployment-dir>/pbs.env on the deploy machine only. This script
# sources that file, so the ${GOOGLE_CLIENT_ID}-style placeholders in application.properties resolve.

set -euo pipefail

# Check if argument is provided
if [ $# -eq 0 ]; then
    echo "Error: deployment base path is required"
    echo "Usage: $0 <deployment-dir>"
    echo "Example: $0 /path/to/priceengine-bill-splitter/deployment"
    exit 1
fi

export FOLDER_BASE="$1"
ENV_FILE="$FOLDER_BASE/pbs.env"
LOG_FILE="$FOLDER_BASE/logs/pbs.log"

if [ ! -f "$ENV_FILE" ]; then
    echo "Error: $ENV_FILE not found."
    echo "Copy deployment/pbs.env.example to that path and fill in the real values."
    exit 1
fi

# shellcheck disable=SC1090
source "$ENV_FILE"

# The deployment runs under the `production` profile. It is activated here rather than left at the defaults so
# that deployment-only behaviour can be gated positively — @Profile("production"),
# <springProfile name="production"> — instead of by a negation list naming every development profile.
export SPRING_PROFILES_ACTIVE=production

# Overridable from pbs.env. The Mini also runs publicbackend (-Xmx2g), overlord and MySQL.
JAVA_OPTS="${JAVA_OPTS:--Xmx512m}"

mkdir -p "$FOLDER_BASE/logs"

echo "Starting pbs..."
echo "  deployment dir : $FOLDER_BASE"
echo "  profile        : $SPRING_PROFILES_ACTIVE"
echo "  java opts      : $JAVA_OPTS"
echo "  log            : $LOG_FILE"

# Writing to $LOG_FILE is logback's job (see logback-spring.xml and logging.file.name in
# application-production.properties), not tee's.
#
# exec replaces this shell with the JVM so that Ctrl-C reaches the JVM directly.
# shellcheck disable=SC2086
exec java $JAVA_OPTS -jar pbs.jar
