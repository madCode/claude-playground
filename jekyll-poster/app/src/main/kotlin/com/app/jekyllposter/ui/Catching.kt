package com.app.jekyllposter.ui

import kotlinx.coroutines.CancellationException

/**
 * [runCatching] for suspending code: a failure becomes a [Result], but a cancellation goes on up.
 * Caught, it would let a screen that's gone (or a timeout that fired) carry on as if it hadn't.
 */
inline fun <T> catching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}
