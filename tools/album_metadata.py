#!/usr/bin/env python3
r"""Read the metadata of a shared Google Photos album (ids, capture times, sizes, content keys) and keep nothing else.

Why: the frame's photo-selection strategy depends on how an album's photos fall on days (how many days, how
many photos on a big day, how much is recent). This writes only `takenMs,offsetMs,id,width,height,key,video,addedMs` per photo,
with no addresses and no images. The id is the photo's media key and `key` is Google's content key (the same for the same
original file in any album); neither grants access to the album, and the file stays in a folder git ignores. The ids also let two albums be compared (does a photo keep its id in a
second album?, see docs/favorites-plan.md). The file drives the playback simulation (see docs/tuning-the-strategy.md).

Usage (the link grants access to the album, so it is read from the environment, never from the command line
where it would show in process lists and shell history):

    export FRAMEREVIVE_ALBUM_LINK='https://photos.app.goo.gl/...'
    python3 tools/album_metadata.py               # writes local/album-metadata-<album title>.csv and prints a summary
    python3 tools/album_metadata.py --from-csv local/album-metadata-<album title>.csv   # summary only, no network

Read-only: it fetches the public shared page and its album pages, exactly as the frame does, and uses no
account cookies. It prints neither the link nor its access key. Standard library only.
Protocol and response layout adapted from xob0t/google_photos_web_client (MIT); see
GOOGLE_PHOTOS_CLIENT_LICENSE.txt. This is an unofficial web interface and can change without notice.
"""
import argparse
import calendar
import collections
import datetime as dt
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

LINK_ENV = "FRAMEREVIVE_ALBUM_LINK"
CSV_HEADER = "takenMs,offsetMs,id,width,height,key,video,addedMs"
OLD_HEADERS = ("takenMs,offsetMs", "takenMs,offsetMs,id")
VIDEO_FIELD = "76647426"   # the extra entry in row[9] that only videos carry

Photo = collections.namedtuple("Photo", "taken offset id width height key video added", defaults=("", 0, 0, "", False, 0))
DAY_MS = 86_400_000
HOUR_MS = 3_600_000
MAX_PAGES = 2000           # about 100 photos a page; a guard against a pagination loop
MAX_BYTES = 25_000_000
HERE = os.path.dirname(os.path.abspath(__file__))
LOCAL_DIR = os.path.join(os.path.dirname(HERE), "local")


class AlbumError(Exception):
    """A problem reading the album; its message never contains the link."""


def at(value, *keys):
    """value[k1][k2]... or None when any step is missing."""
    try:
        for key in keys:
            value = value[key]
        return value
    except (IndexError, KeyError, TypeError):
        return None


# ---- link and host rules, the same as the frame's ---------------------------------------------------------

def validate_link(link):
    """Returns the parsed link, or raises AlbumError unless it is a Google Photos shared album link."""
    try:
        parsed = urllib.parse.urlsplit(link)
        port = parsed.port
    except ValueError:
        raise AlbumError("That is not a web address.")
    ok = (parsed.scheme == "https" and port is None and parsed.username is None and parsed.password is None and
          (parsed.hostname == "photos.app.goo.gl" or (parsed.hostname == "photos.google.com" and parsed.path.startswith("/share/"))))
    if not ok:
        raise AlbumError("Use a Google Photos shared album link (https://photos.app.goo.gl/... or https://photos.google.com/share/...).")
    return parsed


def host_allowed(url):
    parsed = urllib.parse.urlsplit(url)
    return parsed.scheme == "https" and parsed.hostname in ("photos.google.com", "photos.app.goo.gl")


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


