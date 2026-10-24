#!/usr/bin/env bash
# Checks a TapCue distribution archive before it is published (STEP-05, gates G-3/G-4 in docs/ROADMAP-0.1.md).
#
#   scripts/verify-dist.sh [--smoke] <tapcue-*.zip> [SHA256SUMS]
#
# Fails when the archive
#   - lacks bin/tapcue, lib/*.jar, LICENSE, NOTICE (with the origin notice), THIRD-PARTY-NOTICES.md or README.md;
#   - contains, directly or inside a JAR, a forbidden path: simulator-server (L-01), the Viewer, truffle-enterprise
#     (L-03), prebuilt iOS driver ZIPs or Apple frameworks (L-04, D-5), PostHog, remote AI, Selenium/web driver;
#   - contains a native library or executable that is not on the allowlist below (each entry names its license);
#   - has a third-party JAR in lib/ that THIRD-PARTY-NOTICES.md does not list;
#   - has start scripts that link to the vendor (maestro.dev, mobile.dev; N-35) or do not check for Java 17 (D-7);
#   - does not match SHA256SUMS (when given, or when one lies next to the archive).
# With --smoke it also unpacks the archive and runs `bin/tapcue --version` with an empty HOME.
set -euo pipefail

smoke=false
if [ "${1:-}" = "--smoke" ]; then
  smoke=true
  shift
fi

archive="${1:-}"
if [ -z "$archive" ] || [ ! -f "$archive" ]; then
  echo "usage: $0 [--smoke] <tapcue-*.zip> [SHA256SUMS]" >&2
  exit 2
fi
sums="${2:-$(dirname "$archive")/SHA256SUMS}"

python3 -I - "$archive" "$sums" <<'PY'
import hashlib, io, os, re, sys, zipfile

archive, sums = sys.argv[1], sys.argv[2]
problems = []

def problem(message):
    problems.append(message)

# (forbidden path pattern, reason). Matched against archive entries and entries of every JAR.
FORBIDDEN = [
    (r"simulator-server", "simulator-server (LICENSE-AUDIT L-01)"),
    (r"(^|/)mcp-viewer(/|$)|(^|/)maestro/cli/mcp/viewer/", "MCP Viewer (depends on L-01)"),
    (r"truffle-enterprise|(^|/)com/oracle/truffle/enterprise/", "GraalVM truffle-enterprise (L-03, D-6)"),
    (r"(^|/)driver-(iPhoneSimulator|iphoneos)/|maestro-driver-ios(UITests-Runner)?\.zip$|\.xctestrun$",
     "prebuilt iOS driver (L-04, D-5)"),
    (r"\.framework/|\.xctest/|\.app/", "Apple bundle / prebuilt iOS driver (L-04, D-5)"),
    (r"(^|/)com/posthog/|posthog[^/]*\.jar$", "PostHog telemetry"),
    (r"(^|/)maestro/ai/|(^|/)ai_report\.css$|(^|/)tailwind\.config\.js$|maestro-ai[^/]*\.jar$", "remote AI"),
    (r"(^|/)org/openqa/|selenium[^/]*\.jar$|selenium-manager|(^|/)maestro/web/|maestro-web[^/]*\.jar$",
     "web platform / Selenium (N-26, D-4)"),
]

# (JAR file name pattern, entry pattern, license). Every native library, executable or nested archive must match one.
ALLOWED_BINARIES = [
    (r"(jansi|jline-native)-[\d.]+\.jar", r"org/jline/nativ/.+/(lib)?jlinenative\.(so|jnilib|dylib|dll)", "BSD-3-Clause (JLine)"),
    (r"jna-[\d.]+\.jar", r"com/sun/jna/[^/]+/(lib)?jnidispatch\.(so|a|jnilib|dll)", "Apache-2.0 or LGPL-2.1 (JNA; Apache-2.0 chosen)"),
    (r"skiko-awt-runtime-[a-z0-9-]+-[\d.]+\.jar", r"libskiko-[a-z0-9-]+\.(so|dylib|dll)(\.sha256)?", "Apache-2.0 (Skiko), BSD-3-Clause (Skia)"),
    (r"truffle-api-[\d.]+\.jar", r"META-INF/resources/engine/libtruffleattach/.+", "UPL-1.0 (GraalVM Truffle)"),
    (r"maestro-cli-[\d.]+\.jar", r"deps/applesimutils", "MIT (AppleSimulatorUtils, FMDB)"),
    (r"maestro-client\.jar", r"maestro-(app|server)\.apk", "Apache-2.0 (TapCue Android driver, LICENSE-AUDIT L-05)"),
    (r"okhttp-[\d.]+\.jar", r"okhttp3/internal/publicsuffix/publicsuffixes\.gz", "MPL-2.0 data (Public Suffix List), Apache-2.0 (OkHttp)"),
]

