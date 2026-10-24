#!/usr/bin/env bash
# Offline (egress) test for Linux (STEP-06, gate G-5 in docs/ROADMAP-0.1.md; plan in docs/NETWORK-AUDIT.md).
#
# Runs commands in a network namespace that only has loopback (`unshare --net`, `lo` up, no route anywhere else) under
# `strace`, which records every connect/sendto/sendmsg the processes make. The criterion is zero *attempts* outside
# loopback, not merely zero successful requests: an attempt fails in the namespace, so only the log shows it.
#
#   scripts/fork/egress-test.sh cli <tapcue launcher> [report dir]   # TapCue CLI and MCP scenarios (below)
#   scripts/fork/egress-test.sh run <report dir> <name> -- <cmd...>   # any command, e.g. a Gradle test task
#   scripts/fork/egress-test.sh self-test [report dir]               # the harness must flag a probe that goes out
#
# Counted as an attempt: an IPv4/IPv6 destination outside 127.0.0.0/8 and ::1 (also v4-mapped), any destination port
# 53 (a DNS query, even to a loopback stub resolver such as systemd-resolved's 127.0.0.53, which would forward it), and
# a host name lookup through systemd-resolved's or a running nscd's socket. Proxy variables are cleared and the
# JVM gets no options from the environment, so a request cannot hide behind a proxy listening on loopback.
#
# Needs root for `unshare --net` (it uses sudo otherwise); the commands themselves run as the invoking user.
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
uid="$(id -u)"
user="$(id -un)"

usage() {
  sed -n '9,11p' "$0" | sed 's/^# \{0,1\}//' >&2
  exit 2
}

# check_log <strace log> — prints every attempt outside loopback, exits non-zero if there is one.
check_log() {
  python3 -I - "$1" <<'PY'
import ipaddress, re, sys

log = sys.argv[1]
call = re.compile(r"^(?:\[pid\s+)?(\d+)\]?\s+(connect|sendto|sendmsg|sendmmsg)\((.*)$")
inet = re.compile(r'sa_family=AF_INET, sin_port=htons\((\d+)\), sin_addr=inet_addr\("([^"]+)"\)')
inet6 = re.compile(r'sa_family=AF_INET6, sin6_port=htons\((\d+)\).*?inet_pton\(AF_INET6, "([^"]+)"')
unix = re.compile(r'sa_family=AF_UNIX, sun_path=@?"([^"]+)"')
# systemd-resolved's socket only answers host name lookups. The nscd socket also serves passwd/group lookups (the JVM
# asks for the user name at startup), so it counts only when nscd is actually running: its lookups would then leave
# from the host's network namespace and bypass this test.
RESOLVED = re.compile(r"systemd/resolve|io\.systemd\.Resolve")
NSCD = re.compile(r"nscd/socket")

def outside_loopback(address):
    ip = ipaddress.ip_address(address.split("%", 1)[0])
    if isinstance(ip, ipaddress.IPv6Address) and ip.ipv4_mapped:
        ip = ip.ipv4_mapped
    return not (ip.is_loopback or ip.is_unspecified)

attempts, local = [], 0
with open(log, encoding="utf-8", errors="replace") as f:
    for line in f:
        m = call.match(line.strip())
        if not m:
            continue
        pid, name, rest = m.groups()
        for family in (inet, inet6):
            for port, address in family.findall(rest):
                if port == "53":
                    attempts.append(f"DNS query to {address}:53 ({name}, pid {pid})")
                elif outside_loopback(address):
                    attempts.append(f"connection to {address}:{port} ({name}, pid {pid})")
                else:
                    local += 1
        for path in unix.findall(rest):
            if RESOLVED.search(path):
                attempts.append(f"host name lookup via {path} ({name}, pid {pid})")
            elif NSCD.search(path) and re.search(r"\)\s*=\s*0\s*$", rest):
                attempts.append(f"nscd is running and answered on {path} ({name}, pid {pid})")
print(f"  loopback destinations: {local}; attempts outside loopback: {len(attempts)}")
for a in attempts:
    print(f"  ATTEMPT: {a}")
sys.exit(1 if attempts else 0)
PY
}

# isolated <log> <cmd...> — runs the command in a loopback-only network namespace under strace.
isolated() {
  local log="$1"
  shift
  local sudo=() as_user=()
  if [ "$uid" != 0 ]; then
    sudo=(sudo)
    as_user=(-u "$user")
  fi
  # A PID namespace too, so that a daemon the command leaves behind (an adb server, a Gradle daemon) can be reaped and
  # strace -f does not wait for it forever.
  "${sudo[@]}" unshare --net --pid --fork --mount-proc -- /bin/sh -c 'ip link set lo up && exec "$@"' sh \
    strace "${as_user[@]}" -f --seccomp-bpf -qq -o "$log" -e trace=connect,sendto,sendmsg,sendmmsg -- \
    env -u JAVA_TOOL_OPTIONS -u _JAVA_OPTIONS -u JDK_JAVA_OPTIONS \
      -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY -u http_proxy -u https_proxy -u all_proxy -u no_proxy \
      HOME="$HOME" PATH="$PATH" timeout --kill-after=10 "$limit" "$root/scripts/fork/egress-reap.sh" "$@"
}

