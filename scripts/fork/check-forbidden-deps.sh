#!/usr/bin/env bash
# Dependency check (STEP-06; docs/NETWORK-AUDIT.md "Plan weryfikacji" item 2): the CLI's runtime classpath must not
# contain telemetry (PostHog), the web platform (Selenium, maestro-web; D-4), remote AI (maestro-ai) or GraalVM
# truffle-enterprise (L-03, D-6), and the build must not include the removed modules.
#
#   scripts/fork/check-forbidden-deps.sh [dependencies.txt]
#
# Without an argument it runs `./gradlew :maestro-cli:dependencies --configuration runtimeClasspath` and keeps the
# output in build/reports/fork/cli-runtime-dependencies.txt; with one it checks that file (used by the self-test).
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"

# group:artifact patterns (extended regex) that must not appear, with the reason.
forbidden=(
  'com\.posthog[.:]|telemetry (PostHog, NETWORK-AUDIT N-01)'
  'org\.seleniumhq\.selenium[.:]|web platform / Selenium (D-4, N-26)'
  'org\.graalvm\.truffle:truffle-enterprise|GraalVM truffle-enterprise (LICENSE-AUDIT L-03, D-6)'
  'org\.graalvm\.sdk:nativebridge|GraalVM nativebridge (comes with truffle-enterprise, L-03)'
  'project :maestro-ai|:maestro-ai[: ]|maestro-ai module (remote AI, N-08/N-09)'
  'project :maestro-web|:maestro-web[: ]|maestro-web module (D-4)'
)

if [ $# -ge 1 ]; then
  deps="$1"
else
  mkdir -p "$root/build/reports/fork"
  deps="$root/build/reports/fork/cli-runtime-dependencies.txt"
  (cd "$root" && ./gradlew -q :maestro-cli:dependencies --configuration runtimeClasspath) > "$deps"
fi

if ! grep -q -- '--- ' "$deps"; then
  echo "FAIL: $deps does not look like a Gradle dependency tree"
  exit 1
fi

status=0
for entry in "${forbidden[@]}"; do
  pattern="${entry%|*}"
  reason="${entry##*|}"
  if matches="$(grep -En -- "$pattern" "$deps")"; then
    echo "FORBIDDEN: $reason"
    sed 's/^/  /' <<<"$matches" | head -n 10
    status=1
  fi
done

if [ $# -eq 0 ]; then
  for module in maestro-ai maestro-web; do
    if grep -Eq "include\(\"?:?$module\"?\)" "$root/settings.gradle.kts"; then
      echo "FORBIDDEN: settings.gradle.kts includes $module"
      status=1
    fi
  done
fi

if [ "$status" -eq 0 ]; then
  echo "OK: no forbidden dependency in $(grep -c -- '--- ' "$deps") lines of $deps"
fi
exit "$status"
