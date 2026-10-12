package com.app.carry

/** A Ready state for tests, with raw text that matches the session. */
internal fun readyFor(session: Session = SampleSession.today): CarryState.Ready =
    CarryState.Ready(session, Session.json.encodeToString(session), "doc")
