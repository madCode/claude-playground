# What GitHub knows about the writer

An audit of what the app sends to GitHub, and what GitHub keeps, read from real runs rather than
the code. Run of 20 Oct 2026: the device live check (`LiveOnTheDeviceTest`) on an emulator in
GitHub Actions, against the tester's blogs ([TESTER_ACCOUNT.md](../TESTER_ACCOUNT.md)). Its
artifact holds the request records and `privacy-report.json`; the next nightly run makes new ones.

Two runs:

- **Two blogs, two sign-ins**, switches off: blog A signed in to and published from the screens;
  the organisation's blog added with its own token and sent in the background. 33 requests.
- **At most privacy**: every switch "Set up for writing anonymously" turns on, except Only
  connect through a VPN (the CI emulator has no VPN). A link with tracking codes shared in and
  published. 13 requests.

## What each request carries

The same handful of headers, every time:

    Accept: application/vnd.github+json
    Accept-Encoding: gzip
    Authorization: (the sign-in's token)
    Connection: Keep-Alive
    Host: api.github.com
    User-Agent: okhttp/5.5.0
    X-GitHub-Api-Version: 2022-11-28

- Nothing names the phone, its model, its language, the app or the writer. `okhttp/5.5.0` is
  shared by a great many Android apps.
- Each request carries one sign-in's token and goes to that sign-in's own repositories. In the
  two-blog run, the tester's token never touched the organisation's blog, nor the other way round.
- The commit's own body says what the commit will: the post's text, its path, the message, and
  the author when the no-reply switch is on. Nothing more.

## What the switches kept out

Checked in the at-most-privacy run, from what GitHub returned afterwards:

| Switch | What GitHub has |
|---|---|
| Plain commit messages | "Update blog" (switches off: "Add post: *title*") |
| No-reply email | `<id>+<login>@users.noreply.github.com`, author and committer |
| Dates by the day only | `date: 2026-10-20` (switches off: the minute and the site's offset) |
| Tracking codes | The link kept `?id=7`; `utm_source`, `utm_medium` and `fbclid` were gone |
| A random time | Queued for 97 minutes ahead (the window is 3 hours); the test then pressed Send now |

## What GitHub still knows, at most privacy

- **The account.** Every request is made with its token, and every commit is attributed to its
  login. This is why [TESTER_ACCOUNT.md](../TESTER_ACCOUNT.md)'s advice, and the app's own Blog &
  privacy page, start with an account of its own.
- **The address requests come from.** Without the VPN switch, each request comes from the
  phone's own connection. With it, from the VPN's. The CI run can't show this; the laptop's
  Proton check does ([DEVICE-CHECKS.md](../DEVICE-CHECKS.md)).
- **The moment the post went out, to the second.** The commit is dated when it was made:
  `Tue, 20 Oct 2026 13:36:50 -0700`, author and committer alike. The random time moves that
  moment away from when the writer pressed Publish, but the commit still carries it, and it's
  public in the history. *Dates by the day only* covers the post's front matter, not the commit.
- **A time zone offset on that commit, which isn't the writer's.** The app sends no date with the
  commit (the request record shows none), so GitHub filled in both the time and the `-0700`. It
  says nothing about where the writer is, but it is the same for every commit the app makes.
- **When the app is open.** Opening it reads the blog (its file list and the posts' text: three
  times in the first ten seconds of the two-blog run). GitHub can see when the writer uses the
  app even on days nothing is published.
- **That two accounts belong together, if they're on one phone.** In the two-blog run the second
  account's requests came one second after the first's, from the same address. A VPN on both
  hides the phone's address but not that both came from the same one at the same time. Blog &
  privacy already says this under *Beyond the app*.
- **That it's this app.** The order of the calls (a file list, a GraphQL batch of blobs, a `404`
  check of the new post's path, then blob, tree, commit and ref) is this app's own. It names no
  one, but GitHub could tell the requests apart from the website's or `git`'s.
- **Unsigned commits.** GitHub marks them unverified, which also says they were made through the
  API. The live check prints it every night.

## What it means for the app

- Nothing found reaches GitHub that the design doesn't intend, and every switch did what it says.
- One switch could do more: with *dates by the day only* on, the commit could carry the post's day
  (midnight UTC) as its date too, so the public history shows neither the time it went out nor
  any time zone. GitHub would still know the real moment from its own logs. A decision for
  madCode: BACKLOG.md, *Your call*.
- Reading the blog on opening is what keeps the posts and categories current; reading less often
  would be stale for the writer and still say when they use the app. Left as is.
