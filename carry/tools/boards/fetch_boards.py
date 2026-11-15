#!/usr/bin/env python3
"""
The day's board, from r/orangetheory, for Carry to read without a Reddit key.

Run by `.github/workflows/carry-boards.yml` on a schedule. It finds the recent
"Daily Workout and General Chat" threads, takes each class's intel comment
("2G intel", "3G intel"), and writes one file per day into OUT_DIR:

    boards/2026-11-14.json
    {"schema": "carry-board/1", "date": "2026-11-14", "thread": "1abcde",
     "classes": {"2G": {"comment": "xyz", "lines": ["...", ...]}, ...}}

Only lines of at most LINE_MAX characters are kept. Workout lines are short
and commentary is long: over gym-coach's 277-board corpus this cut changes
what the coach reads on 2 boards, and on both it drops a prose sentence the
matcher had misread as an exercise ("Good morning" as the movement). Other
people's writing stays on Reddit.

The rules for which thread and which comment are gym-coach's
(`reddit_fetch.py`), copied, not imported: that repository is private. A
change there is a change here.

Credentials come from the environment (REDDIT_CLIENT_ID, REDDIT_CLIENT_SECRET,
REDDIT_USER_AGENT), set as the repository's Actions secrets. Standard
library only, so the job installs nothing.

    fetch_boards.py OUT_DIR [--today YYYY-MM-DD]
"""

from __future__ import annotations

import base64
import json
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
DAILY_THREAD_RE = re.compile(r"(?i)\bdaily workout and general chat\b.*?\bfor\b")
THREAD_DATE_RE = re.compile(r"\b(\d{1,2})/(\d{1,2})/(\d{2}|\d{4})\s*$")
INTEL_TAG_RE = re.compile(r"(\dG)\s+intel", re.IGNORECASE)


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
    return [line for line in (body or "").splitlines() if line.strip() and len(line.strip()) <= LINE_MAX]


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


def write_if_changed(out_dir: Path, b: dict) -> bool:
    path = out_dir / "boards" / f"{b['date']}.json"
    text = json.dumps(b, indent=1, ensure_ascii=False, sort_keys=True) + "\n"
    if path.exists() and path.read_text(encoding="utf-8") == text:
        return False
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")
    return True


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


def main(argv: list[str]) -> int:
    if not argv or argv[0].startswith("-"):
        print(__doc__, file=sys.stderr)
        return 2
    out_dir = Path(argv[0])
    today = (date.fromisoformat(argv[argv.index("--today") + 1]) if "--today" in argv
             else datetime.now(timezone.utc).date())
    keys = [os.environ.get(k) for k in ("REDDIT_CLIENT_ID", "REDDIT_CLIENT_SECRET", "REDDIT_USER_AGENT")]
    if not all(keys):
        print("REDDIT_CLIENT_ID, REDDIT_CLIENT_SECRET and REDDIT_USER_AGENT must all be set.", file=sys.stderr)
        return 2
    client_id, secret, agent = keys
    auth = {"Authorization": f"Bearer {token(client_id, secret, agent)}", "User-Agent": agent}
    listing = _request(f"{API_BASE}/r/{SUBREDDIT}/new?limit=100", auth)["data"]["children"]
    wanted = {today + timedelta(days=d) for d in range(-DAYS_BACK, DAYS_AHEAD + 1)}
    threads = {}
    for child in listing:
        d = thread_date(child["data"].get("title") or "")
        if d in wanted and d not in threads:
            threads[d] = child["data"]["id"]
    changed = 0
    for d, thread_id in sorted(threads.items()):
        reply = _request(f"{API_BASE}/comments/{thread_id}?limit={COMMENT_LIMIT}&sort=top&depth=1", auth)
        comments = [c["data"] for c in reply[1]["data"]["children"] if c.get("kind") == "t1"]
        b = board(d, thread_id, comments)
        if b and write_if_changed(out_dir, b):
            changed += 1
        print(f"{d}: {', '.join(b['classes']) if b else 'no intel yet'}")
    print(f"{len(threads)} threads, {changed} files changed")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
