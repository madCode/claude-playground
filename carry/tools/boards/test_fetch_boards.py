"""Tests for fetch_boards.py, on invented threads and comments. Run: python3 -m unittest discover carry/tools/boards"""

import json
import os
import tempfile
import unittest
import urllib.error
from datetime import date
from pathlib import Path

import fetch_boards as fb

INTEL_2G = "\n".join([
    "**2G intel**",
    "",
    "Tread: 3 x 4 min pushes",
    "8 x goblet squat",
    "8 total x alt reverse lunge",
    "We had a great time this morning and the coach kept saying to keep the pace honest, which I appreciated "
    "because it is so easy to coast on the second push and then regret it.",
])


class ThreadDate(unittest.TestCase):
    def test_both_title_shapes_seen_live(self):
        self.assertEqual(fb.thread_date("Daily Workout and General Chat for Friday, 09/18/26"), date(2026, 9, 18))
        self.assertEqual(fb.thread_date("Daily Workout and General Chat thread for Saturday, 9/19/2026"),
                         date(2026, 9, 19))

    def test_other_posts_and_bad_dates_are_not_days(self):
        self.assertIsNone(fb.thread_date("Weekly Wins thread for Saturday, 9/19/2026"))
        self.assertIsNone(fb.thread_date("Daily Workout and General Chat for Friday, 13/40/26"))
        self.assertIsNone(fb.thread_date("Daily Workout and General Chat for Friday"))


class Classes(unittest.TestCase):
    def test_each_class_takes_its_first_comment_in_top_order(self):
        comments = [
            {"id": "a", "body": "Anyone else sore today?"},
            {"id": "b", "body": INTEL_2G},
            {"id": "c", "body": "**2G intel** a second copy"},
            {"id": "d", "body": "One mile benchmark 3G Intel\n8 x chest press"},
        ]
        got = fb.classes(comments)
        self.assertEqual(sorted(got), ["2G", "3G"])
        self.assertEqual(got["2G"]["comment"], "b")  # not the later copy
        self.assertEqual(got["3G"]["lines"], ["One mile benchmark 3G Intel", "8 x chest press"])

    def test_a_tag_deep_in_a_comment_is_chatter(self):
        body = "x" * 250 + " my 2G intel was wrong"
        self.assertEqual(fb.classes([{"id": "a", "body": body}]), {})

    def test_long_prose_stays_on_reddit_and_workout_lines_come_through(self):
        lines = fb.classes([{"id": "b", "body": INTEL_2G}])["2G"]["lines"]
        self.assertIn("8 total x alt reverse lunge", lines)
        self.assertIn("Tread: 3 x 4 min pushes", lines)
        self.assertFalse(any("coast" in line for line in lines))
        self.assertNotIn("", lines)


class Writing(unittest.TestCase):
    def test_a_board_is_written_as_ascii_json_even_with_a_lone_surrogate(self):
        with tempfile.TemporaryDirectory() as d:
            b = fb.board(date(2026, 11, 14), "t1", [{"id": "b", "body": INTEL_2G + "\n8 x row \ud83d"}])
            fb.write(Path(d), b)
            saved = json.loads((Path(d) / "boards" / "2026-11-14.json").read_text())
            self.assertEqual(saved["schema"], "carry-board/1")
            self.assertEqual(saved["classes"]["2G"]["comment"], "b")

    def test_a_thread_with_no_intel_yet_writes_nothing(self):
        self.assertIsNone(fb.board(date(2026, 11, 14), "t1", [{"id": "a", "body": "Good luck all"}]))


class Untrusted(unittest.TestCase):
    def test_a_copy_of_the_thread_posted_later_does_not_replace_the_real_one(self):
        title = "Daily Workout and General Chat for Saturday, 11/14/26"
        listing = [{"data": {"id": "fake", "title": title, "author": "someone"}},
                   {"data": {"id": "real", "title": title, "author": "AutoModerator"}}]
        self.assertEqual(fb.pick_threads(listing, {date(2026, 11, 14)}), {date(2026, 11, 14): "real"})
        pinned = [{"data": {"id": "fake", "title": title}}, {"data": {"id": "pin", "title": title, "stickied": True}}]
        self.assertEqual(fb.pick_threads(pinned, {date(2026, 11, 14)}), {date(2026, 11, 14): "pin"})

    def test_other_line_breaks_do_not_let_prose_through_in_pieces(self):
        prose = "\u2028".join(["word " * 20] * 3)
        self.assertEqual(fb.board_lines(prose), [])
        self.assertEqual(fb.board_lines("8 x row\r\n8 x squat"), ["8 x row", "8 x squat"])

    def test_only_ascii_digits_make_a_date_or_a_class(self):
        self.assertIsNone(fb.thread_date("Daily Workout and General Chat for Saturday, \u0661\u0661/\u0661\u0664/26"))
        self.assertIsNone(fb.intel_tag("\u0663G intel"))


