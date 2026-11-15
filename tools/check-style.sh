#!/usr/bin/env bash
# Runs Checkstyle over the Java code of every day (starter/ and solution/),
# with the short list of rules in tools/checkstyle/checkstyle.xml.
# It looks for mistakes that hide bugs, not for formatting.
#
# Usage, from anywhere:
#   tools/check-style.sh
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
checkstyle_jar_sha256="a6e737092ce4e034b1cb9b975af41d7f16638dffbcc197e553b837a951ccb52c"
classpath_file="$(mktemp)"
trap 'rm -f "$classpath_file"' EXIT

# Maven fetches the pinned Checkstyle and its libraries from Maven Central
# (or reuses ~/.m2), refusing any file whose published checksum doesn't match.
"$root/mvnw" -q -B --strict-checksums -f "$root/tools/checkstyle/pom.xml" \
  dependency:build-classpath -Dmdep.outputFile="$classpath_file"
classpath="$(cat "$classpath_file")"

# And the Checkstyle jar itself must be the exact one this script was written for.
jar="$(tr ':' '\n' < "$classpath_file" | grep '/checkstyle-[0-9.]*\.jar$')"
echo "$checkstyle_jar_sha256  $jar" | sha256sum --check --quiet - || {
  echo "The Checkstyle jar's SHA-256 doesn't match the one pinned in $0" >&2
  exit 1
}

cd "$root"
java -cp "$classpath" -Dconfig_loc=tools/checkstyle com.puppycrawl.tools.checkstyle.Main \
  -c tools/checkstyle/checkstyle.xml \
  -x '/target/' \
  ./day-*/
