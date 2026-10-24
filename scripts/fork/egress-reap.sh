#!/bin/sh
# Runs "$@", then kills every process it left behind in its PID namespace — a daemon such as an adb server it started —
# so that `strace -f`, which waits for every traced process, can finish. Used by egress-test.sh inside `unshare --pid`;
# outside a PID namespace of its own it would kill the caller's other processes, so it refuses to run there.
# In egress-test.sh's namespace, PID 1 is the strace that traces this script.
if [ "$(cat /proc/1/comm 2>/dev/null)" != strace ]; then
  echo "egress-reap.sh: not in egress-test.sh's PID namespace (PID 1 is not strace)" >&2
  exit 2
fi
"$@"
status=$?
ancestors=" $$ "
p=$$
while [ "$p" -gt 1 ]; do
  p="$(sed 's/.*) //' "/proc/$p/stat" | cut -d' ' -f2)"
  ancestors="$ancestors$p "
done
for q in /proc/[0-9]*; do
  q="${q#/proc/}"
  case "$ancestors" in
    *" $q "*) ;;
    *) kill -KILL "$q" 2>/dev/null ;;
  esac
done
exit "$status"