ORIGIN_NOTICE = "based on Maestro (Apache-2.0), not affiliated with mobile.dev"
VENDOR_LINK = re.compile(r"maestro\.dev|mobile\.dev|mobile-dev-inc")
MACHO = {b"\xfe\xed\xfa\xce", b"\xfe\xed\xfa\xcf", b"\xce\xfa\xed\xfe", b"\xcf\xfa\xed\xfe"}

def binary_kind(name, head):
    if name.endswith(".class"):
        return None
    if head[:4] == b"\x7fELF":
        return "ELF"
    if head[:4] in MACHO:
        return "Mach-O"
    # 0xCAFEBABE is also the class file magic; a universal Mach-O has a small architecture count after it.
    if head[:4] == b"\xca\xfe\xba\xbe" and len(head) >= 8 and int.from_bytes(head[4:8], "big") <= 20:
        return "Mach-O universal"
    if head[:2] == b"MZ":
        return "PE"
    if re.search(r"\.(so|dylib|dll|jnilib|a|exe)$", name, re.I):
        return "native library"
    if re.search(r"\.(zip|apk|aar|jar|tar|gz|tgz|xz|7z)$", name, re.I):
        return "nested archive"
    return None

def check_forbidden(where, name):
    for pattern, reason in FORBIDDEN:
        if re.search(pattern, name, re.I):
            problem(f"forbidden: {where}{name} — {reason}")

def allowed(jar_name, entry):
    for jar_pattern, entry_pattern, _license in ALLOWED_BINARIES:
        if re.fullmatch(jar_pattern, jar_name) and re.fullmatch(entry_pattern, entry):
            return True
    return False

zf = zipfile.ZipFile(archive)
names = [n for n in zf.namelist() if not n.endswith("/")]
roots = {n.split("/", 1)[0] for n in zf.namelist()}
if len(roots) != 1:
    problem(f"archive must have exactly one top-level directory, found: {sorted(roots)}")
root = sorted(roots)[0] + "/"
rel = {n[len(root):]: n for n in names if n.startswith(root)}

for required in ["bin/tapcue", "bin/jvm-version.jar", "LICENSE", "NOTICE", "THIRD-PARTY-NOTICES.md", "README.md"]:
    if required not in rel:
        problem(f"missing: {required}")
jars = sorted(p for p in rel if p.startswith("lib/") and p.endswith(".jar"))
if not jars:
    problem("missing: lib/*.jar")
unexpected = [p for p in rel if not (p.startswith("lib/") or p.startswith("bin/")
                                     or p in {"LICENSE", "NOTICE", "THIRD-PARTY-NOTICES.md", "README.md"})]
for p in unexpected:
    problem(f"unexpected file at the top level: {p}")

def text(path):
    return zf.read(rel[path]).decode("utf-8", "replace") if path in rel else ""

if ORIGIN_NOTICE not in text("NOTICE"):
    problem(f"NOTICE does not contain: {ORIGIN_NOTICE}")
if "Apache License" not in text("LICENSE"):
    problem("LICENSE is not the Apache License")
if not re.search(r"Java 17", text("README.md")):
    problem("README.md does not state the Java 17+ requirement (D-7)")

for path in sorted(rel):
    if path.startswith("bin/") and not path.endswith(".jar"):
        script = text(path)
        if VENDOR_LINK.search(script):
            problem(f"vendor link in start script {path}: {VENDOR_LINK.search(script).group(0)} (N-35)")
if "Java 17 or higher is required" not in text("bin/tapcue"):
    problem("bin/tapcue does not check for Java 17 (D-7)")
info = zf.getinfo(rel["bin/tapcue"]) if "bin/tapcue" in rel else None
if info is not None and info.create_system == 3 and not (info.external_attr >> 16) & 0o111:
    problem("bin/tapcue is not executable in the archive")

