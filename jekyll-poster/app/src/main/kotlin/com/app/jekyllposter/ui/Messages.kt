package com.app.jekyllposter.ui

import com.app.jekyllposter.core.github.GitHubException

/** A GitHub failure in words the writer can act on. */
fun Throwable.forWriter(): String = when ((this as? GitHubException)?.kind) {
    GitHubException.Kind.Unauthorized -> "GitHub didn't accept that token. Check it was copied whole and hasn't expired."
    GitHubException.Kind.NoAccess -> "The token can't see that. Make sure it covers the blog's repository."
    GitHubException.Kind.RateLimited -> "GitHub asked us to slow down. Try again in a few minutes."
    GitHubException.Kind.Network -> "Couldn't reach GitHub. Check your connection."
    GitHubException.Kind.Conflict -> "Something changed on GitHub at the same moment. Try again."
    GitHubException.Kind.Other -> "GitHub said: $message"
    null -> "Something went wrong: ${message ?: javaClass.simpleName}"
}
