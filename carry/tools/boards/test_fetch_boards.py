"""Tests for fetch_boards.py, on invented threads and comments. Run: python3 -m unittest discover carry/tools/boards"""

import json
import os
import tempfile
import unittest
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
    def test_a_file_is_rewritten_only_when_the_board_changed(self):
        with tempfile.TemporaryDirectory() as d:
            b = fb.board(date(2026, 11, 14), "t1", [{"id": "b", "body": INTEL_2G}])
            self.assertTrue(fb.write_if_changed(Path(d), b))
            self.assertFalse(fb.write_if_changed(Path(d), b))
            saved = json.loads((Path(d) / "boards" / "2026-11-14.json").read_text())
            self.assertEqual(saved["schema"], "carry-board/1")
            self.assertEqual(saved["classes"]["2G"]["comment"], "b")

    def test_a_thread_with_no_intel_yet_writes_nothing(self):
        self.assertIsNone(fb.board(date(2026, 11, 14), "t1", [{"id": "a", "body": "Good luck all"}]))


class WholeRun(unittest.TestCase):
    """main() against replies shaped like Reddit's: a listing, then [post, comments] per thread."""

    def test_recent_days_are_written_and_old_or_unrelated_posts_are_not(self):
        def post(pid, title):
            return {"kind": "t3", "data": {"id": pid, "title": title}}
        listing = {"data": {"children": [
            post("new1", "Daily Workout and General Chat for Saturday, 11/14/26"),
            post("chat", "Anyone tried the new studio?"),
            post("old1", "Daily Workout and General Chat for Saturday, 10/03/26"),
            post("dup1", "Daily Workout and General Chat for Saturday, 11/14/26"),
        ]}}
        comments = [{"kind": "t3", "data": {}}, {"data": {"children": [
            {"kind": "t1", "data": {"id": "c1", "body": INTEL_2G}},
            {"kind": "more", "data": {}},
        ]}}]
        asked = []

        def fake(url, headers, data=None):
            asked.append(url)
            if url == fb.TOKEN_URL:
                return {"access_token": "tok"}
            self.assertEqual(headers["Authorization"], "Bearer tok")
            return listing if "/r/orangetheory/new" in url else comments

        real, fb._request = fb._request, fake
        env = {"REDDIT_CLIENT_ID": "i", "REDDIT_CLIENT_SECRET": "s", "REDDIT_USER_AGENT": "a"}
        old = {k: os.environ.get(k) for k in env}
        os.environ.update(env)
        try:
            with tempfile.TemporaryDirectory() as d:
                self.assertEqual(fb.main([d, "--today", "2026-11-14"]), 0)
                self.assertEqual(sorted(p.name for p in (Path(d) / "boards").iterdir()), ["2026-11-14.json"])
                saved = json.loads((Path(d) / "boards" / "2026-11-14.json").read_text())
                self.assertEqual(saved["thread"], "new1")  # the first in the listing, as the laptop takes it
        finally:
            fb._request = real
            for k, v in old.items():
                if v is None:
                    os.environ.pop(k, None)
                else:
                    os.environ[k] = v
        self.assertFalse(any("old1" in u or "dup1" in u for u in asked), asked)


if __name__ == "__main__":
    unittest.main()
