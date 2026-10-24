#!/usr/bin/env python3
"""Compares two TapCue distribution archives entry by entry (STEP-07: is the release build reproducible?).

    scripts/fork/compare-archives.py <a.zip> <b.zip>

Exits 0 when both files have the same bytes. Otherwise lists what differs — entries missing on one side, different
contents (looking inside JARs to name the classes or resources), modes or timestamps — and exits 1.
"""
import hashlib
import io
import sys
import zipfile


def digest(data):
    return hashlib.sha256(data).hexdigest()


def entries(zf):
    return {info.filename: info for info in zf.infolist()}


def compare(a, b, prefix, out, depth=0):
    ea, eb = entries(a), entries(b)
    for name in sorted(ea.keys() - eb.keys()):
        out.append(f"only in first:  {prefix}{name}")
    for name in sorted(eb.keys() - ea.keys()):
        out.append(f"only in second: {prefix}{name}")
    if [i.filename for i in a.infolist()] != [i.filename for i in b.infolist()] and ea.keys() == eb.keys():
        out.append(f"entry order differs: {prefix or 'archive'}")
    for name in sorted(ea.keys() & eb.keys()):
        ia, ib = ea[name], eb[name]
        if ia.external_attr != ib.external_attr:
            out.append(f"mode differs:   {prefix}{name} ({oct(ia.external_attr >> 16)} vs {oct(ib.external_attr >> 16)})")
        if ia.date_time != ib.date_time:
            out.append(f"time differs:   {prefix}{name} ({ia.date_time} vs {ib.date_time})")
        if ia.CRC == ib.CRC and ia.file_size == ib.file_size:
            continue
        da, db = a.read(name), b.read(name)
        if da == db:
            continue
        if depth < 2 and name.endswith((".jar", ".zip", ".apk")):
            try:
                with zipfile.ZipFile(io.BytesIO(da)) as ja, zipfile.ZipFile(io.BytesIO(db)) as jb:
                    before = len(out)
                    compare(ja, jb, f"{prefix}{name}!/", out, depth + 1)
                    if len(out) == before:
                        out.append(f"bytes differ:   {prefix}{name} (same entries; compression or headers)")
                    continue
            except zipfile.BadZipFile:
                pass
        out.append(f"content differs: {prefix}{name} ({len(da)} vs {len(db)} bytes)")


def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__.strip().splitlines()[2].strip())
    first, second = sys.argv[1:]
    with open(first, "rb") as f:
        da = f.read()
    with open(second, "rb") as f:
        db = f.read()
    print(f"{digest(da)}  {first}")
    print(f"{digest(db)}  {second}")
    if da == db:
        print("IDENTICAL")
        return
    out = []
    with zipfile.ZipFile(first) as a, zipfile.ZipFile(second) as b:
        compare(a, b, "", out)
    print("DIFFERENT:")
    for line in out[:200] or ["bytes differ (same entries; compression or headers)"]:
        print("  " + line)
    if len(out) > 200:
        print(f"  ... and {len(out) - 200} more")
    sys.exit(1)


if __name__ == "__main__":
    main()
