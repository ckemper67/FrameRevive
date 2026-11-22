"""Tests for album_dates.py. They use a fake network, so no album and no Google access are needed.

Run: python3 -m unittest discover -s tools -p 'test_*.py'
"""
import datetime as dt
import io
import json
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import album_dates as ad  # noqa: E402

LINK = "https://photos.app.goo.gl/SecretShortLink123"
RESOLVED = "https://photos.google.com/share/AF1QipSecretAlbumId?key=SecretAccessKey99"
GLOBAL = {"FdrFJe": "sid-1", "cfb2h": "boq_build", "SNlM0e": "at-token", "Im6cmf": "/_/PhotosUi"}
DAY = 86_400_000


def row(photo_id, taken, offset=None, url=True):
    return [photo_id, ["https://lh3.example/" + photo_id, 100, 100] if url else None, taken, None, offset]


def page(rows, token=None, album_key="album-key-1", title="My trip"):
    album = [album_key, title] + [None] * 17 + ["embedded-key", None, 321]
    return [None, rows, token, album]


def share_html(first):
    return ("<html><script>window.WIZ_global_data = %s;</script>"
            "<script>AF_initDataCallback({key: 'ds:9', hash: '1', data:%s, sideChannel: {}});</script></html>"
            % (json.dumps(GLOBAL), json.dumps(first)))


class FakeFetcher:
    """Serves the share page, then one batchexecute reply per requested page token."""

    def __init__(self, first, later=(), resolved=RESOLVED):
        self.first, self.later, self.resolved = first, list(later), resolved
        self.requests = []

    def get(self, url, body=None):
        self.requests.append((url, body))
        if "batchexecute" not in url:
            return share_html(self.first).encode(), self.resolved
        reply = self.later.pop(0)
        line = json.dumps([["wrb.fr", "snAcKc", json.dumps(reply), None, None, None, "generic"]])
        return (")]}'\n\n" + line + "\n").encode(), url


class LinkTests(unittest.TestCase):
    def test_accepts_shared_album_links_only(self):
        for good in (LINK, "https://photos.google.com/share/AF1Qip?key=k"):
            ad.validate_link(good)
        for bad in ("http://photos.app.goo.gl/x", "https://photos.google.com/u/0/", "https://evil.example/share/x",
                    "https://user:pw@photos.app.goo.gl/x", "https://photos.app.goo.gl:8443/x", "not a link", ""):
            with self.assertRaises(ad.AlbumError, msg=bad):
                ad.validate_link(bad)

    def test_redirects_may_only_stay_on_google_photos(self):
        self.assertTrue(ad.host_allowed("https://photos.google.com/share/x"))
        self.assertTrue(ad.host_allowed("https://photos.app.goo.gl/x"))
        for url in ("http://photos.google.com/x", "https://evil.example/x", "https://photos.google.com.evil.example/x"):
            self.assertFalse(ad.host_allowed(url), url)