class Fetcher:
    """HTTPS with manual redirects, so a redirect can never leave the Google hosts."""

    def __init__(self):
        self.opener = urllib.request.build_opener(_NoRedirect)
        self.opener.addheaders = [("User-Agent", "Mozilla/5.0")]

    def get(self, url, body=None):
        """Returns (bytes, final_url)."""
        for _ in range(6):
            if not host_allowed(url):
                raise AlbumError("The link led somewhere other than Google Photos.")
            data = None if body is None else body.encode("utf-8")
            request = urllib.request.Request(url, data=data)
            if data is not None:
                request.add_header("Content-Type", "application/x-www-form-urlencoded")
            try:
                with self.opener.open(request, timeout=35) as response:
                    payload = response.read(MAX_BYTES + 1)
                    if len(payload) > MAX_BYTES:
                        raise AlbumError("A response was larger than expected.")
                    return payload, url
            except urllib.error.HTTPError as error:
                if error.code in (301, 302, 303, 307, 308):
                    location = error.headers.get("Location")
                    if not location:
                        raise AlbumError("Google sent a redirect without a destination.")
                    url = urllib.parse.urljoin(url, location)
                    body = None
                    continue
                raise AlbumError("Google returned HTTP %d." % error.code)
            except urllib.error.URLError as error:
                raise AlbumError("Could not reach Google Photos (%s)." % type(error.reason).__name__)
        raise AlbumError("Too many redirects.")


# ---- reading the shared album -----------------------------------------------------------------------------

_GLOBAL = re.compile(r"window\.WIZ_global_data\s*=\s*(\{.*?\})\s*;", re.S)
_BLOCKS = re.compile(r"AF_initDataCallback\(\{key: '[^']+'.*?data:(.*?), sideChannel:", re.S)


def first_page(html, resolved_url):
    """Returns (global_data, page, auth_key, source_path) from the shared album's own page."""
    resolved = urllib.parse.urlsplit(resolved_url)
    if resolved.hostname != "photos.google.com" or not resolved.path.startswith("/share/"):
        raise AlbumError("The link did not open a shared album.")
    match = _GLOBAL.search(html)
    if not match:
        raise AlbumError("Shared album page format changed (no session data).")
    try:
        global_data = json.loads(match.group(1))
    except ValueError:
        raise AlbumError("Shared album page format changed (unreadable session data).")
    page = None
    for block in _BLOCKS.finditer(html):
        try:
            candidate = json.loads(block.group(1))
        except ValueError:
            continue
        if isinstance(at(candidate, 1), list) and str(at(candidate, 3, 0) or "").strip():
            page = candidate
            break
    if page is None:
        raise AlbumError("The shared album is unavailable or its format changed.")
    query = urllib.parse.parse_qs(resolved.query)
    auth_key = (query.get("key") or [at(page, 3, 19) or ""])[0]
    if not auth_key:
        raise AlbumError("Shared album access key is missing.")
    return global_data, page, auth_key, resolved.path


def album_rpc(fetcher, global_data, source_path, auth_key, rpc_id, data):
    """One read-only album request (snAcKc lists an album's next page). No write operations exist here."""
    if rpc_id not in ("snAcKc", "Z5xsfc"):
        raise AlbumError("Only album reads are allowed.")
    enc = urllib.parse.quote
    path = global_data.get("Im6cmf", "")
    if not path.startswith("/") or path.startswith("//") or "?" in path:
        raise AlbumError("Shared album page format changed (request path).")
    query = "rpcids=%s&source-path=%s&f.sid=%s&bl=%s&rt=c" % (
        rpc_id, enc(source_path, safe=""), enc(str(global_data.get("FdrFJe", "")), safe=""), enc(str(global_data.get("cfb2h", "")), safe=""))
    payload = [[[rpc_id, json.dumps(data, separators=(",", ":")), None, "generic"]]]
    body = "f.req=" + enc(json.dumps(payload, separators=(",", ":")), safe="")
    if str(global_data.get("SNlM0e", "")).strip():
        body += "&at=" + enc(str(global_data["SNlM0e"]), safe="")
    text, _ = fetcher.get("https://photos.google.com%s/data/batchexecute?%s" % (path, query), body)
    for line in text.decode("utf-8", "replace").splitlines():
        if '"wrb.fr"' not in line:
            continue
        try:
            batch = json.loads(line)
        except ValueError:
            continue
        for row in batch if isinstance(batch, list) else []:
            if at(row, 0) == "wrb.fr" and at(row, 1) == rpc_id and at(row, 2):
                return json.loads(row[2])
    raise AlbumError("Google album response changed.")