# Seconds one isolated command may take before it is killed (and fails): a hang must not stall CI silently.
limit="${EGRESS_TIMEOUT:-300}"

failures=0
summary=""

# scenario <report dir> <name> <expected exit: 0|nonzero|any> <cmd...> — runs one isolated command and checks it.
scenario() {
  local dir="$1" name="$2" expect="$3"
  shift 3
  local out="$dir/$name.out" log="$dir/$name.strace" status=0 verdict=PASS
  echo "== $name: $*"
  isolated "$log" "$@" > "$out" 2>&1 || status=$?
  if [ "$status" = 124 ] || [ "$status" = 137 ]; then
    verdict=FAIL
    echo "  killed after ${limit} s (EGRESS_TIMEOUT)"
  fi
  case "$expect" in
    0) [ "$status" = 0 ] || { verdict=FAIL; echo "  exit code $status, expected 0"; } ;;
    nonzero) [ "$status" != 0 ] || { verdict=FAIL; echo "  exit code 0, expected an error"; } ;;
  esac
  if ! check_log "$log"; then
    verdict=FAIL
  fi
  echo "  exit code $status -> $verdict"
  [ "$verdict" = PASS ] || { failures=$((failures + 1)); sed 's/^/  | /' "$out" | tail -n 30; }
  summary+="$verdict $name (exit $status)"$'\n'
}

# expect_output <report dir> <name> <grep -E pattern> [absent] — checks the output of a finished scenario.
expect_output() {
  local dir="$1" name="$2" pattern="$3" mode="${4:-present}"
  if grep -Eq -- "$pattern" "$dir/$name.out"; then
    [ "$mode" = present ] && return 0
    echo "  $name: output must not match /$pattern/"
  else
    [ "$mode" = absent ] && return 0
    echo "  $name: output does not match /$pattern/"
  fi
  failures=$((failures + 1))
  summary+="FAIL $name output /$pattern/ ($mode)"$'\n'
}

finish() {
  local dir="$1"
  printf '%s' "$summary" > "$dir/summary.txt"
  echo
  echo "Summary ($dir/summary.txt):"
  cat "$dir/summary.txt"
  if [ "$failures" -ne 0 ]; then
    echo "FAIL: $failures check(s) failed"
    exit 1
  fi
  echo "OK: no network attempt outside loopback"
}

mcp_session() {
  # One request per line; the input ends right after the last request (EOF), as with `printf ... | tapcue mcp`.
  printf '%s\n' \
    '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"egress-test","version":"0"}}}' \
    '{"jsonrpc":"2.0","method":"notifications/initialized"}' \
    '{"jsonrpc":"2.0","id":2,"method":"tools/list"}' \
    '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"cheat_sheet","arguments":{}}}' \
    '{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"list_devices","arguments":{}}}'
}

