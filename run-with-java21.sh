#!/bin/bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH=$JAVA_HOME/bin:$PATH

echo "[环境隔离] 已动态切换为 Java 21, 当前 Java 版本："
java -version
echo "----------------------------------------"

exec "$@"