def page_times(page, found, urls=None, raw=None):
    """Adds a Photo for each photo on a page to `found`, keyed by id so a repeat is counted once.

    For tools that need more than the CSV holds: `urls` (a dict) receives each photo's address by id, and `raw` (a list)
    receives every row as Google sent it. Neither is ever written to the CSV.
    """
    for row in at(page, 1) or []:
        photo_id, url, taken = at(row, 0), at(row, 1, 0), at(row, 2)
        if raw is not None and isinstance(row, list):
            raw.append(row)
        if photo_id and url and taken is not None:
            if urls is not None:
                urls[str(photo_id)] = url
            offset = at(row, 4)
            key = at(row, 3)
            extras = at(row, 9)
            found[str(photo_id)] = Photo(
                int(taken), int(offset) if offset is not None else 0, str(photo_id),
                int(at(row, 1, 1) or 0), int(at(row, 1, 2) or 0), key if isinstance(key, str) else "",
                isinstance(extras, dict) and VIDEO_FIELD in extras, int(at(row, 5) or 0))


def secrets_from(address):
    """The parts of an address that grant access: the whole address, its query values and its last path segment."""
    parsed = urllib.parse.urlsplit(address)
    found = {address}
    found.update(v for values in urllib.parse.parse_qs(parsed.query).values() for v in values if len(v) >= 6)
    tail = parsed.path.rstrip("/").rsplit("/", 1)[-1]
    if len(tail) >= 6:
        found.add(tail)
    return found


def slug(title):
    """A file-name-safe form of an album title: lower case letters and digits joined by hyphens."""
    words = re.sub(r"[^a-z0-9]+", "-", str(title or "").lower()).strip("-")[:40].strip("-")
    return words or "album"


def default_path(title):
    return os.path.join(LOCAL_DIR, "album-metadata-%s.csv" % slug(title))


def fetch_photos(fetcher, link, progress=None, secrets=None, info=None, urls=None, raw=None):
    """Every photo in the shared album, as a list of Photo tuples sorted by capture time.

    `info`, when given, receives the album's title as info["title"]. `urls` and `raw` are described at page_times.

    Anything that grants access to the album (the link, the address it resolves to, the access key) is added to
    `secrets`, so a caller can scrub it from messages.
    """
    validate_link(link)
    if secrets is not None:
        secrets.update(secrets_from(link))
    html, resolved = fetcher.get(link)
    if secrets is not None:
        secrets.update(secrets_from(resolved))
    global_data, page, auth_key, source_path = first_page(html.decode("utf-8", "replace"), resolved)
    if secrets is not None:
        secrets.add(auth_key)
    album_key = at(page, 3, 0)
    if info is not None:
        info["title"] = at(page, 3, 1)
    if not album_key:
        raise AlbumError("Shared album page format changed (album key).")
    found, seen = {}, set()
    for _ in range(MAX_PAGES):
        page_times(page, found, urls, raw)
        if progress:
            progress(len(found))
        token = at(page, 2)
        if not token:
            break
        if token in seen:
            raise AlbumError("Shared album pagination repeated.")
        seen.add(token)
        page = album_rpc(fetcher, global_data, source_path, auth_key, "snAcKc", [album_key, token, None, auth_key])
    else:
        raise AlbumError("Shared album has more pages than expected.")
    if not found:
        raise AlbumError("The shared album returned no photos.")
    return sorted(found.values())


# ---- the CSV and the summary ------------------------------------------------------------------------------

def write_csv(path, photos):
    directory = os.path.dirname(path)
    if directory:
        os.makedirs(directory, exist_ok=True)
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w", newline="") as out:
        out.write(CSV_HEADER + "\n")
        for item in photos:
            p = Photo(*item)
            out.write("%d,%d,%s,%d,%d,%s,%d,%d\n" % (p.taken, p.offset, p.id, p.width, p.height, p.key, 1 if p.video else 0, p.added))


def read_csv(path):
    """A Photo per line. Older files, with fewer columns, give the missing fields their empty values."""
    photos = []
    with open(path) as source:
        header = source.readline().strip()
        if header != CSV_HEADER and header not in OLD_HEADERS:
            raise AlbumError("That file is not an album metadata file (expected the header %s)." % CSV_HEADER)
        for line in source:
            line = line.strip()
            if not line:
                continue
            f = line.split(",")
            photos.append(Photo(int(f[0]), int(f[1]), f[2] if len(f) > 2 else "", int(f[3]) if len(f) > 3 else 0,
                                int(f[4]) if len(f) > 4 else 0, f[5] if len(f) > 5 else "", len(f) > 6 and f[6] == "1",
                                int(f[7]) if len(f) > 7 else 0))
    return photos