cli() {
  local tapcue="$1" dir="${2:-egress-report}"
  [ -x "$tapcue" ] || { echo "not executable: $tapcue" >&2; exit 2; }
  tapcue="$(cd "$(dirname "$tapcue")" && pwd)/$(basename "$tapcue")"
  mkdir -p "$dir"
  dir="$(cd "$dir" && pwd)"
  local work="$dir/work"
  rm -rf "$work"
  mkdir -p "$work/home" "$work/project/.tapcue" "$work/bin"

  # No adb and no Android SDK: the "no device" case, and nothing but TapCue itself that could open a socket.
  export HOME="$work/home"
  # The JVM takes user.home from the account, not from $HOME; the start script passes JAVA_OPTS on.
  export JAVA_OPTS="-Duser.home=$HOME"
  export PATH="$work/bin:/usr/bin:/bin"
  unset ANDROID_HOME ANDROID_SDK_ROOT ANDROID_NDK_HOME
  ln -sf "$tapcue" "$work/bin/tapcue"
  # The launcher finds Java through JAVA_HOME or PATH; keep the one the caller uses.
  if [ -n "${JAVA_HOME:-}" ]; then export JAVA_HOME; else ln -sf "$(command -v java)" "$work/bin/java"; fi

  printf 'appId: com.example.app\n---\n- launchApp\n- tapOn: "Sign in"\n- assertVisible: "Welcome"\n' > "$work/project/.tapcue/flow.yaml"
  printf 'appId: com.example.app\n---\n- launchApp\n- tapOn: [unclosed\n' > "$work/project/.tapcue/invalid.yaml"
  printf 'appId: com.example.app\n---\n- launchApp\n- notACommand: true\n' > "$work/project/.tapcue/unknown-command.yaml"
  printf 'not a directory\n' > "$work/project/report-file"

  scenario "$dir" version 0 tapcue --version
  expect_output "$dir" version '^tapcue [0-9]+\.[0-9]+\.[0-9]+'
  scenario "$dir" help 0 tapcue --help
  expect_output "$dir" help 'Usage: tapcue'
  scenario "$dir" check-syntax 0 tapcue check-syntax "$work/project/.tapcue/flow.yaml"
  scenario "$dir" invalid-yaml nonzero tapcue check-syntax "$work/project/.tapcue/invalid.yaml"
  expect_output "$dir" invalid-yaml 'Parsing Failed'
  scenario "$dir" unknown-command nonzero tapcue check-syntax "$work/project/.tapcue/unknown-command.yaml"
  expect_output "$dir" unknown-command 'notACommand'
  scenario "$dir" test-no-device nonzero tapcue test "$work/project/.tapcue/flow.yaml"
  expect_output "$dir" test-no-device 'Not enough devices connected \(0\)'
  expect_output "$dir" test-no-device 'at maestro\.' absent
  # An unexpected exception in the CLI: the report output is a file where a directory is needed. The error is reported
  # locally (message and stack trace on stderr) and nothing is sent anywhere (the upstream ErrorReporter is gone).
  scenario "$dir" cli-crash nonzero tapcue test --debug-output "$work/project/report-file" "$work/project/.tapcue/flow.yaml"
  expect_output "$dir" cli-crash 'The stack trace was:'
  expect_output "$dir" cli-crash 'Not a directory'
  scenario "$dir" mcp 0 /bin/sh -c "$(declare -f mcp_session); mcp_session | tapcue mcp"
  for id in 1 2 3 4; do expect_output "$dir" mcp "\"id\":$id,\"result\""; done
  expect_output "$dir" mcp '"name":"tapcue"'
  expect_output "$dir" mcp '"name":"cheat_sheet"'
  if [ -f "$root/e2e/cli/test-cli.sh" ]; then
    scenario "$dir" e2e-cli-test 0 /bin/bash -c "cd '$root' && e2e/cli/test-cli.sh"
  fi
  if [ -e "$HOME/.maestro" ] || [ -e "$HOME/.mobiledev" ]; then
    echo "  upstream Maestro state created in HOME"
    failures=$((failures + 1))
    summary+="FAIL HOME contains .maestro or .mobiledev"$'\n'
  fi
  finish "$dir"
}

self_test() {
  local dir="${1:-egress-self-test}"
  mkdir -p "$dir"
  dir="$(cd "$dir" && pwd)"
  # Each probe goes out in a different way (DNS, raw TCP, an HTTP client, the JVM); the harness must flag every one of
  # them, and must not count a loopback connection.
  local probe name cmd status ok=0
  printf 'class P { public static void main(String[] a) throws Exception { new java.net.Socket().connect(new java.net.InetSocketAddress("93.184.215.14", 80), 3000); } }\n' > "$dir/P.java"
  for probe in "dns|getent ahosts example.com" \
               "tcp|python3 -I -c 'import socket; socket.create_connection((\"1.1.1.1\", 443), timeout=3)'" \
               "curl|curl -sS --max-time 5 https://example.com" \
               "jvm|java $dir/P.java"; do
    name="${probe%%|*}"
    cmd="${probe#*|}"
    echo "== probe $name: $cmd"
    status=0
    isolated "$dir/$name.strace" /bin/sh -c "$cmd" > "$dir/$name.out" 2>&1 || status=$?
    if check_log "$dir/$name.strace"; then
      echo "  FAIL: probe $name went out and was not detected"
      ok=1
    else
      echo "  ok: probe $name detected (exit $status)"
    fi
  done
  echo "== probe loopback: python3 TCP connect to 127.0.0.1"
  isolated "$dir/loopback.strace" python3 -I -c 'import socket
s = socket.socket(); s.bind(("127.0.0.1", 0)); s.listen(1)
socket.create_connection(s.getsockname(), timeout=3)' > "$dir/loopback.out" 2>&1 || true
  if check_log "$dir/loopback.strace"; then
    echo "  ok: loopback connection not counted"
  else
    echo "  FAIL: loopback connection counted as an attempt"
    ok=1
  fi
  if [ "$ok" -ne 0 ]; then
    echo "FAIL: the harness does not detect network attempts reliably"
    exit 1
  fi
  echo "OK: the harness detects every probe"
}

case "${1:-}" in
  cli)
    shift
    [ $# -ge 1 ] || usage
    cli "$@"
    ;;
  run)
    shift
    [ $# -ge 3 ] && [ "$3" = "--" ] || usage
    dir="$1" name="$2"
    shift 3
    limit="${EGRESS_TIMEOUT:-3600}"
    mkdir -p "$dir"
    dir="$(cd "$dir" && pwd)"
    scenario "$dir" "$name" 0 "$@"
    finish "$dir"
    ;;
  self-test)
    shift
    self_test "$@"
    ;;
  *)
    usage
    ;;
esac
