#!/usr/bin/env bash
# Negative tests for the STEP-06 CI checks: scripts/fork/check-licenses.py and scripts/fork/check-forbidden-deps.sh.
# Each case plants one problem and expects the check to fail with a message naming it; the good inputs must pass.
#
#   scripts/fork/test-ci-checks.sh <tapcue-*.zip> [cli-runtime-dependencies.txt]
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
archive="${1:?usage: $0 <tapcue-*.zip> [cli-runtime-dependencies.txt]}"
deps="${2:-}"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

python3 -I - "$archive" "$work/notices.md" <<'PY'
import re, sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as zf:
    name = next(n for n in zf.namelist() if re.fullmatch(r"[^/]+/THIRD-PARTY-NOTICES\.md", n))
    open(sys.argv[2], "wb").write(zf.read(name))
PY

expect_fail() {
  local name="$1" pattern="$2"
  shift 2
  if output="$("$@" 2>&1)"; then
    echo "FAIL: $name was accepted"
    exit 1
  fi
  if ! grep -Eq -- "$pattern" <<<"$output"; then
    echo "FAIL: $name was rejected, but not for /$pattern/:"
    echo "$output"
    exit 1
  fi
  echo "ok: $name rejected"
}

licenses() { python3 -I "$root/scripts/fork/check-licenses.py" "$@"; }

licenses "$archive" > /dev/null
echo "ok: license scan accepts the archive"

# mutate <name> <python expression transforming the text t>
mutate() {
  python3 -I -c 'import re, sys; t = open(sys.argv[1], encoding="utf-8").read(); open(sys.argv[2], "w", encoding="utf-8").write(eval(sys.argv[3]))' \
    "$work/notices.md" "$work/$1.md" "$2"
}

mutate new-license 't.replace("| `com.squareup.okio:okio-jvm:", "| `com.example:proprietary:1.0` | `proprietary-1.0.jar` | GraalVM Free Terms and Conditions (GFTC) (https://www.oracle.com/downloads/licenses/graal-free-license.html) |\n| `com.squareup.okio:okio-jvm:", 1)'
# Keep the declared count right, so only the license is wrong.
python3 -I -c 'import re, sys; p = sys.argv[1]; t = open(p).read(); n = len(re.findall(r"^\| `[^`]+` \| `", t.split("## 2.")[0], re.M)); open(p, "w").write(re.sub(r"Third-party libraries: \d+\.", f"Third-party libraries: {n}.", t))' "$work/new-license.md"
expect_fail new-license "license not on the allowlist: GraalVM Free Terms" licenses "$work/new-license.md"

mutate respelled 're.sub(r"(\| `com\.squareup\.okio:okio-jvm:[^|]*\|[^|]*\| )([^|]*)\|", r"\1Apache License 2 (https://example.com/apache) |", t, count=1)'
expect_fail respelled "license not on the allowlist: Apache License 2 \(https://example.com/apache\)" licenses "$work/respelled.md"

mutate lgpl-only 're.sub(r"LGPL-2\.1-or-later \(https://www\.gnu\.org/licenses/old-licenses/lgpl-2\.1\); Apache-2\.0 \([^)]*\)", "LGPL-2.1-or-later (https://www.gnu.org/licenses/old-licenses/lgpl-2.1)", t, count=1)'
expect_fail lgpl-only "only alternative-only licenses" licenses "$work/lgpl-only.md"

mutate no-table 're.sub(r"^\| `.*\n", "", t, flags=re.M)'
expect_fail no-table "lists no libraries" licenses "$work/no-table.md"

mutate count-mismatch 're.sub(r"^\| `com\.squareup\.okio:okio-jvm:.*\n", "", t, count=1, flags=re.M)'
expect_fail count-mismatch "the table has" licenses "$work/count-mismatch.md"

# Dependency check: synthetic Gradle trees with one forbidden artifact each.
tree() {
  printf 'runtimeClasspath - Runtime classpath of source set '"'"'main'"'"'.\n+--- project :maestro-orchestra\n|    \\--- %s\n\\--- org.jetbrains.kotlin:kotlin-stdlib:1.9.0\n' "$1" > "$work/$2.txt"
}
tree "com.squareup.okio:okio:3.9.0" clean
"$root/scripts/fork/check-forbidden-deps.sh" "$work/clean.txt" > /dev/null
echo "ok: dependency check accepts a clean tree"
tree "com.posthog:posthog-server:1.0.0" posthog
expect_fail posthog "telemetry \(PostHog" "$root/scripts/fork/check-forbidden-deps.sh" "$work/posthog.txt"
tree "org.seleniumhq.selenium:selenium-java:4.43.0" selenium
expect_fail selenium "Selenium" "$root/scripts/fork/check-forbidden-deps.sh" "$work/selenium.txt"
tree "org.graalvm.truffle:truffle-enterprise:24.2.0" truffle
expect_fail truffle "truffle-enterprise" "$root/scripts/fork/check-forbidden-deps.sh" "$work/truffle.txt"
tree "project :maestro-ai" maestro-ai
expect_fail maestro-ai "maestro-ai module" "$root/scripts/fork/check-forbidden-deps.sh" "$work/maestro-ai.txt"
tree "project :maestro-web" maestro-web
expect_fail maestro-web "maestro-web module" "$root/scripts/fork/check-forbidden-deps.sh" "$work/maestro-web.txt"
printf 'nothing here\n' > "$work/garbage.txt"
expect_fail not-a-tree "does not look like a Gradle dependency tree" "$root/scripts/fork/check-forbidden-deps.sh" "$work/garbage.txt"

if [ -n "$deps" ]; then
  "$root/scripts/fork/check-forbidden-deps.sh" "$deps" > /dev/null
  echo "ok: dependency check accepts $deps"
fi

echo "OK: the license scan and the dependency check reject every planted problem"
