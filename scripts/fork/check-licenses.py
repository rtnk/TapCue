#!/usr/bin/env python3
"""License scan of a TapCue distribution (STEP-06; docs/LICENSE-AUDIT.md §3 and §5).

    scripts/fork/check-licenses.py <tapcue-*.zip | THIRD-PARTY-NOTICES.md> [--allowlist FILE] [--report FILE]

Reads the library table (§1) of THIRD-PARTY-NOTICES.md — from the archive when given one, so the check covers what is
shipped — and compares each declared license with scripts/fork/allowed-licenses.tsv. Fails when a license is not on
the list (a new license, or a known one spelled differently: both need a reviewed entry), when a library offers only
licenses marked alternative-only, or when the table is missing or empty. Writes a per-license summary to --report.
"""
import argparse
import collections
import os
import re
import sys
import zipfile

ROW = re.compile(r"^\| `([^`]+)` \| `([^`]+)` \| (.*) \|$")


def read_notices(path):
    if zipfile.is_zipfile(path):
        with zipfile.ZipFile(path) as zf:
            names = [n for n in zf.namelist() if re.fullmatch(r"[^/]+/THIRD-PARTY-NOTICES\.md", n)]
            if len(names) != 1:
                sys.exit(f"FAIL: {path} has {len(names)} top-level THIRD-PARTY-NOTICES.md files, expected 1")
            return zf.read(names[0]).decode("utf-8")
    with open(path, encoding="utf-8") as f:
        return f.read()


def read_allowlist(path):
    allowed = {}
    with open(path, encoding="utf-8") as f:
        for number, line in enumerate(f, 1):
            line = line.rstrip("\n")
            if not line.strip() or line.startswith("#"):
                continue
            parts = line.split("\t")
            if len(parts) != 3 or parts[1] not in ("allowed", "alternative-only"):
                sys.exit(f"FAIL: {path}:{number}: expected 'spdx<TAB>allowed|alternative-only<TAB>license string'")
            spdx, policy, text = parts
            allowed[text] = (spdx, policy)
    return allowed


def library_rows(notices):
    section = notices.split("## 1. Libraries in `lib/`", 1)
    if len(section) != 2:
        sys.exit("FAIL: THIRD-PARTY-NOTICES.md has no section '## 1. Libraries in `lib/`'")
    body = section[1].split("\n## ", 1)[0]
    rows = []
    for line in body.splitlines():
        m = ROW.match(line.strip())
        if m:
            gav, jar, licenses = m.groups()
            rows.append((gav, jar, [l.strip() for l in licenses.replace("\\|", "|").split("; ") if l.strip()]))
    declared = re.search(r"Third-party libraries: (\d+)\.", body)
    if not rows:
        sys.exit("FAIL: THIRD-PARTY-NOTICES.md §1 lists no libraries")
    if declared and int(declared.group(1)) != len(rows):
        sys.exit(f"FAIL: THIRD-PARTY-NOTICES.md says {declared.group(1)} libraries, the table has {len(rows)} rows")
    return rows


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("source", help="tapcue-*.zip or THIRD-PARTY-NOTICES.md")
    parser.add_argument("--allowlist", default=os.path.join(here, "allowed-licenses.tsv"))
    parser.add_argument("--report", help="write a Markdown summary here")
    args = parser.parse_args()

    allowed = read_allowlist(args.allowlist)
    rows = library_rows(read_notices(args.source))
    problems = []
    by_spdx = collections.defaultdict(list)
    for gav, jar, licenses in rows:
        if not licenses:
            problems.append(f"{gav}: no license")
            continue
        known = []
        for text in licenses:
            if text in allowed:
                known.append(allowed[text])
            else:
                problems.append(f"{gav}: license not on the allowlist: {text}")
        if len(known) != len(licenses):
            continue
        usable = [spdx for spdx, policy in known if policy == "allowed"]
        if not usable:
            problems.append(f"{gav}: only alternative-only licenses: {'; '.join(licenses)}")
            continue
        by_spdx[" / ".join(sorted(set(usable)))].append(gav)

    lines = [f"# License scan — {os.path.basename(args.source)}", "",
             f"Libraries: {len(rows)}; allowlist: `{os.path.relpath(args.allowlist)}` ({len(allowed)} entries).", "",
             "| License used (SPDX) | Libraries |", "|---|---|"]
    lines += [f"| {spdx} | {len(gavs)} |" for spdx, gavs in sorted(by_spdx.items(), key=lambda kv: (-len(kv[1]), kv[0]))]
    if problems:
        lines += ["", f"FAIL: {len(problems)} problem(s):", ""] + [f"- {p}" for p in problems]
    report = "\n".join(lines) + "\n"
    if args.report:
        with open(args.report, "w", encoding="utf-8") as f:
            f.write(report)
    print(report, end="")
    if problems:
        sys.exit(1)
    print(f"OK: every license of the {len(rows)} libraries is on the allowlist")


if __name__ == "__main__":
    main()
