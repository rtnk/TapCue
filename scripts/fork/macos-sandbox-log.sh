#!/usr/bin/env bash
# Prints the network denials the macOS sandbox logged since <start> for processes named <process> (STEP-06, CI job
# `macos`): the register of attempts for commands run under sandbox-exec with a profile that denies network-outbound.
#
#   scripts/fork/macos-sandbox-log.sh "<YYYY-MM-DD HH:MM:SS>" <process name>
set -euo pipefail
start="${1:?usage: $0 <start> <process>}"
process="${2:?usage: $0 <start> <process>}"
sudo log show --style compact --start "$start" \
  --predicate "(sender == \"Sandbox\" OR process == \"kernel\" OR process == \"sandboxd\") AND eventMessage CONTAINS \"network-outbound\" AND eventMessage CONTAINS \"$process\"" \
  | grep -E "deny" || echo "no network-outbound denials for $process since $start"