def _date(epoch_day):
    return dt.date(1970, 1, 1) + dt.timedelta(days=epoch_day)


def months_ago(today, months):
    index = today.year * 12 + today.month - 1 - months
    year, month = divmod(index, 12)
    month += 1
    return dt.date(year, month, min(today.day, calendar.monthrange(year, month)[1]))


def _moments(times, gap_ms):
    """Runs of photos with no gap above `gap_ms` between neighbours, by absolute time (so a change of timezone
    cannot split or join them). Each moment is a list of (takenMs, offsetMs)."""
    dated = sorted(t for t in times if t[0] > 0)
    runs = []
    for item in dated:
        if runs and item[0] - runs[-1][-1][0] <= gap_ms:
            runs[-1].append(item)
        else:
            runs.append([item])
    return runs


def summarize(times, today, recent_months=(1, 3, 6, 12, 24), big_day=20, moment_gap_hours=2):
    per_day, undated = {}, 0
    for taken, offset in times:
        if taken <= 0:
            undated += 1
            continue
        day = (taken + offset) // DAY_MS
        per_day[day] = per_day.get(day, 0) + 1
    counts = sorted(per_day.values())

    def pick(fraction):
        return counts[min(len(counts) - 1, int(len(counts) * fraction))] if counts else 0

    recent = {}
    for months in recent_months:
        cutoff = months_ago(today, months).toordinal() - dt.date(1970, 1, 1).toordinal()
        inside = [n for day, n in per_day.items() if day >= cutoff]
        recent[months] = (sum(inside), len(inside))
    by_year = {}
    for day, n in per_day.items():
        photos, days = by_year.get(_date(day).year, (0, 0))
        by_year[_date(day).year] = (photos + n, days + 1)
    dated = sum(counts)
    offsets = collections.Counter(o for t, o in times if t > 0)
    moments = _moments(times, int(moment_gap_hours * HOUR_MS))
    moments_per_day = collections.Counter((m[0][0] + m[0][1]) // DAY_MS for m in moments)
    zones_on_day = collections.defaultdict(set)
    for taken, offset in times:
        if taken > 0:
            zones_on_day[(taken + offset) // DAY_MS].add(offset)
    mixed_days = [d for d, zones in zones_on_day.items() if len(zones) > 1]
    return {
        "offsets": offsets.most_common(4), "offset_kinds": len(offsets), "offset_zero": offsets.get(0, 0),
        "moments": len(moments), "moment_gap_hours": moment_gap_hours,
        "days_by_moments": {k: sum(1 for v in moments_per_day.values() if (v == k if k < 3 else v >= 3)) for k in (1, 2, 3)},
        "midnight_moments": sum(1 for m in moments if (m[0][0] + m[0][1]) // DAY_MS != (m[-1][0] + m[-1][1]) // DAY_MS),
        "mixed_zone_days": len(mixed_days), "mixed_zone_photos": sum(per_day[d] for d in mixed_days),
        "photos": len(times), "undated": undated, "days": len(per_day),
        "median": counts[len(counts) // 2] if counts else 0, "p90": pick(0.9), "p99": pick(0.99), "max": counts[-1] if counts else 0,
        "first": _date(min(per_day)) if per_day else None, "last": _date(max(per_day)) if per_day else None,
        "biggest": [(_date(d), n) for d, n in sorted(per_day.items(), key=lambda kv: -kv[1])[:5]],
        "recent": recent, "by_year": dict(sorted(by_year.items())),
        "single_photo_days": sum(1 for n in counts if n == 1),
        "big_day_threshold": big_day,
        "big_days": sum(1 for n in counts if n >= big_day),
        "big_day_photo_share": (sum(n for n in counts if n >= big_day) / dated) if dated else 0.0,
    }


def format_summary(s):
    if not s["days"]:
        return "No dated photos."
    lines = [
        "%d photos on %d days (%s to %s), %d undated" % (s["photos"], s["days"], s["first"], s["last"], s["undated"]),
        "Photos per day: median %d, 90th percentile %d, 99th %d, most %d" % (s["median"], s["p90"], s["p99"], s["max"]),
        "Days with a single photo: %d (%.0f%% of days)" % (s["single_photo_days"], 100.0 * s["single_photo_days"] / s["days"]),
        "Days with %d+ photos: %d, holding %.0f%% of all photos" % (s["big_day_threshold"], s["big_days"], 100.0 * s["big_day_photo_share"]),
        "Biggest days: " + ", ".join("%s (%d)" % (d, n) for d, n in s["biggest"]),
    ]
    for months, (photos, days) in s["recent"].items():
        lines.append("Last %2d months: %5d photos on %4d days" % (months, photos, days))
    lines.append("By year (photos/days): " + " ".join("%d=%d/%d" % (y, p, d) for y, (p, d) in s["by_year"].items()))
    total = max(1, s["photos"] - s["undated"])
    lines.append("Timezones: %d offsets; most common %s; offset 0 on %d photos (UTC, or a missing timezone)" % (
        s["offset_kinds"], ", ".join("UTC%+g (%.0f%%)" % (o / HOUR_MS, 100.0 * n / total) for o, n in s["offsets"]), s["offset_zero"]))
    by = s["days_by_moments"]
    lines.append("Moments (photos within %g h of each other): %d; days with 1/2/3+ moments: %d/%d/%d; %d moments cross midnight; "
                 "%d days hold photos from 2+ timezones (%d photos)" % (s["moment_gap_hours"], s["moments"], by[1], by[2], by[3],
                                                                         s["midnight_moments"], s["mixed_zone_days"], s["mixed_zone_photos"]))
    return "\n".join(lines)


# ---- command line -----------------------------------------------------------------------------------------

def scrub(text, secrets):
    """Removes anything that grants access to the album from a message before it is shown."""
    for secret in sorted((s for s in secrets if s), key=len, reverse=True):
        text = text.replace(secret, "[link]")
    return text


def main(argv=None, fetcher=None, today=None, stdout=None, stderr=None):
    stdout = stdout or sys.stdout
    stderr = stderr or sys.stderr
    parser = argparse.ArgumentParser(description="Write an album's ids and capture times to a CSV and summarize them.")
    parser.add_argument("--out", help="CSV to write (default: local/album-metadata-<album title>.csv, which git ignores)")
    parser.add_argument("--overwrite", action="store_true", help="replace the CSV if it already exists (otherwise that is an error)")
    parser.add_argument("--link-env", default=LINK_ENV, help="environment variable holding the shared album link")
    parser.add_argument("--from-csv", help="summarize an existing CSV instead of reading the album")
    args = parser.parse_args(argv)
    today = today or dt.date.today()
    secrets = set()
    try:
        if args.from_csv:
            photos = read_csv(args.from_csv)
        else:
            link = os.environ.get(args.link_env, "").strip()
            if not link:
                raise AlbumError("Set %s to the shared album link first (see --help)." % args.link_env)
            fetcher = fetcher or Fetcher()
            last = [0]

            def progress(n):
                if n - last[0] >= 500 or n < 500:
                    print("  read %d photos..." % n, file=stderr)
                    last[0] = n
            secrets.update(secrets_from(link))
            info = {}
            photos = fetch_photos(fetcher, link, progress, secrets, info)
            out = args.out or default_path(info.get("title"))
            if os.path.exists(out) and not args.overwrite and out != os.devnull:
                raise AlbumError("%s already exists; pass --overwrite to replace it, or --out to write somewhere else." % out)
            write_csv(out, photos)
            print("Wrote %d photos to %s (ids and capture times only: no addresses)." % (len(photos), out), file=stdout)
        print(format_summary(summarize([(p.taken, p.offset) for p in photos], today)), file=stdout)
        return 0
    except AlbumError as error:
        print("Error: " + scrub(str(error), secrets), file=stderr)
        return 1
    except Exception as error:  # never show a raw traceback: it could carry the link
        print("Error: unexpected %s while reading the album." % type(error).__name__, file=stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
