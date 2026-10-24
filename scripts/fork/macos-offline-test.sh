#!/usr/bin/env bash
# Offline run of an unpacked TapCue archive on macOS (STEP-06 job `macos` in ci.yml, moved here in STEP-07 so that
# release.yml runs the same check on the archive it publishes).
#
#   scripts/fork/macos-offline-test.sh <tapcue launcher> <report dir>
#
# With REQUIRE_REGISTER=true (release.yml) a sandbox log that does not show curl's denials (one probe before and one after
# the TapCue commands, each awaited up to LOG_WAIT seconds, default 60) fails the run instead of warning: a release is
# published only when the absence of attempts was actually observed.
#
# sandbox-exec denies every outbound connection except loopback (and the DNS resolver socket), so a command that needed
# the internet fails here. Denials are logged by the kernel; scripts/fork/macos-sandbox-log.sh reads them back as the
# register of attempts, after checking that it sees curl's. The JVM takes user.home from the account, not from $HOME, so
# TapCue state lands in the real home directory.
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
tapcue="${1:?usage: $0 <tapcue launcher> <report dir>}"
reports="${2:?usage: $0 <tapcue launcher> <report dir>}"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir -p "$reports"

cat > "$work/offline.sb" <<'SB'
(version 1)
(allow default)
(deny network-outbound)
(allow network-outbound (remote ip "localhost:*"))
(allow network-outbound (remote unix-socket))
(deny network-outbound (remote unix-socket (path-literal "/private/var/run/mDNSResponder")))
SB
offline() { sandbox-exec -f "$work/offline.sb" "$@"; }

# The unified log lags behind, by more than a few seconds after a heavy build (release.yml runs the tests first; the first
# run on main, 40115219200, read it after a fixed 2 s twice and found nothing). Wait for curl's denial instead: up to
# LOG_WAIT seconds, polling. Once a later probe shows up, the log also holds everything that happened before it.
probe() { # <start> <output file>: a denied curl, then wait for its denial in the log
  offline curl -sS --max-time 10 https://example.com > /dev/null 2>&1 && { echo "sandbox does not block the network"; exit 1; }
  local waited=0
  while :; do
    "$root/scripts/fork/macos-sandbox-log.sh" "$1" curl > "$2" || true
    grep -q "deny" "$2" && return 0
    [ "$waited" -ge "${LOG_WAIT:-60}" ] && return 1
    sleep 5; waited=$((waited + 5))
  done
}

probe_start="$(date '+%Y-%m-%d %H:%M:%S')"
probe "$probe_start" "$reports/sandbox-probe.txt" || true

start="$(date '+%Y-%m-%d %H:%M:%S')"
mkdir -p "$work/project/.tapcue"
printf 'appId: com.example\n---\n- launchApp\n' > "$work/project/.tapcue/flow.yaml"
offline "$tapcue" --version
offline "$tapcue" --help > /dev/null
offline "$tapcue" check-syntax "$work/project/.tapcue/flow.yaml"
# STDIN ends right after the requests: since STEP-06 the server answers them before it exits.
reply="$(printf '%s\n' \
  '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"ci","version":"0"}}}' \
  '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"cheat_sheet","arguments":{}}}' \
  | offline "$tapcue" mcp)"
echo "$reply" | head -c 600; echo
grep -q '"name":"tapcue"' <<<"$reply"
grep -q '"id":2,"result"' <<<"$reply"
test ! -e "$HOME/.maestro"
sleep 1
end_start="$(date '+%Y-%m-%d %H:%M:%S')"
probe "$end_start" "$reports/sandbox-probe-end.txt" || true

if grep -q "deny" "$reports/sandbox-probe.txt" && grep -q "deny" "$reports/sandbox-probe-end.txt"; then
  echo "The sandbox log shows curl's denied attempts before and after TapCue ran; checking that TapCue made none:"
  "$root/scripts/fork/macos-sandbox-log.sh" "$start" java | tee "$reports/sandbox-tapcue.txt"
  if grep -q "deny" "$reports/sandbox-tapcue.txt"; then
    echo "TapCue attempted a network connection outside loopback"
    exit 1
  fi
  echo "OK: no denied network attempt by TapCue"
elif [ "${REQUIRE_REGISTER:-false}" = true ]; then
  echo "The sandbox log did not show curl's denied attempts within ${LOG_WAIT:-60} s, so the absence of attempts cannot be shown (REQUIRE_REGISTER)"
  cat "$reports/sandbox-probe.txt" "$reports/sandbox-probe-end.txt"
  exit 1
else
  echo "::warning::The sandbox log did not show curl's denied attempts; on this runner the macOS run proves only that the commands work without network, not the absence of attempts."
  cat "$reports/sandbox-probe.txt" "$reports/sandbox-probe-end.txt"
fi