class ReadingTests(unittest.TestCase):
    def test_reads_every_page_and_keeps_times_only(self):
        first = page([row("a", 10 * DAY, 3600_000), row("b", 11 * DAY)], token="t1")
        second = page([row("c", 12 * DAY, 0)], token="t2")
        third = page([row("d", 13 * DAY, None)])
        fetcher = FakeFetcher(first, [second, third])
        times = ad.fetch_times(fetcher, LINK)
        self.assertEqual(times, [(10 * DAY, 3600_000), (11 * DAY, 0), (12 * DAY, 0), (13 * DAY, 0)])
        batch = [r for r in fetcher.requests if "batchexecute" in r[0]]
        self.assertEqual(len(batch), 2)
        self.assertIn("rpcids=snAcKc", batch[0][0])
        self.assertIn("at=at-token", batch[0][1])

    def test_a_photo_seen_twice_counts_once_and_unusable_rows_are_skipped(self):
        first = page([row("a", 5 * DAY), row("a", 5 * DAY), row("nodate", None), row("nourl", 6 * DAY, url=False)])
        self.assertEqual(ad.fetch_times(FakeFetcher(first), LINK), [(5 * DAY, 0)])

    def test_the_next_page_request_uses_the_album_key_token_and_access_key(self):
        fetcher = FakeFetcher(page([row("a", DAY)], token="tok/1+2"), [page([row("b", 2 * DAY)])])
        ad.fetch_times(fetcher, LINK)
        body = [r for r in fetcher.requests if "batchexecute" in r[0]][0][1]
        decoded = __import__("urllib.parse", fromlist=["unquote"]).unquote(body.split("&")[0].split("=", 1)[1])
        self.assertEqual(json.loads(json.loads(decoded)[0][0][1]), ["album-key-1", "tok/1+2", None, "SecretAccessKey99"])

    def test_an_album_without_photos_is_an_error(self):
        with self.assertRaises(ad.AlbumError):
            ad.fetch_times(FakeFetcher(page([])), LINK)

    def test_a_repeating_page_token_stops_the_read(self):
        loop = page([row("a", DAY)], token="same")
        with self.assertRaises(ad.AlbumError):
            ad.fetch_times(FakeFetcher(loop, [page([row("b", 2 * DAY)], token="same"), loop]), LINK)

    def test_a_link_that_does_not_open_an_album_is_an_error(self):
        with self.assertRaises(ad.AlbumError):
            ad.fetch_times(FakeFetcher(page([row("a", DAY)]), resolved="https://photos.google.com/u/0/"), LINK)

    def test_a_changed_page_format_is_reported_not_guessed_at(self):
        class Broken(FakeFetcher):
            def get(self, url, body=None):
                return b"<html>nothing here</html>", RESOLVED
        with self.assertRaises(ad.AlbumError):
            ad.fetch_times(Broken(page([])), LINK)

    def test_the_access_key_falls_back_to_the_albums_own_when_the_address_has_none(self):
        fetcher = FakeFetcher(page([row("a", DAY)], token="t"), [page([row("b", 2 * DAY)])], resolved="https://photos.google.com/share/AF1Qip")
        ad.fetch_times(fetcher, LINK)
        body = [r for r in fetcher.requests if "batchexecute" in r[0]][0][1]
        self.assertIn("embedded-key", __import__("urllib.parse", fromlist=["unquote"]).unquote(body))

    def test_only_album_reads_are_allowed(self):
        with self.assertRaises(ad.AlbumError):
            ad.album_rpc(FakeFetcher(page([])), GLOBAL, "/share/x", "k", "someWriteCall", [])


