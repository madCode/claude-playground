package com.app.jekyllposter.core.github

/** A GitHub call that failed, sorted by what the writer can do about it. */
class GitHubException(val kind: Kind, message: String, val status: Int = 0, cause: Throwable? = null) :
    Exception(message, cause) {
    enum class Kind {
        /** The token is wrong, expired or revoked: sign in again. */
        Unauthorized,

        /** The token can't do this: fine-grained tokens answer 404 for repos they don't cover. */
        NoAccess,

        RateLimited,

        /** Someone else pushed in between; the caller retries on the new head. */
        Conflict,

        /** No connection, or GitHub didn't answer: worth retrying later. */
        Network,

        Other,
    }

    val retryable: Boolean get() = kind == Kind.Network || kind == Kind.RateLimited || (kind == Kind.Other && status >= 500)
}
