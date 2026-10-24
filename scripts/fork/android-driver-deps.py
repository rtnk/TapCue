#!/usr/bin/env python3
"""Lists the libraries compiled into the Android driver APKs, with the licenses their POMs declare (STEP-06, L-05).

    scripts/fork/android-driver-deps.py --app app-deps.txt --server server-deps.txt [--report FILE]

The inputs are the output of `./gradlew :maestro-android:dependencies --configuration debugRuntimeClasspath`
(maestro-app.apk) and `--configuration debugAndroidTestRuntimeClasspath` (maestro-server.apk, the instrumentation
APK). POMs come from the Gradle cache when there, else from Google Maven or Maven Central (build time, as B-03 in
docs/NETWORK-AUDIT.md); licenses follow parent POMs. The report is the source of docs/audit/notices-supplement.md §3.2.
It lists, it does not judge: a license that is not open source (e.g. the Android SDK License) is reported as declared.
"""
import argparse
import glob
import os
import re
import sys
import urllib.request
import xml.etree.ElementTree as ET

REPOSITORIES = ["https://dl.google.com/dl/android/maven2", "https://repo.maven.apache.org/maven2"]
LINE = re.compile(r"^[| \\+-]*--- (?P<g>[^:\s]+):(?P<a>[^:\s]+)(?::(?P<v>[^\s]+))?(?: -> (?P<r>[^\s]+))?(?: \((?:\*|c|n)\))?\s*$")
NS = re.compile(r"^\{[^}]*\}")


def parse_tree(path):
    gavs = set()
    with open(path, encoding="utf-8") as f:
        for line in f:
            m = LINE.match(line.rstrip())
            if not m or line.strip().endswith("(c)") or line.strip().endswith("(n)"):
                continue
            version = m.group("r") or m.group("v")
            if version:
                gavs.add((m.group("g"), m.group("a"), version))
    return gavs


def fetch_pom(group, artifact, version, cache={}):
    key = (group, artifact, version)
    if key in cache:
        return cache[key]
    gradle_home = os.environ.get("GRADLE_USER_HOME", os.path.expanduser("~/.gradle"))
    local = glob.glob(os.path.join(gradle_home, "caches/modules-2/files-2.1", group, artifact, version, "*",
                                   f"{artifact}-{version}.pom"))
    data = None
    if local:
        with open(local[0], "rb") as f:
            data = f.read()
    else:
        path = f"{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}.pom"
        for repository in REPOSITORIES:
            try:
                with urllib.request.urlopen(f"{repository}/{path}", timeout=30) as response:
                    data = response.read()
                    break
            except Exception:  # noqa: BLE001 - try the next repository
                continue
    root = None
    if data:
        try:
            root = ET.fromstring(data)
            for element in root.iter():
                element.tag = NS.sub("", element.tag)
        except ET.ParseError:
            root = None
    cache[key] = root
    return root


def licenses(group, artifact, version, depth=0):
    pom = fetch_pom(group, artifact, version)
    if pom is None:
        return None
    found = []
    for lic in pom.findall("./licenses/license"):
        name = (lic.findtext("name") or "").strip()
        url = (lic.findtext("url") or "").strip()
        if name or url:
            found.append(f"{name} ({url})" if url else name)
    if found or depth > 5:
        return found
    parent = pom.find("./parent")
    if parent is not None:
        return licenses(parent.findtext("groupId"), parent.findtext("artifactId"), parent.findtext("version"), depth + 1)
    return found


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--app", required=True, help="dependencies of debugRuntimeClasspath (maestro-app.apk)")
    parser.add_argument("--server", required=True, help="dependencies of debugAndroidTestRuntimeClasspath (maestro-server.apk)")
    parser.add_argument("--report", help="write the Markdown report here")
    args = parser.parse_args()

    app, server = parse_tree(args.app), parse_tree(args.server)
    if not app or not server:
        sys.exit(f"FAIL: no dependencies parsed (app: {len(app)}, server: {len(server)})")
    rows, unknown = [], 0
    for group, artifact, version in sorted(app | server):
        where = ", ".join(n for n, s in (("app", app), ("server", server)) if (group, artifact, version) in s)
        found = licenses(group, artifact, version)
        if found is None:
            text, unknown = "POM not found", unknown + 1
        elif not found:
            text, unknown = "no license in POM", unknown + 1
        else:
            text = "; ".join(found)
        rows.append(f"| `{group}:{artifact}:{version}` | {where} | {text.replace('|', '/')} |")
    lines = ["# Android driver APK libraries (`maestro-android`)", "",
             f"Libraries: {len(rows)} (maestro-app.apk: {len(app)}, maestro-server.apk: {len(server)}; the instrumentation "
             "APK leaves out what the app APK already has, but the Gradle configuration lists both). "
             f"Without a license found: {unknown}.", "",
             "| Library | APK | License (POM) |", "|---|---|---|"] + rows
    report = "\n".join(lines) + "\n"
    if args.report:
        with open(args.report, "w", encoding="utf-8") as f:
            f.write(report)
    print(report, end="")


if __name__ == "__main__":
    main()
