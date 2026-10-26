#!/bin/bash
# Compiles the plugin and its tests against the hand-written Paper API stubs in ./stubs and runs them.
# Needs: a JDK 21 (javac, java) and python3 with PyYAML (only for the config.yml / plugin.yml checks).
# This is NOT a real Maven/Paper build: run `mvn clean package` for that. (src/test is skipped by Maven on purpose:
# those checks call sandbox-only helpers that do not exist in the real Paper API.)
set -e
cd "$(dirname "$0")/.."
B="$(mktemp -d)"
mkdir -p "$B/stubs" "$B/main" "$B/test"
javac -encoding UTF-8 -nowarn -d "$B/stubs" $(find stubs -name "*.java")
javac -encoding UTF-8 -Xlint:all -Werror -cp "$B/stubs" -d "$B/main" $(find src/main/java -name "*.java")
# the drop-in adapters other plugins copy are compiled and tested here too
javac -encoding UTF-8 -Xlint:all -Werror -cp "$B/stubs:$B/main" -d "$B/main" $(find adapters -name "*.java")
javac -encoding UTF-8 -Xlint:all -Werror -cp "$B/stubs:$B/main" -d "$B/test" $(find src/test/java -name "*.java")
status=0
python3 -I tools/check-resources.py || status=1
python3 -I tools/yaml-to-stub.py src/main/resources/config.yml "$B/shipped.tsv"
for t in $(cd src/test/java && find . -name "*Test.java" | sed 's#^\./##;s#\.java$##;s#.*/##' | sort); do
  echo "== $t"
  rc=0
  out="$(java -Dshipped="$B/shipped.tsv" -cp "$B/stubs:$B/main:$B/test" "com.nexuscraft.nexuscharacter.$t" 2>&1)" || rc=$?
  echo "$out" | grep -E "FAIL|checks|Exception|Error" || true
  if [ "$rc" -ne 0 ]; then status=1; fi
done
rm -rf "$B"
exit $status
