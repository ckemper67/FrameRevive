"""Tests for album_census.py and album_compare.py. They use no network."""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import album_census as census  # noqa: E402
import album_compare as compare  # noqa: E402
import album_metadata as am  # noqa: E402


def photo(pid, taken, key="", w=4000, h=3000):
    return am.Photo(taken, 0, pid, w, h, key, False, 0)


class CensusTests(unittest.TestCase):
    def test_shapes_counts_and_ranges_per_field_without_values(self):
        rows = [["id1", ["u", 4000, 3000], 1000, "key1", 0, None], ["id2", ["u", 480, 360], 2000, "key2", 3600, 7, {"15": 1}]]
        text = "\n".join(census.format_census(census.census(rows)))
        self.assertIn("row[1][1]: int 2 range 480..4000", text)
        self.assertIn("row[3]: str 2", text)
        self.assertIn("row[5]: int 1; null 1 range 7..7", text)
        self.assertNotIn("key1", text)
        self.assertNotIn("id1", text)

    def test_a_field_that_identifies_a_photo_across_albums_is_found(self):
        main = [["a", None, 1, "contentkey-1"], ["b", None, 2, "contentkey-2"]]
        other = [["z", None, 1, "contentkey-2"], ["y", None, 5, "contentkey-9"]]
        self.assertEqual([(3, 1, 2, True)], census.shared_values(main, other, fields=[3]))


class CompareTests(unittest.TestCase):
    def test_twins_by_content_key_and_by_time_are_counted_separately(self):
        main = [photo("m1", 1000, "K1"), photo("m2", 2000, "K2"), photo("m3", 2000, "K3"), photo("m4", 5000, "K4", 1000, 800)]
        other = [photo("f1", 1000, "K1"), photo("f2", 2000, "K3"), photo("f3", 5000, "K4", 1000, 600), photo("f4", 9000, "K9")]
        out = compare.compare(main, other)
        self.assertEqual(3, out["twin by content key"])
        self.assertEqual(1, out["no twin by content key"])
        self.assertEqual(2, out["twin same size"])
        self.assertEqual(1, out["twin different size"])
        self.assertEqual(1, out["burst: the key picks one of several same-time photos"])
        self.assertEqual((2, 1, 1), (out["time matches one main photo"], out["time matches several"], out["time matches none"]))

    def test_byte_sampling_counts_identical_and_failed_pairs_without_the_network(self):
        main = [photo("m1", 1, "K1"), photo("m2", 2, "K2"), photo("m3", 3, "K3")]
        other = [photo("f1", 1, "K1"), photo("f2", 2, "K2"), photo("f3", 3, "K3")]
        urls = {"m1": "a", "m2": "b", "m3": "c", "f1": "a", "f2": "x", "f3": "boom"}

        def fake(url):
            if url == "boom":
                raise OSError("down")
            return url
        self.assertEqual((3, 1, 1), compare.sample_bytes(main, other, urls, urls, 10, md5=fake))
        self.assertEqual((1, 1, 0), compare.sample_bytes(main, other, urls, urls, 1, md5=fake))


if __name__ == "__main__":
    unittest.main()
