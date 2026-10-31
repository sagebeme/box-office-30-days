#!/usr/bin/env bash
# Checks every day of the course, the same way CI does:
#   1. the tests in starter/ and solution/ are the same files,
#   2. the solution passes them,
#   3. the untouched starter compiles but fails them (or the tests check nothing).
#
# Usage, from the repository root:
#   tools/verify.sh              every day
#   tools/verify.sh 103 104      only those days
set -uo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
mvnw="$root/mvnw"
failed=0

days=()
if [ "$#" -eq 0 ]; then
  for d in "$root"/day-*/; do days+=("$(basename "$d")"); done
else
  for n in "$@"; do
    match=("$root"/day-"$n"-*/)
    [ -d "${match[0]}" ] || { echo "No folder for day $n"; exit 2; }
    days+=("$(basename "${match[0]}")")
  done
fi

fail() { echo "  FAIL  $1"; failed=1; }
pass() { echo "  ok    $1"; }

for day in "${days[@]}"; do
  echo "$day"
  starter="$root/$day/starter"
  solution="$root/$day/solution"

  # 1. Same tests on both sides, so the learner is held to what the solution passes.
  tests_differ=0
  for module in "$solution"/*/; do
    m="$(basename "$module")"
    [ -d "$module/src/test" ] || continue
    if ! diff -rq "$starter/$m/src/test" "$module/src/test" > /dev/null 2>&1; then
      fail "$m: tests in starter/ and solution/ differ"
      tests_differ=1
    fi
  done
  [ "$tests_differ" -eq 0 ] && pass "starter and solution share the same tests"

  # 2. The solution passes.
  if "$mvnw" -q -B -f "$solution/pom.xml" test > "$root/.verify-$day-solution.log" 2>&1; then
    pass "solution passes its tests"
  else
    fail "solution fails its tests (see .verify-$day-solution.log)"
  fi

  # 3. The starter compiles, and its tests fail until the TODOs are done.
  if ! "$mvnw" -q -B -f "$starter/pom.xml" test-compile > "$root/.verify-$day-starter.log" 2>&1; then
    fail "starter does not compile (see .verify-$day-starter.log)"
  elif "$mvnw" -q -B -f "$starter/pom.xml" test -Dsurefire.failIfNoSpecifiedTests=false > /dev/null 2>&1; then
    fail "the untouched starter passes its tests, so they check nothing"
  else
    pass "starter compiles and fails its tests"
  fi
done

if [ "$failed" -ne 0 ]; then
  echo "Some checks failed."
  exit 1
fi
rm -f "$root"/.verify-*.log
echo "All checks passed."
