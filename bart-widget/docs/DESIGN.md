# Design

Who it's for: a BART rider who checks the same one or two stations every day, and has been sent
out the door at the wrong time by an app that showed the station it thought they were nearest.

## Never guess a station

- No location permission, no "nearby". The stations are the ones you star, bundled with the app
  so picking one needs no network.
- Tapping a station on the widget opens that station's page, and only it. A second tap on
  another station while the app is open switches to that one.
- The widget lists starred stations in the order they were starred.

## Times

- The widget shows clock times ("7:33"), up to three per destination, five destinations per
  station. A widget redraws only now and then, and "13 min" goes wrong as it ages while "7:33"
  doesn't. Trains that have left are dropped whenever it redraws.
- The header says when the times were fetched ("Updated 7:20", the oldest of the stations).
- The app shows clock time and minutes to go, counting down every 15 seconds, with platform,
  cars and delays.
- Destinations are ordered by their next train.
- "Leaving" is now; cancelled trains are left out.

## Refreshing

- Refresh on the widget, opening the app, starring a station: each fetches every starred station.
- In the background every 15 minutes (Android's floor), from when the app is opened or a widget
  placed until the last widget is removed.
- A station that fails keeps its last times, marked "Couldn't refresh: times from 7:20"; one
  that has never loaded says "Couldn't refresh". Unstarring drops a station's times.

## Parking

- The official BART app (com.app.bart) has no public link to its parking screen, so Parking
  opens the app, one tap from Parking there. Without it, bart.gov's parking page.

## Data

- BART's real-time API (api.bart.gov `etd.aspx`) with BART's published public key. No account,
  no analytics; the only server the app talks to is BART's.
