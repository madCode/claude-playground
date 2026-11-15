#!/usr/bin/env python3
"""
The day's board, from r/orangetheory, for Carry to read without a Reddit key.

Run by `.github/workflows/carry-boards.yml` on a schedule. It finds the recent
"Daily Workout and General Chat" threads, takes each class's intel comment
("2G intel", "3G intel"), and writes one file per day into OUT_DIR, for the
days from DAYS_BACK before today to DAYS_AHEAD after:

    boards/2026-11-14.json
    {"schema": "carry-board/1", "date": "2026-11-14", "thread": "1abcde",
     "classes": {"2G": {"comment": "xyz", "lines": ["...", ...]}, ...}}

Only lines of at most LINE_MAX characters are kept. Workout lines are short
and commentary is long: over gym-coach's 277-board corpus this cut changes
what the coach reads on 2 boards, and on both it drops a prose sentence the
matcher had misread as an exercise ("Good morning" as the movement). Other
people's writing stays on Reddit, and so does what they delete: the branch
holds only the current window, as one commit replaced on each change, so a
removed comment leaves no copy here.

The rules for which thread and which comment are gym-coach's
(`reddit_fetch.py`), copied, not imported: that repository is private. A
change there is a change here.

Credentials come from the environment (REDDIT_CLIENT_ID, REDDIT_CLIENT_SECRET,
REDDIT_USER_AGENT), set as the repository's Actions secrets. Standard
library only, so the job installs nothing.

    fetch_boards.py PREV_DIR OUT_DIR [--today YYYY-MM-DD]

PREV_DIR is the branch as it stands. A day whose thread can't be read this
run keeps its file from there. Exit 0 when OUT_DIR is the new branch, 75 when
Reddit couldn't be reached and nothing should change, 2 for a missing key or
one Reddit refuses, which needs a person.
"""

from __future__ import annotations

import base64
import json
import shutil
import urllib.error
import os
import re
import sys
import urllib.parse
import urllib.request
from datetime import date, datetime, timedelta, timezone
from pathlib import Path

SCHEMA = "carry-board/1"
TOKEN_URL = "https://www.reddit.com/api/v1/access_token"
API_BASE = "https://oauth.reddit.com"
SUBREDDIT = "orangetheory"
LINE_MAX = 120
# Threads go up the evening before or the morning of; a few days back keeps
# a late edit to yesterday's intel, and tomorrow's lets Carry plan ahead.
DAYS_BACK, DAYS_AHEAD = 3, 1
# Comments read per thread, top first, as the laptop reads them.
COMMENT_LIMIT = 15

# gym-coach reddit_fetch.DAILY_THREAD_RE, THREAD_DATE_RE, INTEL_TAG_RE.
# ASCII here, unlike there: "١١/١٤/٢٦" or "٣G" should not pass for a date or a class.
DAILY_THREAD_RE = re.compile(r"(?i)\bdaily workout and general chat\b.*?\bfor\b", re.ASCII)
THREAD_DATE_RE = re.compile(r"\b(\d{1,2})/(\d{1,2})/(\d{2}|\d{4})\s*$", re.ASCII)
INTEL_TAG_RE = re.compile(r"(\dG)\s+intel", re.IGNORECASE | re.ASCII)
# Who posts the real daily thread. Anyone can post a copy titled the same, so
# a thread from them, or one the moderators pinned, wins over the newest.
TRUSTED_AUTHORS = {"AutoModerator"}
TEMPFAIL = 75


def thread_date(title: str) -> date | None:
    """`reddit_fetch.thread_date_from_title`: "...for Saturday, 9/19/2026" -> 2026-09-19."""
    if not DAILY_THREAD_RE.search(title):
        return None
    m = THREAD_DATE_RE.search(title.strip())
    if not m:
        return None
    month, day, year = (int(g) for g in m.groups())
    if year < 100:
        year += 2000
    try:
        return date(year, month, day)
    except ValueError:
        return None


def intel_tag(body: str) -> str | None:
    """`reddit_fetch.intel_tag`: the class a comment claims, from its first 200 characters."""
    m = INTEL_TAG_RE.search((body or "")[:200])
    return m.group(1).upper() if m else None


def board_lines(body: str) -> list[str]:
    """The comment's lines short enough to be a workout line, as written."""
    # Newlines only: splitlines() also breaks on U+2028 and the like, which
    # would let a long paragraph through in pieces.
    lines = (line.rstrip("\r") for line in (body or "").split("\n"))
    return [line for line in lines if line.strip() and len(line.strip()) <= LINE_MAX]


def classes(comments: list[dict]) -> dict[str, dict]:
    """Each class's first tagged comment in top order, as `reddit_fetch.get_wod_text` takes it."""
    out: dict[str, dict] = {}
    for c in comments:
        tag = intel_tag(c.get("body") or "")
        if tag and tag not in out:
            out[tag] = {"comment": c.get("id"), "lines": board_lines(c.get("body") or "")}
    return out


