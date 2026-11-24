#!/usr/bin/env python3
"""Lists what a shared album's rows carry, as shapes and counts only: no ids, addresses or text values are printed.

Why: the frame keeps some fields of each row (see tools/album_metadata.py). This shows every field Google sends, so a
new field can be judged without guessing, and tests which fields identify the same photo in two albums.

    export FRAMEREVIVE_ALBUM_LINK='https://photos.app.goo.gl/...'
    export FRAMEREVIVE_FAVORITES_LINK='https://photos.app.goo.gl/...'    # optional: compares fields across the two albums
    python3 tools/album_census.py

Read-only, standard library only, and nothing is written to disk.
"""
import collections
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import album_metadata as am  # noqa: E402


def shape(value):
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "bool"
    return {int: "int", float: "float", str: "str", list: "list", dict: "dict"}.get(type(value), type(value).__name__)


def census(rows, max_depth=3):
    """{path: Counter of shapes, plus 'min' and 'max' for numbers} over every row, to `max_depth` levels."""
    stats = collections.defaultdict(collections.Counter)

    def walk(value, path, depth):
        s = stats[path]
        s["type:" + shape(value)] += 1
        if isinstance(value, (int, float)) and not isinstance(value, bool):
            s["min"] = min(s.get("min", value), value)
            s["max"] = max(s.get("max", value), value)
        if depth >= max_depth:
            return
        if isinstance(value, list):
            for i, item in enumerate(value):
                walk(item, path + (i,), depth + 1)
        elif isinstance(value, dict):
            for key, item in value.items():
                walk(item, path + ("{%s}" % key,), depth + 1)

    for row in rows:
        walk(row, (), 0)
    return stats


def format_census(stats):
    lines = []
    for path in sorted((p for p in stats if p), key=lambda p: [str(x) for x in p]):
        s = stats[path]
        types = ", ".join("%s %d" % (k[5:], v) for k, v in sorted(s.items()) if k.startswith("type:") and k != "type:null")
        if not types:
            continue
        nulls = s.get("type:null", 0)
        extra = " range %s..%s" % (s["min"], s["max"]) if "min" in s else ""
        lines.append("  row%s: %s%s%s" % ("".join("[%s]" % p for p in path), types, ("; null %d" % nulls) if nulls else "", extra))
    return lines


def shared_values(main_rows, other_rows, fields=range(3, 12)):
    """For each string field, how many of `other_rows` carry a value that some main row also carries."""
    out = []
    for i in fields:
        a = collections.Counter(r[i] for r in main_rows if len(r) > i and isinstance(r[i], str) and len(r[i]) > 8)
        b = collections.Counter(r[i] for r in other_rows if len(r) > i and isinstance(r[i], str) and len(r[i]) > 8)
        if a and b:
            out.append((i, sum(1 for k in b if k in a), len(b), len(a) == sum(a.values())))
    return out


def rows_of(link):
    raw = []
    am.fetch_photos(am.Fetcher(), link, raw=raw)
    return raw


def main():
    main_link = os.environ.get(am.LINK_ENV, "").strip()
    if not main_link:
        print("Set %s to the shared album link first." % am.LINK_ENV)
        return 1
    try:
        main_rows = rows_of(main_link)
        print("rows: %d" % len(main_rows))
        print("\n".join(format_census(census(main_rows))))
        other = os.environ.get("FRAMEREVIVE_FAVORITES_LINK", "").strip()
        if other:
            other_rows = rows_of(other)
            print("\nsecond album rows: %d" % len(other_rows))
            for i, hit, total, unique in shared_values(main_rows, other_rows):
                print("  field [%d]: %d of %d share a value with a main photo (unique per photo in main: %s)" % (i, hit, total, unique))
    except am.AlbumError as error:
        print("Error: " + am.scrub(str(error), am.secrets_from(main_link)))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
