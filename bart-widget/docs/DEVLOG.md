# Devlog

What each change shipped and why. Newest first.

**Latest debug build:** [bart-widget-debug.apk](https://github.com/madCode/claude-playground/releases/download/bart-widget-debug/bart-widget-debug.apk)
(published once this is on main).

## Mon 5 Oct

### Tests, end to end, and what they caught
- **Shipped:** a fake BART server answering with boards recorded from the real API, and tests
  through the real app, the widget, the refresh and the background worker: 46 tests, 98% line
  coverage, with CI failing below 85%. A live check against api.bart.gov, by hand or weekly,
  also checks the bundled stations are still BART's 50.
- **The regression that matters:** `FlowTest` opens the app from a widget tap on Dublin while
  Montgomery is starred first, and checks only Dublin is on screen.
- **Caught by screenshots:** times wrapped inside a train ("8:13 (53 / min)"); they now wrap
  only between trains. The widget's list now fills the space under its header.
- **In the way:** Robolectric can't bind Glance's RemoteViewsService, so the widget's rows are
  checked through Glance's test host rather than in the rendered PNG. No emulator in the cloud
  session (no KVM), so nothing has run on a phone yet.

### The widget
- **Why:** the official BART app shows the station it thinks you're nearest to, even with
  another starred, and that has meant leaving at the wrong time. Transit's Android widgets are
  "Nearby" ones too; no app found showed a fixed station on a widget.
- **Shipped:** star stations, see their departures on a widget in clock times, tap one to open
  exactly that one, Refresh, and Parking via the official app.