class SummaryTests(unittest.TestCase):
    TODAY = dt.date(2026, 11, 22)

    def day(self, date, hour=12, offset=0):
        return ((date - dt.date(1970, 1, 1)).days * DAY + hour * 3_600_000 - offset, offset)

    def album(self):
        times = [self.day(dt.date(2026, 11, 1), 8 + i) for i in range(5)]
        times += [self.day(dt.date(2026, 10, 20))]
        times += [self.day(dt.date(2025, 3, 5), 9 + i) for i in range(3)]
        times += [(0, 0), (0, 0)]
        return times

    def test_counts_days_and_the_per_day_spread(self):
        s = ad.summarize(self.album(), self.TODAY)
        self.assertEqual((s["photos"], s["undated"], s["days"]), (11, 2, 3))
        self.assertEqual((s["median"], s["max"]), (3, 5))
        self.assertEqual(s["first"], dt.date(2025, 3, 5))
        self.assertEqual(s["last"], dt.date(2026, 11, 1))
        self.assertEqual(s["biggest"][0], (dt.date(2026, 11, 1), 5))
        self.assertEqual(s["single_photo_days"], 1)

    def test_recent_windows_count_photos_and_days(self):
        s = ad.summarize(self.album(), self.TODAY)
        self.assertEqual(s["recent"][1], (5, 1))
        self.assertEqual(s["recent"][3], (6, 2))
        self.assertEqual(s["recent"][24], (9, 3))

    def test_the_local_day_uses_the_stored_offset(self):
        # 23:30 UTC with a +2 hour offset is 01:30 the next local day.
        taken = (dt.date(2026, 5, 1) - dt.date(1970, 1, 1)).days * DAY + 23 * 3_600_000 + 1_800_000
        s = ad.summarize([(taken, 7_200_000)], self.TODAY)
        self.assertEqual(s["first"], dt.date(2026, 5, 2))

    def test_big_days_and_their_share_of_photos(self):
        times = [self.day(dt.date(2024, 6, 1), i % 24) for i in range(30)] + [self.day(dt.date(2024, 6, 2 + i)) for i in range(10)]
        s = ad.summarize(times, self.TODAY)
        self.assertEqual(s["big_days"], 1)
        self.assertAlmostEqual(s["big_day_photo_share"], 0.75)

    def test_months_back_handles_short_months_and_year_ends(self):
        self.assertEqual(ad.months_ago(dt.date(2026, 3, 31), 1), dt.date(2026, 2, 28))
        self.assertEqual(ad.months_ago(dt.date(2026, 1, 15), 2), dt.date(2025, 11, 15))
        self.assertEqual(ad.months_ago(dt.date(2026, 11, 22), 24), dt.date(2024, 11, 22))

    def test_moments_are_runs_of_photos_by_absolute_time_not_by_calendar_day(self):
        h = 3_600_000
        base = (dt.date(2026, 5, 1) - dt.date(1970, 1, 1)).days * DAY
        # Morning: three photos 20 minutes apart. Afternoon: two photos. Both on one local day.
        times = [(base + 10 * h, 0), (base + 10 * h + 1_200_000, 0), (base + 10 * h + 2_400_000, 0),
                 (base + 15 * h, 0), (base + 15 * h + 600_000, 0)]
        s = ad.summarize(times, self.TODAY)
        self.assertEqual(s["moments"], 2)
        self.assertEqual(s["days_by_moments"], {1: 0, 2: 1, 3: 0})
        self.assertEqual(s["midnight_moments"], 0)

    def test_a_moment_that_crosses_local_midnight_is_counted_once_and_flagged(self):
        h = 3_600_000
        base = (dt.date(2026, 5, 1) - dt.date(1970, 1, 1)).days * DAY
        times = [(base + 23 * h + 30 * 60_000, 0), (base + 24 * h + 20 * 60_000, 0)]
        s = ad.summarize(times, self.TODAY)
        self.assertEqual((s["moments"], s["midnight_moments"]), (1, 1))

    def test_a_timezone_change_does_not_split_a_moment(self):
        h = 3_600_000
        base = (dt.date(2026, 5, 1) - dt.date(1970, 1, 1)).days * DAY
        # Twenty minutes apart in real time, but the phone moved from UTC-7 to UTC+2 between them.
        times = [(base + 12 * h, -7 * h), (base + 12 * h + 1_200_000, 2 * h)]
        s = ad.summarize(times, self.TODAY)
        self.assertEqual(s["moments"], 1)

    def test_timezones_are_counted_and_mixed_zone_days_found(self):
        h = 3_600_000
        base = (dt.date(2026, 5, 1) - dt.date(1970, 1, 1)).days * DAY
        times = [(base + 20 * h, -7 * h), (base + 30 * h, 2 * h), (base + 34 * h, 2 * h), (base + 60 * h, 0)]
        s = ad.summarize(times, self.TODAY)
        self.assertEqual(s["offset_kinds"], 3)
        self.assertEqual(s["offsets"][0], (2 * h, 2))
        self.assertEqual(s["offset_zero"], 1)
        text = ad.format_summary(s)
        self.assertIn("UTC+2 (50%)", text)
        self.assertIn("offset 0 on 1 photos", text)

    def test_the_summary_text_reports_moments_and_zones(self):
        text = ad.format_summary(ad.summarize(self.album(), self.TODAY))
        self.assertIn("Timezones:", text)
        self.assertIn("Moments (photos within 2 h of each other):", text)

    def test_an_empty_or_undated_album_is_safe(self):
        self.assertEqual(ad.format_summary(ad.summarize([(0, 0)], self.TODAY)), "No dated photos.")
        self.assertEqual(ad.summarize([], self.TODAY)["days"], 0)

    def test_the_summary_text_names_what_the_strategy_needs(self):
        text = ad.format_summary(ad.summarize(self.album(), self.TODAY))
        for needle in ("11 photos on 3 days", "2 undated", "median 3", "most 5", "Last  6 months", "2026=6/2"):
            self.assertIn(needle, text)


