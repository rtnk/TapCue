#!/usr/bin/env bash
# Fails if the build fetched or packaged simulator-server (licensed only for official
# Maestro releases, see docs/LICENSE-AUDIT.md L-01) or the Viewer frontend that drives it.
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
status=0

generated="$root/maestro-cli/build/generated/simulator-server"
if [ -e "$generated" ]; then
  echo "FORBIDDEN: $generated exists (downloadSimulatorServer ran)"
  status=1
fi

while IFS= read -r -d '' archive; do
  if unzip -Z1 "$archive" 2>/dev/null | grep -Eq '(^|/)(deps/simulator-server/|simulator-server(\.exe)?$|mcp-viewer/)'; then
    echo "FORBIDDEN: $archive contains simulator-server or mcp-viewer entries"
    status=1
  fi
done < <(find "$root" -path '*/build/*' \( -name '*.jar' -o -name '*.zip' \) -not -path '*/node_modules/*' -print0)

if [ "$status" -eq 0 ]; then
  echo "OK: no simulator-server or mcp-viewer artifacts in build outputs"
fi
exit "$status"
