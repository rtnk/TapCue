#!/usr/bin/env bash
# Negative tests for scripts/verify-dist.sh: each case copies a good archive, plants one problem and expects the
# check to fail with a message naming it. The good archive itself must pass.
#
#   scripts/fork/test-verify-dist.sh <tapcue-*-macos-arm64.zip>
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
good="${1:?usage: $0 <tapcue-*-macos-arm64.zip>}"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# mutate <name> <python snippet using src (ZipFile), dst (ZipFile), root (top-level dir)>
mutate() {
  local name="$1" snippet="$2"
  python3 -I - "$good" "$work/$name.zip" "$snippet" <<'PY'
import io, sys, zipfile
src_path, dst_path, snippet = sys.argv[1:4]
src = zipfile.ZipFile(src_path)
root = src.namelist()[0].split("/", 1)[0] + "/"
skip = set()
extra = []

def drop(path):
    skip.add(root + path)

def add(path, data, mode=0o644):
    info = zipfile.ZipInfo(root + path)
    info.external_attr = (0o100000 | mode) << 16
    extra.append((info, data))

def patch_jar(jar_glob_prefix, entry, data):
    name = next(n for n in src.namelist() if n.startswith(root + "lib/" + jar_glob_prefix))
    buf = io.BytesIO()
    with zipfile.ZipFile(io.BytesIO(src.read(name))) as jin, zipfile.ZipFile(buf, "w") as jout:
        for item in jin.infolist():
            jout.writestr(item, jin.read(item))
        jout.writestr(entry, data)
    skip.add(name)
    extra.append((src.getinfo(name), buf.getvalue()))

def replace(path, transform):
    info = src.getinfo(root + path)
    skip.add(root + path)
    extra.append((info, transform(src.read(info))))

exec(snippet)
with zipfile.ZipFile(dst_path, "w", zipfile.ZIP_DEFLATED) as dst:
    for item in src.infolist():
        if item.filename not in skip:
            dst.writestr(item, src.read(item))
    for info, data in extra:
        dst.writestr(info, data)
PY
}

expect_fail() {
  local name="$1" pattern="$2"
  if output="$("$root/scripts/verify-dist.sh" "$work/$name.zip" "$work/none" 2>&1)"; then
    echo "FAIL: $name was accepted"
    exit 1
  fi
  if ! grep -Eq "$pattern" <<<"$output"; then
    echo "FAIL: $name was rejected, but not for /$pattern/:"
    echo "$output"
    exit 1
  fi
  echo "ok: $name rejected"
}

"$root/scripts/verify-dist.sh" "$good" "$work/none" > /dev/null
echo "ok: good archive accepted"

mutate simulator-server 'patch_jar("maestro-cli-", "deps/simulator-server/simulator-server", b"x")'
expect_fail simulator-server "simulator-server \(LICENSE-AUDIT L-01\)"

mutate viewer 'patch_jar("maestro-cli-", "mcp-viewer/index.html", b"x")'
expect_fail viewer "MCP Viewer"

mutate ios-zip 'patch_jar("maestro-ios-driver", "driver-iPhoneSimulator/Debug-iphonesimulator/maestro-driver-ios.zip", b"PK")'
expect_fail ios-zip "prebuilt iOS driver"

mutate apple-framework 'patch_jar("maestro-ios-driver", "Runner.app/Frameworks/XCTest.framework/XCTest", b"x")'
expect_fail apple-framework "Apple bundle"

mutate truffle-enterprise 'add("lib/truffle-enterprise-24.2.0.jar", b"PK\x05\x06" + b"\0" * 18)'
expect_fail truffle-enterprise "truffle-enterprise"

mutate posthog 'patch_jar("maestro-cli-", "com/posthog/PostHog.class", b"\xca\xfe\xba\xbe\0\0\0\x34")'
expect_fail posthog "PostHog"

mutate remote-ai 'patch_jar("maestro-orchestra.jar", "maestro/ai/Prediction.class", b"\xca\xfe\xba\xbe\0\0\0\x34")'
expect_fail remote-ai "remote AI"

mutate unknown-native 'patch_jar("maestro-utils", "native/libunknown.dylib", b"\xcf\xfa\xed\xfe0000")'
expect_fail unknown-native "not on the allowlist"

mutate vendor-link 'replace("bin/tapcue", lambda b: b + b"\n# see https://maestro.dev\n")'
expect_fail vendor-link "vendor link in start script"

mutate no-java-check 'replace("bin/tapcue", lambda b: b.replace(b"Java 17 or higher is required", b"Java is required"))'
expect_fail no-java-check "check for Java 17"

mutate no-notice 'drop("NOTICE")'
expect_fail no-notice "missing: NOTICE"

mutate unlisted-jar 'add("lib/unlisted-1.0.jar", b"PK\x05\x06" + b"\0" * 18)'
expect_fail unlisted-jar "does not list unlisted-1.0.jar"

cp "$good" "$work/checksum.zip"
echo "0000000000000000000000000000000000000000000000000000000000000000  checksum.zip" > "$work/SHA256SUMS"
if output="$("$root/scripts/verify-dist.sh" "$work/checksum.zip" "$work/SHA256SUMS" 2>&1)" || ! grep -q "SHA-256 mismatch" <<<"$output"; then
  echo "FAIL: wrong SHA256SUMS was accepted"
  exit 1
fi
echo "ok: wrong SHA256SUMS rejected"

echo "OK: verify-dist.sh rejects every planted problem"