class FileAndPrivacyTests(unittest.TestCase):
    def test_the_csv_holds_times_only_and_is_readable_back(self):
        with tempfile.TemporaryDirectory() as directory:
            path = os.path.join(directory, "sub", "dates.csv")
            ad.write_csv(path, [(1000, 0), (2000, 3600)])
            with open(path) as written:
                text = written.read()
            self.assertEqual(text, "takenMs,offsetMs\n1000,0\n2000,3600\n")
            self.assertEqual(ad.read_csv(path), [(1000, 0), (2000, 3600)])
            self.assertEqual(os.stat(path).st_mode & 0o077, 0, "only the owner may read it")

    def test_a_file_that_is_not_an_album_dates_file_is_refused(self):
        with tempfile.TemporaryDirectory() as directory:
            path = os.path.join(directory, "other.csv")
            with open(path, "w") as other:
                other.write("id,url\n1,2\n")
            with self.assertRaises(ad.AlbumError):
                ad.read_csv(path)

    def run_main(self, fetcher, argv=(), env=LINK):
        out, err = io.StringIO(), io.StringIO()
        old = os.environ.get(ad.LINK_ENV)
        if env is None:
            os.environ.pop(ad.LINK_ENV, None)
        else:
            os.environ[ad.LINK_ENV] = env
        try:
            code = ad.main(list(argv), fetcher=fetcher, today=dt.date(2026, 11, 22), stdout=out, stderr=err)
        finally:
            if old is None:
                os.environ.pop(ad.LINK_ENV, None)
            else:
                os.environ[ad.LINK_ENV] = old
        return code, out.getvalue(), err.getvalue()

    def test_a_run_prints_neither_the_link_nor_its_key_and_writes_no_identifiers(self):
        with tempfile.TemporaryDirectory() as directory:
            path = os.path.join(directory, "dates.csv")
            fetcher = FakeFetcher(page([row("photoid-1", 10 * DAY), row("photoid-2", 11 * DAY)]))
            code, out, err = self.run_main(fetcher, ["--out", path])
            self.assertEqual(code, 0)
            with open(path) as written:
                shown = out + err + written.read()
            for secret in ("SecretShortLink123", "SecretAccessKey99", "AF1QipSecretAlbumId", "photoid-1", "lh3.example", "My trip"):
                self.assertNotIn(secret, shown)
            self.assertIn("2 photos on 2 days", out)

    def test_an_error_message_never_carries_the_link_or_the_key(self):
        class Leaky(FakeFetcher):
            def get(self, url, body=None):
                if "batchexecute" in url:
                    raise ad.AlbumError("Could not read %s (key SecretAccessKey99)" % url)
                return super().get(url, body)
        code, out, err = self.run_main(Leaky(page([row("a", DAY)], token="t")), ["--out", os.devnull])
        self.assertEqual(code, 1)
        self.assertIn("[link]", err)
        self.assertNotIn("SecretShortLink123", err)
        self.assertNotIn("SecretAccessKey99", err)

    def test_an_unexpected_failure_shows_no_traceback(self):
        class Crash:
            def get(self, url, body=None):
                raise RuntimeError(LINK)
        code, out, err = self.run_main(Crash(), ["--out", os.devnull])
        self.assertEqual(code, 2)
        self.assertNotIn("SecretShortLink123", err)
        self.assertNotIn("Traceback", err)

    def test_without_a_link_it_says_what_to_set_and_does_not_touch_the_network(self):
        class Never:
            def get(self, url, body=None):
                raise AssertionError("must not fetch")
        code, out, err = self.run_main(Never(), env=None)
        self.assertEqual(code, 1)
        self.assertIn(ad.LINK_ENV, err)

    def test_from_csv_summarizes_without_a_link_or_network(self):
        with tempfile.TemporaryDirectory() as directory:
            path = os.path.join(directory, "dates.csv")
            ad.write_csv(path, [(10 * DAY, 0), (10 * DAY + 1000, 0), (12 * DAY, 0)])
            code, out, err = self.run_main(None, ["--from-csv", path], env=None)
            self.assertEqual(code, 0)
            self.assertIn("3 photos on 2 days", out)


if __name__ == "__main__":
    unittest.main()
