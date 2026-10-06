package com.app.jekyllposter.core.images

import java.io.ByteArrayOutputStream

/** Just enough of the GIF format to drop the blocks that carry metadata rather than pictures. */
object Gif {
    /**
     * The GIF without comment extensions or application extensions other than the animation loop
     * count (NETSCAPE2.0). A file that doesn't parse as expected is refused rather than passed on.
     */
    fun withoutMetadata(gif: ByteArray): ByteArray = try {
        strip(gif)
    } catch (e: IndexOutOfBoundsException) {
        throw IllegalArgumentException("This GIF is damaged", e)
    }

    private fun strip(gif: ByteArray): ByteArray {
        require(gif.size >= 13 && String(gif, 0, 3) == "GIF") { "Not a GIF" }
        val out = ByteArrayOutputStream(gif.size)
        var i = 13
        val flags = gif[10].toInt() and 0xff
        if (flags and 0x80 != 0) i += 3 * (1 shl ((flags and 0x07) + 1))
        out.write(gif, 0, i)
        fun skipSubBlocks(from: Int): Int {
            var j = from
            while (true) {
                require(j < gif.size) { "A GIF cut short" }
                val n = gif[j].toInt() and 0xff
                j += 1 + n
                if (n == 0) return j
            }
        }
        while (i < gif.size) {
            when (gif[i].toInt() and 0xff) {
                0x3B -> { out.write(0x3B); return out.toByteArray() }
                0x21 -> {
                    val label = gif[i + 1].toInt() and 0xff
                    val end = skipSubBlocks(i + 2)
                    val keep = when (label) {
                        0xFE -> false
                        0xFF -> String(gif, i + 3, minOf(11, gif.size - i - 3)) == "NETSCAPE2.0"
                        else -> true
                    }
                    if (keep) out.write(gif, i, end - i)
                    i = end
                }
                0x2C -> {
                    var j = i + 10
                    val f = gif[i + 9].toInt() and 0xff
                    if (f and 0x80 != 0) j += 3 * (1 shl ((f and 0x07) + 1))
                    j += 1 // LZW minimum code size
                    val end = skipSubBlocks(j)
                    out.write(gif, i, end - i)
                    i = end
                }
                else -> throw IllegalArgumentException("Not a GIF this app can read")
            }
        }
        throw IllegalArgumentException("A GIF cut short")
    }
}
