#!/usr/bin/env python3
"""Release notes for a TapCue GitHub Release (STEP-07, docs/RELEASING.md).

    scripts/fork/release-notes.py <version> [--changelog CHANGELOG.md] [--sums SHA256SUMS] [--build-info FILE]
                                  [--require-section] [--output FILE]

Takes the section `### <version>` (optionally `### v<version>`, anything after the version on that line, e.g. a date, is
kept) from the fork part of CHANGELOG.md — between `## TapCue` and the next `## ` heading, so upstream Maestro's sections of
the same number are never used — and appends the origin notice (D-1), the archive's SHA-256 from SHA256SUMS and the build
facts from --build-info (key=value lines).

With --require-section (publish mode) it fails when the section is missing or empty, or lacks a heading for known
limitations and untested platforms (`Known limitations` or `Znane ograniczenia`; gate G-8). Without it a missing section
is replaced by a placeholder, so a validation run still shows what the notes would look like.
"""
import argparse
import re
import sys

ORIGIN = "TapCue is based on Maestro (Apache-2.0) and is not affiliated with mobile.dev."
LIMITATIONS = re.compile(r"^#{4,6}\s+.*(known limitations|znane ograniczenia)", re.I | re.M)


def fork_part(text):
    match = re.search(r"^## TapCue\b.*$", text, re.M)
    if not match:
        sys.exit("FAIL: CHANGELOG has no '## TapCue' section")
    rest = text[match.end():]
    end = re.search(r"^## ", rest, re.M)
    return rest[: end.start()] if end else rest


def section(part, version):
    heading = re.compile(r"^### v?" + re.escape(version) + r"(?=\s|$).*$", re.M)
    found = list(heading.finditer(part))
    if len(found) > 1:
        sys.exit(f"FAIL: CHANGELOG has {len(found)} '### {version}' sections in the TapCue part")
    if not found:
        return None
    rest = part[found[0].end():]
    end = re.search(r"^### ", rest, re.M)
    return (rest[: end.start()] if end else rest).strip()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("version")
    parser.add_argument("--changelog", default="CHANGELOG.md")
    parser.add_argument("--sums")
    parser.add_argument("--build-info")
    parser.add_argument("--require-section", action="store_true")
    parser.add_argument("--output")
    args = parser.parse_args()

    with open(args.changelog, encoding="utf-8") as f:
        body = section(fork_part(f.read()), args.version)

    if args.require_section:
        if not body:
            sys.exit(f"FAIL: CHANGELOG has no non-empty '### {args.version}' section in the TapCue part")
        if not LIMITATIONS.search(body):
            sys.exit(f"FAIL: the '### {args.version}' section has no 'Known limitations' / 'Znane ograniczenia' heading (G-8)")
    elif not body:
        body = f"_No `### {args.version}` section in CHANGELOG.md yet (validation run): a tag push would fail here._"

    lines = [body, "", "---", "", ORIGIN, ""]
    if args.sums:
        with open(args.sums, encoding="utf-8") as f:
            entries = [line.split() for line in f if line.strip()]
        lines.append("SHA-256 (also in the `SHA256SUMS` asset; check with `shasum -a 256 -c SHA256SUMS`):")
        lines.append("")
        lines += [f"- `{name}`: `{digest}`" for digest, name in entries]
        lines.append("")
    if args.build_info:
        with open(args.build_info, encoding="utf-8") as f:
            facts = [line.rstrip("\n").split("=", 1) for line in f if "=" in line]
        lines.append("Built by `.github/workflows/release.yml` (rebuilding: `docs/RELEASING.md`):")
        lines.append("")
        lines += [f"- {key}: `{value}`" for key, value in facts]
        lines.append("")

    notes = "\n".join(lines)
    if args.output:
        with open(args.output, "w", encoding="utf-8") as f:
            f.write(notes)
    else:
        sys.stdout.write(notes)


if __name__ == "__main__":
    main()
