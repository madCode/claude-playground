package com.app.jekyllposter.core.io

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * The stream's bytes, but no more than [limit] plus one: a result longer than [limit] says the
 * stream was too big, without reading (or holding) all of it.
 */
fun InputStream.readAtMost(limit: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val chunk = ByteArray(64 * 1024)
    while (out.size() <= limit) {
        val n = read(chunk, 0, minOf(chunk.size, limit + 1 - out.size()))
        if (n < 0) break
        out.write(chunk, 0, n)
    }
    return out.toByteArray()
}
