package com.app.jekyllposter.core.github

import java.io.IOException

/**
 * Thrown in place of connecting: the writer asked for a VPN and there isn't one. An
 * [IOException], so every caller treats it as not reaching GitHub: nothing was sent, and a
 * queued post waits and tries again.
 */
class NoVpnException : IOException("No VPN, so nothing was sent")
