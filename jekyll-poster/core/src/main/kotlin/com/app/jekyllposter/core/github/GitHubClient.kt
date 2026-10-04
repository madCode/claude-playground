package com.app.jekyllposter.core.github

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Base64

/**
 * The few GitHub REST and GraphQL calls the app makes, with a token from a fine-grained personal
 * access token or the OAuth device flow. Blocking calls run on [Dispatchers.IO].
 */
class GitHubClient(
    private val http: OkHttpClient,
    private val token: String,
    private val apiBase: HttpUrl = "https://api.github.com/".toHttpUrl(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonType = "application/json".toMediaType()

    suspend fun user(): GitHubUser = get("user")

    /** Repositories the token can write to, most recently pushed first. */
    suspend fun writableRepos(): List<GitHubRepo> {
        val all = mutableListOf<GitHubRepo>()
        for (page in 1..10) {
            val batch: List<GitHubRepo> = get("user/repos?per_page=100&sort=pushed&page=$page")
            all += batch
            if (batch.size < 100) break
        }
        return all.filter { it.canPush }
    }

    suspend fun repo(owner: String, name: String): GitHubRepo = get("repos/$owner/$name")

    /** Every file on [branch], with each blob's sha so unchanged files needn't be fetched again. */
    suspend fun files(owner: String, name: String, branch: String): List<TreeEntry> {
        val tree: Tree = get("repos/$owner/$name/git/trees/${ref(branch)}?recursive=1")
        // A partial list would make posts look deleted and existing files look free to overwrite.
        if (tree.truncated) throw GitHubException(GitHubException.Kind.Other, "This repository is too big for GitHub to list in one go.")
        return tree.tree.filter { it.type == "blob" }
    }

    /** A file as it is on a branch: its text and blob sha. */
    data class FileAt(val text: String, val sha: String)

    /** One file's text and sha on [ref] (a branch or a commit), or null when it isn't there. */
    suspend fun file(owner: String, name: String, ref: String, path: String): FileAt? = try {
        val obj: JsonObject = get("repos/$owner/$name/contents/${path.split('/').joinToString("/") { enc(it) }}?ref=${enc(ref)}")
        val content = obj["content"]?.jsonPrimitive?.contentOrNull ?: return null
        FileAt(String(Base64.getMimeDecoder().decode(content), Charsets.UTF_8), obj.getValue("sha").jsonPrimitive.content)
    } catch (e: GitHubException) {
        if (e.status == 404) null else throw e
    }

    suspend fun text(owner: String, name: String, branch: String, path: String): String? = file(owner, name, branch, path)?.text

    /**
     * Many small text files at once, by blob sha: one GraphQL query per [batch] files instead of a
     * request each, which matters for a blog with hundreds of posts. Binary blobs come back null.
     */
    suspend fun blobTexts(owner: String, name: String, shas: List<String>, batch: Int = 80): Map<String, String?> {
        val out = mutableMapOf<String, String?>()
        for (chunk in shas.distinct().chunked(batch)) {
            val fields = chunk.mapIndexed { i, sha -> "b$i: object(oid: \"$sha\") { ... on Blob { text isBinary } }" }
            val query = "query(\$owner: String!, \$name: String!) { repository(owner: \$owner, name: \$name) { ${fields.joinToString(" ")} } }"
            val body = buildJsonObject {
                put("query", query)
                put("variables", buildJsonObject { put("owner", owner); put("name", name) })
            }
            val result: JsonObject = post("graphql", body)
            val errors = result["errors"] as? JsonArray
            // GraphQL reports rate limits and timeouts as 200 with errors; both pass, so retry them.
            val repository = (result["data"] as? JsonObject)?.get("repository") as? JsonObject
                ?: throw GitHubException(
                    if (errors.toString().contains("RATE_LIMITED")) GitHubException.Kind.RateLimited else GitHubException.Kind.Network,
                    "GitHub couldn't read the posts just now",
                )
            chunk.forEachIndexed { i, sha ->
                val blob = repository["b$i"] as? JsonObject
                out[sha] = blob?.get("text")?.takeIf { it != JsonNull }?.jsonPrimitive?.contentOrNull
            }
        }
        return out
    }

    /**
     * Writes [changes] to [branch] as one commit and returns its sha. Uses the Git Data API so a
     * post and its images land together (one Pages build, no half-published post). If the branch
     * moves meanwhile, the commit is rebuilt on the new head; the blobs are already uploaded.
     */
    suspend fun commit(
        owner: String,
        name: String,
        branch: String,
        message: String,
        changes: List<FileChange>,
        /**
         * Paths that must still be as the caller last saw them, by blob sha (null: absent). Checked
         * on every attempt, so rebuilding on a newer head never overwrites a change made there.
         */
        expect: Map<String, String?> = emptyMap(),
        author: CommitAuthor? = null,
        attempts: Int = 3,
    ): String {
        val blobs = changes.associate { change ->
            change.path to change.content?.let { bytes ->
                post<Sha>("repos/$owner/$name/git/blobs", buildJsonObject {
                    put("content", Base64.getEncoder().encodeToString(bytes))
                    put("encoding", "base64")
                }).sha
            }
        }
        var lastConflict: GitHubException? = null
        repeat(attempts) {
            val head: Ref = get("repos/$owner/$name/git/ref/heads/${ref(branch)}")
            expect.forEach { (path, sha) ->
                if (file(owner, name, head.obj.sha, path)?.sha != sha) {
                    throw GitHubException(GitHubException.Kind.Changed, "$path changed on GitHub")
                }
            }
            val parent: Commit = get("repos/$owner/$name/git/commits/${head.obj.sha}")
            val tree: Sha = post("repos/$owner/$name/git/trees", buildJsonObject {
                put("base_tree", parent.tree.sha)
                putJsonArray("tree") {
                    blobs.forEach { (path, sha) ->
                        add(buildJsonObject {
                            put("path", path)
                            put("mode", "100644")
                            put("type", "blob")
                            // A null sha deletes the path.
                            if (sha == null) put("sha", JsonNull) else put("sha", sha)
                        })
                    }
                }
            })
            val commit: Sha = post("repos/$owner/$name/git/commits", buildJsonObject {
                put("message", message)
                put("tree", tree.sha)
                // The committer follows the author when only the author is given.
                author?.let { putJsonObject("author") { put("name", it.name); put("email", it.email) } }
                put("parents", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(head.obj.sha)) })
            })
            try {
                patch<JsonObject>("repos/$owner/$name/git/refs/heads/${ref(branch)}", buildJsonObject {
                    put("sha", commit.sha)
                    put("force", false)
                })
                return commit.sha
            } catch (e: GitHubException) {
                if (e.kind != GitHubException.Kind.Conflict) throw e
                lastConflict = e
            }
        }
        throw lastConflict!!
    }

    /** A commit as GitHub shows it, with the account it's attributed to. */
    suspend fun commitView(owner: String, name: String, sha: String): CommitView = get("repos/$owner/$name/commits/$sha")

    suspend fun pages(owner: String, name: String): PagesSite? = try {
        get("repos/$owner/$name/pages")
    } catch (e: GitHubException) {
        if (e.status == 404) null else throw e
    }

    /**
     * The Actions runs for a commit. Every Pages site deploys through one now, branch-built sites
     * too ("pages build and deployment"), so this tells whether a post went live. The Pages builds
     * endpoint would need a token that can write Pages settings, which the app shouldn't ask for.
     */
    suspend fun workflowRuns(owner: String, name: String, sha: String): List<WorkflowRun> =
        get<WorkflowRuns>("repos/$owner/$name/actions/runs?head_sha=$sha&per_page=20").runs

    private suspend inline fun <reified T> get(path: String): T = decode(send(request(path).get().build()))

    private suspend inline fun <reified T> post(path: String, body: JsonObject): T =
        decode(send(request(path).post(body.toString().toRequestBody(jsonType)).build()))

    private suspend inline fun <reified T> patch(path: String, body: JsonObject): T =
        decode(send(request(path).patch(body.toString().toRequestBody(jsonType)).build()))

    /**
     * A 2xx that isn't GitHub's JSON is almost always a network in the way (a captive portal's
     * login page), so it's retried like any other failure to reach GitHub.
     */
    private inline fun <reified T> decode(body: String): T = try {
        json.decodeFromString(body)
    } catch (e: IllegalArgumentException) {
        throw GitHubException(GitHubException.Kind.Network, "GitHub's answer didn't come through", cause = e)
    }

    private fun request(path: String): Request.Builder = Request.Builder()
        .url(apiBase.toString().trimEnd('/') + "/" + path)
        .header("Authorization", "Bearer $token")
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")

    private suspend fun send(request: Request): String = withContext(Dispatchers.IO) {
        // Reading the body can fail too (a connection dropped mid-response); that's as retryable
        // as not connecting, and must not escape as a bare IOException.
        val (response, body) = try {
            http.newCall(request).execute().use { it to it.body.string() }
        } catch (e: IOException) {
            throw GitHubException(GitHubException.Kind.Network, "Couldn't reach GitHub", cause = e)
        }
        response.let {
            if (it.isSuccessful) return@withContext body
            val message = runCatching { json.parseToJsonElement(body).jsonObject["message"]?.jsonPrimitive?.contentOrNull }.getOrNull() ?: it.message
            val kind = when {
                it.code == 401 -> GitHubException.Kind.Unauthorized
                (it.code == 403 || it.code == 429) && (it.header("x-ratelimit-remaining") == "0" || it.header("retry-after") != null) ->
                    GitHubException.Kind.RateLimited
                it.code == 403 || it.code == 404 -> GitHubException.Kind.NoAccess
                it.code == 409 || (it.code == 422 && message.contains("fast forward", ignoreCase = true)) -> GitHubException.Kind.Conflict
                else -> GitHubException.Kind.Other
            }
            throw GitHubException(kind, message, it.code)
        }
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /** A branch in a URL path: each segment encoded, the slashes of `site/main` kept. */
    private fun ref(branch: String) = branch.split('/').joinToString("/") { enc(it) }
}