for path in sorted(rel):
    check_forbidden("", path)
    if not path.endswith(".jar"):
        with zf.open(rel[path]) as f:
            kind = binary_kind(path, f.read(8))
        if kind and path != "bin/jvm-version.jar":
            problem(f"{kind} outside lib/: {path}")

natives = []
for path in jars:
    jar_name = path.rsplit("/", 1)[1]
    with zipfile.ZipFile(io.BytesIO(zf.read(rel[path]))) as jar:
        for entry in jar.infolist():
            if entry.is_dir():
                continue
            check_forbidden(f"{path}!/", entry.filename)
            with jar.open(entry) as f:
                kind = binary_kind(entry.filename, f.read(8))
            if kind:
                if allowed(jar_name, entry.filename):
                    natives.append(f"{jar_name}!/{entry.filename}")
                else:
                    problem(f"{kind} not on the allowlist: {path}!/{entry.filename}")

notices = text("THIRD-PARTY-NOTICES.md")
for path in jars:
    jar_name = path.rsplit("/", 1)[1]
    if re.fullmatch(r"maestro-[a-z-]+(-[\d.]+)?\.jar", jar_name):
        if f"`{jar_name}`" not in notices:
            problem(f"THIRD-PARTY-NOTICES.md does not list the TapCue module {jar_name}")
    elif f"| `{jar_name}` |" not in notices:
        problem(f"THIRD-PARTY-NOTICES.md does not list {jar_name}")

if os.path.isfile(sums):
    digest = hashlib.sha256()
    with open(archive, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            digest.update(chunk)
    expected = None
    for line in open(sums, encoding="utf-8"):
        parts = line.split()
        if len(parts) == 2 and parts[1].lstrip("*") == os.path.basename(archive):
            expected = parts[0].lower()
    if expected is None:
        problem(f"{sums} has no entry for {os.path.basename(archive)}")
    elif expected != digest.hexdigest():
        problem(f"SHA-256 mismatch: {sums} says {expected}, archive is {digest.hexdigest()}")
    else:
        print(f"OK: SHA-256 matches {sums}")
else:
    print(f"note: no {sums}, checksum not verified")

print(f"checked {len(names)} files, {len(jars)} JARs; allowed native binaries / nested archives: {len(natives)}")
by_jar = {}
for n in natives:
    jar_name, entry = n.split("!/", 1)
    by_jar.setdefault(jar_name, []).append(entry)
for jar_name, entries in sorted(by_jar.items()):
    print(f"  {jar_name}: {len(entries)} ({', '.join(sorted({e.rsplit('/', 1)[-1] for e in entries}))})")
if problems:
    print(f"FAIL: {len(problems)} problem(s) in {archive}", file=sys.stderr)
    for p in problems:
        print(f"  - {p}", file=sys.stderr)
    sys.exit(1)
print(f"OK: {archive} passed the distribution checks")
PY

if [ "$smoke" = true ]; then
  work="$(mktemp -d)"
  trap 'rm -rf "$work"' EXIT
  mkdir -p "$work/home" "$work/unpacked"
  unzip -q "$archive" -d "$work/unpacked"
  launcher="$(find "$work/unpacked" -path '*/bin/tapcue' -type f | head -n 1)"
  # Run as a user would: no JVM options from the environment, an empty home directory.
  output="$(env -u JAVA_TOOL_OPTIONS -u _JAVA_OPTIONS HOME="$work/home" "$launcher" --version 2>&1)"
  echo "$output"
  if ! printf '%s\n' "$output" | grep -Eq '^tapcue [0-9]+\.[0-9]+\.[0-9]+'; then
    echo "FAIL: bin/tapcue --version did not print 'tapcue <version>'" >&2
    exit 1
  fi
  if ! printf '%s\n' "$output" | grep -Fq "based on Maestro (Apache-2.0), not affiliated with mobile.dev"; then
    echo "FAIL: bin/tapcue --version did not print the origin notice" >&2
    exit 1
  fi
  if [ -e "$work/home/.maestro" ] || [ -e "$work/home/.mobiledev" ]; then
    echo "FAIL: bin/tapcue --version created upstream Maestro state in HOME" >&2
    exit 1
  fi
  echo "OK: bin/tapcue --version from the unpacked archive"
fi
