#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"

for pom in \
  "$PROJECT_DIR/backend/qknow-server/pom.xml" \
  "$PROJECT_DIR/backend/qknow-hermes/qknow-hermes-starter/pom.xml"
do
  count="$(xmllint --xpath "count(/*[local-name()='project']/*[local-name()='dependencies']/*[local-name()='dependency'][*[local-name()='artifactId']='spring-boot-devtools'])" "$pom")"
  test "$count" = "1"
done