def board(day: date, thread_id: str, comments: list[dict]) -> dict | None:
    found = classes(comments)
    if not found:
        return None  # a thread with no intel yet says nothing; an empty file would read as "no workout"
    return {"schema": SCHEMA, "date": day.isoformat(), "thread": thread_id, "classes": dict(sorted(found.items()))}


def pick_threads(listing: list[dict], wanted: set[date]) -> dict[date, str]:
    """Each wanted day's thread: a pinned one or one from a trusted author first,
    then the first in the listing, as the laptop takes it."""
    candidates: dict[date, list[dict]] = {}
    for child in listing:
        data = child.get("data") or {}
        d = thread_date(data.get("title") or "")
        if d in wanted:
            candidates.setdefault(d, []).append(data)
    return {d: next((p for p in posts if p.get("stickied") or p.get("author") in TRUSTED_AUTHORS), posts[0])["id"]
            for d, posts in candidates.items()}


def write(out_dir: Path, b: dict) -> None:
    path = out_dir / "boards" / f"{b['date']}.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    # ASCII: a lone surrogate from the API would not encode as UTF-8.
    path.write_text(json.dumps(b, indent=1, ensure_ascii=True, sort_keys=True) + "\n", encoding="ascii")


# ------------------------------------------------------------------ network


def _request(url: str, headers: dict, data: bytes | None = None) -> object:
    req = urllib.request.Request(url, data=data, headers=headers)
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.loads(r.read().decode("utf-8"))


def token(client_id: str, secret: str, agent: str) -> str:
    basic = base64.b64encode(f"{client_id}:{secret}".encode()).decode()
    body = urllib.parse.urlencode({"grant_type": "client_credentials"}).encode()
    reply = _request(TOKEN_URL, {"Authorization": f"Basic {basic}", "User-Agent": agent}, body)
    return reply["access_token"]


def _transient(e: Exception) -> bool:
    """Reddit being down or busy, not a key it refuses."""
    if isinstance(e, urllib.error.HTTPError):
        return e.code == 429 or e.code >= 500
    return isinstance(e, (urllib.error.URLError, TimeoutError, ValueError, KeyError, IndexError, TypeError))


def main(argv: list[str]) -> int:
    paths = [a for a in argv if not a.startswith("-")]
    if "--today" in argv:
        paths.remove(argv[argv.index("--today") + 1])
    if len(paths) != 2:
        print(__doc__, file=sys.stderr)
        return 2
    prev_dir, out_dir = Path(paths[0]), Path(paths[1])
    today = (date.fromisoformat(argv[argv.index("--today") + 1]) if "--today" in argv
             else datetime.now(timezone.utc).date())
    keys = [os.environ.get(k) for k in ("REDDIT_CLIENT_ID", "REDDIT_CLIENT_SECRET", "REDDIT_USER_AGENT")]
    if not all(keys):
        print("REDDIT_CLIENT_ID, REDDIT_CLIENT_SECRET and REDDIT_USER_AGENT must all be set.", file=sys.stderr)
        return 2
    client_id, secret, agent = keys
    wanted = {today + timedelta(days=d) for d in range(-DAYS_BACK, DAYS_AHEAD + 1)}
    try:
        auth = {"Authorization": f"Bearer {token(client_id, secret, agent)}", "User-Agent": agent}
        listing = _request(f"{API_BASE}/r/{SUBREDDIT}/new?limit=100", auth)["data"]["children"]
    except Exception as e:  # noqa: BLE001 -- sorted below into "try later" and "needs a person"
        if not _transient(e):
            print(f"Reddit refused: {e}", file=sys.stderr)
            return 2
        print(f"Reddit unreachable, nothing changed: {e}")
        return TEMPFAIL
    threads = pick_threads(listing, wanted)
    for d in sorted(wanted):
        thread_id = threads.get(d)
        if thread_id is None:
            print(f"{d}: no thread")
            continue
        try:
            reply = _request(f"{API_BASE}/comments/{thread_id}?limit={COMMENT_LIMIT}&sort=top&depth=1", auth)
            comments = [c["data"] for c in reply[1]["data"]["children"] if c.get("kind") == "t1"]
        except Exception as e:  # noqa: BLE001
            if not _transient(e):
                raise
            kept = prev_dir / "boards" / f"{d.isoformat()}.json"
            if kept.exists():
                (out_dir / "boards").mkdir(parents=True, exist_ok=True)
                shutil.copyfile(kept, out_dir / "boards" / kept.name)
            print(f"{d}: unreadable this run, {'kept' if kept.exists() else 'nothing to keep'}: {e}")
            continue
        b = board(d, thread_id, comments)
        if b:
            write(out_dir, b)
        print(f"{d}: {', '.join(b['classes']) if b else 'no intel'}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
