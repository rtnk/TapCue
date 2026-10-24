#!/usr/bin/env bash
# Android integration test on an emulator (STEP-06; AT-02…AT-07 in docs/ROADMAP-0.1.md). Runs the flows in
# scripts/fork/android-flows/ with the TapCue launcher from the distribution archive against the one booted emulator:
# a passing flow (launch, assert, scroll, tap, input, screenshot), the view hierarchy, MCP with a device, and the error
# paths — missing element (timeout), app not installed, invalid YAML, the on-device driver killed mid-flow.
#
#   scripts/fork/android-integration.sh <tapcue launcher> <report dir>
set -uo pipefail

tapcue="${1:?usage: $0 <tapcue launcher> <report dir>}"
dir="${2:?usage: $0 <tapcue launcher> <report dir>}"
flows="$(cd "$(dirname "$0")/android-flows" && pwd)"
mkdir -p "$dir"
dir="$(cd "$dir" && pwd)"
failures=0
summary=""

device="$(adb devices | awk 'NR > 1 && $2 == "device" { print $1; exit }')"
if [ -z "$device" ]; then
  echo "FAIL: no booted Android device"
  exit 1
fi
echo "device: $device ($(adb -s "$device" shell getprop ro.build.version.release | tr -d '\r'), API $(adb -s "$device" shell getprop ro.build.version.sdk | tr -d '\r'))"

record() {
  summary+="$1"$'\n'
  [ "${1%% *}" = PASS ] || failures=$((failures + 1))
  echo "$1"
}

# run <name> <expected: pass|fail> <grep -E pattern for the output, or ""> <tapcue args...>
run() {
  local name="$1" expect="$2" pattern="$3" status=0 start end
  shift 3
  echo "== $name: tapcue $*"
  start="$(date +%s)"
  "$tapcue" "$@" > "$dir/$name.out" 2>&1 || status=$?
  end="$(date +%s)"
  tail -n 25 "$dir/$name.out" | sed 's/^/  | /'
  local verdict=PASS reason=""
  if [ "$expect" = pass ] && [ "$status" != 0 ]; then verdict=FAIL; reason="exit $status, expected 0"; fi
  if [ "$expect" = fail ] && [ "$status" = 0 ]; then verdict=FAIL; reason="exit 0, expected an error"; fi
  if [ -n "$pattern" ] && ! grep -Eq -- "$pattern" "$dir/$name.out"; then verdict=FAIL; reason+=" output does not match /$pattern/"; fi
  record "$verdict $name (exit $status, $((end - start)) s)${reason:+: $reason}"
}

debug() { echo --device "$device" test --debug-output "$dir/debug/$1" --flatten-debug-output; }

# AT-03, AT-05: a flow that must pass, with a JUnit report, debug output and a screenshot.
run settings pass "" $(debug settings) --format junit --output "$dir/settings-junit.xml" "$flows/settings.yaml"
run hierarchy pass '"attributes"' --device "$device" hierarchy

# AT-02, AT-06: MCP over STDIO with a device — list_devices sees the emulator, inspect_screen and take_screenshot work.
echo "== mcp-device"
status=0
printf '%s\n' \
  '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"ci","version":"0"}}}' \
  '{"jsonrpc":"2.0","method":"notifications/initialized"}' \
  '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"list_devices","arguments":{}}}' \
  "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"inspect_screen\",\"arguments\":{\"device_id\":\"$device\"}}}" \
  "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"take_screenshot\",\"arguments\":{\"device_id\":\"$device\"}}}" \
  | "$tapcue" mcp > "$dir/mcp-device.out" 2> "$dir/mcp-device.err" || status=$?
python3 -I - "$dir/mcp-device.out" "$device" <<'PY' && record "PASS mcp-device (exit $status)" || record "FAIL mcp-device (exit $status)"
import json, sys
out, device = sys.argv[1], sys.argv[2]
replies = {}
for line in open(out, encoding="utf-8"):
    if line.strip():
        message = json.loads(line)
        replies[message.get("id")] = message
for i in (1, 2, 3, 4):
    assert i in replies and "result" in replies[i], f"no result for request {i}: {replies.get(i)}"
    assert not replies[i]["result"].get("isError"), f"request {i} returned an error: {replies[i]}"
devices = json.loads(replies[2]["result"]["content"][0]["text"])["devices"]
assert any(d["device_id"] == device for d in devices), f"list_devices does not list {device}: {devices}"
assert replies[3]["result"]["content"][0]["text"].strip(), "inspect_screen returned nothing"
assert any(c.get("type") == "image" for c in replies[4]["result"]["content"]), "take_screenshot returned no image"
print(f"  list_devices: {[d['device_id'] for d in devices]}; inspect_screen and take_screenshot answered")
PY

# AT-04, AT-07: error paths. Each must fail with a local, readable message.
run element-timeout fail 'TapCue element that is not on any screen' $(debug element-timeout) "$flows/element-timeout.yaml"
run missing-element fail 'Assertion is false|not visible|TapCue element that is not on any screen' $(debug missing-element) "$flows/missing-element.yaml"
run app-not-installed fail 'dev\.tapcue\.not\.installed' $(debug app-not-installed) "$flows/app-not-installed.yaml"
run invalid-yaml fail 'Parsing Failed' --device "$device" test "$flows/invalid.yaml"

# Driver error: stop the on-device driver (app and instrumentation) while the flow waits; the CLI must end with an
# error well before the flow's own 150 s timeout, not hang or report success.
echo "== driver-killed"
start="$(date +%s)"
"$tapcue" $(debug driver-killed) "$flows/driver-killed.yaml" > "$dir/driver-killed.out" 2>&1 &
pid=$!
for _ in $(seq 1 90); do
  adb -s "$device" shell pidof dev.mobile.maestro > /dev/null 2>&1 && break
  sleep 1
done
sleep 10
adb -s "$device" shell am force-stop dev.mobile.maestro.test
adb -s "$device" shell am force-stop dev.mobile.maestro
status=0
wait "$pid" || status=$?
elapsed=$(( $(date +%s) - start ))
tail -n 25 "$dir/driver-killed.out" | sed 's/^/  | /'
if [ "$status" != 0 ] && [ "$elapsed" -lt 150 ]; then
  record "PASS driver-killed (exit $status, $elapsed s)"
else
  record "FAIL driver-killed (exit $status, $elapsed s): expected an error before the 150 s flow timeout"
fi

adb -s "$device" exec-out screencap -p > "$dir/final-screen.png" 2>/dev/null || true
printf '%s' "$summary" > "$dir/summary.txt"
echo
cat "$dir/summary.txt"
if [ "$failures" -ne 0 ]; then
  echo "FAIL: $failures Android check(s) failed"
  exit 1
fi
echo "OK: Android integration passed"
