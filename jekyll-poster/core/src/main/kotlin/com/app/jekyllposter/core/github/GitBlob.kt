package com.app.jekyllposter.core.github

import java.security.MessageDigest

/** Git's id for a file's content, as GitHub reports it: SHA-1 of `blob <size>\0<bytes>`. */
fun gitBlobSha(text: String): String {
    val bytes = text.toByteArray(Charsets.UTF_8)
    val digest = MessageDigest.getInstance("SHA-1")
    digest.update("blob ${bytes.size}\u0000".toByteArray())
    digest.update(bytes)
    return digest.digest().joinToString("") { "%02x".format(it) }
}
