package com.app.jekyllposter.core.github

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubUser(val login: String, val name: String? = null, val id: Long = 0) {
    /**
     * Who a commit is by when the writer keeps their email out of it: the no-reply address GitHub
     * gives every account, `<id>+<login>@users.noreply.github.com`, which still links the commit
     * to the account. Null without the account's id, rather than an address that links to no one.
     */
    val noReplyAuthor: CommitAuthor?
        get() = if (id <= 0) null else CommitAuthor(name?.takeIf { it.isNotBlank() } ?: login, "$id+$login@users.noreply.github.com")
}

/** A commit's author; without one, GitHub uses the token's account and its email settings. */
data class CommitAuthor(val name: String, val email: String)

@Serializable
data class GitHubRepo(
    @SerialName("full_name") val fullName: String,
    val name: String,
    val owner: Owner,
    @SerialName("default_branch") val defaultBranch: String,
    val private: Boolean = false,
    @SerialName("has_pages") val hasPages: Boolean = false,
    val permissions: Permissions? = null,
    @SerialName("pushed_at") val pushedAt: String? = null,
) {
    @Serializable
    data class Owner(val login: String)

    @Serializable
    data class Permissions(val push: Boolean = false)

    val canPush: Boolean get() = permissions?.push ?: false
}

@Serializable
data class TreeEntry(val path: String, val type: String, val sha: String, val mode: String = "100644", val size: Long? = null)

@Serializable
internal data class Tree(val sha: String, val tree: List<TreeEntry>, val truncated: Boolean = false)

@Serializable
internal data class Ref(@SerialName("object") val obj: Obj) {
    @Serializable
    data class Obj(val sha: String)
}

@Serializable
internal data class Commit(val sha: String, val tree: TreeRef) {
    @Serializable
    data class TreeRef(val sha: String)
}

@Serializable
internal data class Sha(val sha: String)

@Serializable
data class PagesSite(
    @SerialName("html_url") val htmlUrl: String? = null,
    /** "legacy" (GitHub builds the branch) or "workflow" (an Actions workflow deploys it). */
    @SerialName("build_type") val buildType: String? = null,
    val status: String? = null,
)

@Serializable
internal data class WorkflowRuns(@SerialName("workflow_runs") val runs: List<WorkflowRun>)

@Serializable
data class WorkflowRun(
    val name: String? = null,
    val status: String,
    val conclusion: String? = null,
    @SerialName("head_sha") val headSha: String,
    @SerialName("html_url") val htmlUrl: String? = null,
    /** The workflow file, or `dynamic/pages/pages-build-deployment` for GitHub's own Pages build. */
    val path: String? = null,
)

/** A file to write in a commit. [content] is raw bytes, so images and text go the same way. */
class FileChange(val path: String, val content: ByteArray?) {
    companion object {
        fun text(path: String, text: String) = FileChange(path, text.toByteArray(Charsets.UTF_8))
        fun delete(path: String) = FileChange(path, null)
    }
}

/**
 * A commit as GitHub shows it: [login] is the account it's attributed to (null when GitHub links
 * the email to no account), and whether GitHub marks it Verified.
 */
@Serializable
data class CommitView(val author: Account? = null, val commit: Details) {
    @Serializable
    data class Account(val login: String)

    @Serializable
    data class Details(val author: Person, val verification: Verification? = null)

    @Serializable
    data class Person(val name: String, val email: String)

    @Serializable
    data class Verification(val verified: Boolean = false, val reason: String? = null)

    val login: String? get() = author?.login
}