def post(pid, title, **extra):
    return {"kind": "t3", "data": {"id": pid, "title": title, **extra}}


LISTING = {"data": {"children": [
    post("new1", "Daily Workout and General Chat for Saturday, 11/14/26"),
    post("chat", "Anyone tried the new studio?"),
    post("old1", "Daily Workout and General Chat for Saturday, 10/03/26"),
    post("fri1", "Daily Workout and General Chat for Friday, 11/13/26"),
]}}
COMMENTS = [{"kind": "t3", "data": {}}, {"data": {"children": [
    {"kind": "t1", "data": {"id": "c1", "body": INTEL_2G}},
    {"kind": "more", "data": {}},
]}}]


class WholeRun(unittest.TestCase):
    """main() against replies shaped like Reddit's: a listing, then [post, comments] per thread."""

    def run_main(self, fake, prev_files=()):
        real, fb._request = fb._request, fake
        env = {"REDDIT_CLIENT_ID": "i", "REDDIT_CLIENT_SECRET": "s", "REDDIT_USER_AGENT": "a"}
        old = {k: os.environ.get(k) for k in env}
        os.environ.update(env)
        try:
            with tempfile.TemporaryDirectory() as prev, tempfile.TemporaryDirectory() as out:
                for name in prev_files:
                    (Path(prev) / "boards").mkdir(exist_ok=True)
                    (Path(prev) / "boards" / name).write_text('{"kept": true}\n')
                code = fb.main([prev, out, "--today", "2026-11-14"])
                files = {p.name: p.read_text() for p in (Path(out) / "boards").glob("*.json")}
                return code, files
        finally:
            fb._request = real
            for k, v in old.items():
                if v is None:
                    os.environ.pop(k, None)
                else:
                    os.environ[k] = v

    @staticmethod
    def reddit(fail=None):
        asked = []

        def fake(url, headers, data=None):
            asked.append(url)
            if fail:
                fail(url)
            if url == fb.TOKEN_URL:
                return {"access_token": "tok"}
            assert headers["Authorization"] == "Bearer tok"
            return LISTING if "/r/orangetheory/new" in url else COMMENTS
        return fake, asked

    def test_the_window_is_written_and_older_days_and_other_posts_are_not(self):
        fake, asked = self.reddit()
        code, files = self.run_main(fake, prev_files=["2026-10-03.json"])
        self.assertEqual(code, 0)
        # The old day leaves the branch with the window: nothing is kept for good.
        self.assertEqual(sorted(files), ["2026-11-13.json", "2026-11-14.json"])
        self.assertEqual(json.loads(files["2026-11-14.json"])["thread"], "new1")
        self.assertFalse(any("old1" in u for u in asked), asked)

    def test_reddit_down_changes_nothing_and_does_not_fail_the_job(self):
        def down(url):
            raise urllib.error.HTTPError(url, 503, "busy", None, None)
        code, _ = self.run_main(self.reddit(down)[0])
        self.assertEqual(code, fb.TEMPFAIL)

    def test_a_refused_key_needs_a_person(self):
        def refused(url):
            if url == fb.TOKEN_URL:
                raise urllib.error.HTTPError(url, 401, "no", None, None)
        code, _ = self.run_main(self.reddit(refused)[0])
        self.assertEqual(code, 2)

    def test_a_thread_that_cannot_be_read_keeps_its_day(self):
        def friday_down(url):
            if "/comments/fri1" in url:
                raise urllib.error.URLError("reset")
        code, files = self.run_main(self.reddit(friday_down)[0], prev_files=["2026-11-13.json"])
        self.assertEqual(code, 0)
        self.assertEqual(json.loads(files["2026-11-13.json"]), {"kept": True})
        self.assertIn("2026-11-14.json", files)


if __name__ == "__main__":
    unittest.main()
