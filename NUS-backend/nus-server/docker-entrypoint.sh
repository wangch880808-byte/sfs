#!/bin/sh
set -eu

skywalking_opts=
if [ "${NUS_SKYWALKING_ENABLED:-true}" = "true" ]; then
  agent_jar=/opt/skywalking-agent/skywalking-agent.jar
  [ -f "$agent_jar" ] || {
    printf '%s\n' "SkyWalking is enabled but the Java agent is missing: $agent_jar" >&2
    exit 1
  }
  skywalking_opts="-javaagent:$agent_jar"
fi

exec java $skywalking_opts ${JAVA_OPTS:-} -jar /nus-server/app.jar "$@"
