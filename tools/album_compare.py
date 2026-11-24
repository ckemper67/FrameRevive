#!/usr/bin/env python3
"""Compares two shared albums (the main one and the favorites one) and reports counts only.

It answers: how many photos of the second album are also in the first (by Google's content key, then by capture time),
whether those copies are the same size, and, with --bytes N, whether the downloaded renditions of N such pairs are
byte-identical. Nothing is written to disk and no ids, addresses or keys are printed.

    export FRAMEREVIVE_ALBUM_LINK='https://photos.app.goo.gl/...'
    export FRAMEREVIVE_FAVORITES_LINK='https://photos.app.goo.gl/...'
    python3 tools/album_compare.py [--bytes 40]
"""
import argparse
import collections
import hashlib
import os
import sys
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import album_metadata as am  # noqa: E402

FAVORITES_ENV = "FRAMEREVIVE_FAVORITES_LINK"
REQUEST_SIDE = 1280   # the frame asks for a square box of the panel's long edge; any one size does for a comparison


def compare(main, other):
    """Counts for photos of `other` against `main`, both lists of Photo."""
    by_key = {p.key: p for p in main if p.key}
    by_time = collections.defaultdict(list)
    for p in main:
        if p.taken > 0:
            by_time[p.taken].append(p)
    out = collections.Counter()
    out["other"] = len(other)
    for p in other:
        twin = by_key.get(p.key) if p.key else None
        times = by_time.get(p.taken, [])
        if twin:
            out["twin by content key"] += 1
            out["twin same size" if (twin.width, twin.height) == (p.width, p.height) else "twin different size"] += 1
        else:
            out["no twin by content key"] += 1
        out["time matches one main photo" if len(times) == 1 else "time matches several" if times else "time matches none"] += 1
        if twin and len(times) > 1:
            out["burst: the key picks one of several same-time photos"] += 1
    return out


def md5_of(url):
    parsed = urllib.parse.urlsplit(url)
    if parsed.scheme != "https" or not (parsed.hostname or "").endswith(".googleusercontent.com"):
        raise ValueError("unexpected photo host")
    with urllib.request.urlopen(url.split("=")[0] + "=w%d-h%d" % (REQUEST_SIDE, REQUEST_SIDE), timeout=60) as response:
        return hashlib.md5(response.read(30_000_000)).hexdigest()


def sample_bytes(main, other, main_urls, other_urls, count, md5=md5_of):
    """Downloads up to `count` twin pairs and counts the byte-identical ones. Returns (tried, identical, failed)."""
    by_key = {p.key: p for p in main if p.key}
    pairs = [(by_key[p.key], p) for p in other if p.key in by_key][:count]
    identical = failed = 0
    for m, o in pairs:
        try:
            identical += md5(main_urls[m.id]) == md5(other_urls[o.id])
        except Exception:
            failed += 1
    return len(pairs), identical, failed


def main():
    parser = argparse.ArgumentParser(description="Compare the favorites album with the main album.")
    parser.add_argument("--bytes", type=int, default=0, help="also download this many twin pairs and compare their bytes")
    args = parser.parse_args()
    links = [os.environ.get(am.LINK_ENV, "").strip(), os.environ.get(FAVORITES_ENV, "").strip()]
    if not all(links):
        print("Set %s and %s to the two shared album links first." % (am.LINK_ENV, FAVORITES_ENV))
        return 1
    try:
        fetcher = am.Fetcher()
        main_urls, other_urls = {}, {}
        main_photos = am.fetch_photos(fetcher, links[0], urls=main_urls)
        other_photos = am.fetch_photos(fetcher, links[1], urls=other_urls)
        print("main album %d photos, second album %d photos" % (len(main_photos), len(other_photos)))
        for key, value in sorted(compare(main_photos, other_photos).items()):
            if key != "other":
                print("  %-52s %d" % (key, value))
        if args.bytes > 0:
            tried, same, failed = sample_bytes(main_photos, other_photos, main_urls, other_urls, args.bytes)
            print("downloaded %d twin pairs at %d px: %d byte-identical, %d failed" % (tried, REQUEST_SIDE, same, failed))
    except am.AlbumError as error:
        print("Error: " + am.scrub(str(error), am.secrets_from(links[0]) | am.secrets_from(links[1])))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
