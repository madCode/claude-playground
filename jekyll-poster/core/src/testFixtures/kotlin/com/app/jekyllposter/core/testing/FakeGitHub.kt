package com.app.jekyllposter.core.testing

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import java.io.File
import java.security.MessageDigest
import java.util.Base64

/**
 * An in-memory GitHub serving one repository, for tests: the REST and GraphQL calls the app makes,
 * backed by real commits, so a test can publish and then read back what landed on the branch.
 */
class FakeGitHub(
    files: Map<String, ByteArray> = emptyMap(),
    val owner: String = "sample",
    val repo: String = "sample-blog",
    val branch: String = "main",
    var token: String = "good-token",
) : AutoCloseable {
    data class CommitRecord(val sha: String, val tree: String, val parent: String?, val message: String)

    private val blobs = mutableMapOf<String, ByteArray>()
    private val trees = mutableMapOf<String, Map<String, String>>()
    val commits = mutableMapOf<String, CommitRecord>()
    private val refs = mutableMapOf<String, String>()
    val log = mutableListOf<String>()

    /** Runs before a ref update, e.g. to push a competing commit and force a conflict. */
    var beforeRefUpdate: (() -> Unit)? = null

    /** When set, the next commit lands but its answer is a 502, as if lost on the way back. */
    var loseNextRefAnswer = false

    /** Status of the Pages workflow run for each commit; commits without one have no run yet. */
    val runs = mutableMapOf<String, Pair<String, String?>>()

    /** Other workflow runs per commit, as (name, status, conclusion): CI, linters. */
    val otherRuns = mutableMapOf<String, List<Triple<String, String, String?>>>()

    /** Whether the writer has entered the device-flow code on "github.com". */
    var deviceApproved = false

    /** Set to make the tree listing say it was cut short, as GitHub does for huge repositories. */
    var truncated = false

    /** Paths that answer with this HTTP status instead, to test failures. */
    val failures = mutableMapOf<String, Int>()

    private val server = MockWebServer()
    private val json = Json

    init {
        val tree = files.mapValues { (_, bytes) -> putBlob(bytes) }
        val treeSha = putTree(tree)
        val commit = sha("commit", "initial $treeSha")
        commits[commit] = CommitRecord(commit, treeSha, null, "Initial commit")
        refs[branch] = commit
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = synchronized(this@FakeGitHub) { handle(request) }
        }
        server.start()
    }

    val apiBase get() = server.url("/")

    val head: String get() = refs.getValue(branch)

    /** The files on the branch now. */
    fun files(): Map<String, ByteArray> = trees.getValue(commits.getValue(head).tree).mapValues { blobs.getValue(it.value) }

    fun text(path: String): String? = files()[path]?.toString(Charsets.UTF_8)

    /** Commits straight to the branch, as someone pushing from a laptop would. */
    fun push(message: String, changes: Map<String, String?>) {
        val tree = trees.getValue(commits.getValue(head).tree).toMutableMap()
        changes.forEach { (path, text) -> if (text == null) tree.remove(path) else tree[path] = putBlob(text.toByteArray()) }
        val commit = sha("commit", "$message ${System.nanoTime()}")
        commits[commit] = CommitRecord(commit, putTree(tree), head, message)
        refs[branch] = commit
    }

    override fun close() = server.close()

    private fun handle(request: RecordedRequest): MockResponse {
        val url = request.url
        val path = url.encodedPath.removePrefix("/")
        log += "${request.method} $path"
        failures.entries.firstOrNull { path.startsWith(it.key) }?.let { return error(it.value, "Simulated failure") }
        if (path == "login/device/code") {
            return ok(buildJsonObject {
                put("device_code", "device-1"); put("user_code", "WDJB-MJHT"); put("verification_uri", "https://github.com/login/device")
                put("expires_in", 900); put("interval", 1)
            })
        }
        if (path == "login/oauth/access_token") {
            val form = request.body?.utf8().orEmpty()
            return when {
                form.contains("grant_type=refresh_token") -> ok(buildJsonObject {
                    put("access_token", token); put("expires_in", 28800); put("refresh_token", "refresh-2")
                })
                !deviceApproved -> ok(buildJsonObject { put("error", "authorization_pending") })
                else -> ok(buildJsonObject { put("access_token", token); put("expires_in", 28800); put("refresh_token", "refresh-1") })
            }
        }
        if (request.headers["Authorization"] != "Bearer $token") return error(401, "Bad credentials")
        val body = request.body?.utf8()
        val base = "repos/$owner/$repo"
        return when {
            path == "user" -> ok(buildJsonObject { put("login", owner); put("name", "Sample Writer") })
            path == "user/repos" -> ok(buildJsonArray {
                add(repoJson())
                add(buildJsonObject {
                    put("full_name", "$owner/read-only"); put("name", "read-only"); put("default_branch", "main")
                    put("owner", buildJsonObject { put("login", owner) })
                    put("permissions", buildJsonObject { put("push", false) })
                })
            })
            path == base -> ok(repoJson())
            path.startsWith("repos/") && !path.startsWith(base) -> error(404, "Not Found")
            path == "graphql" -> graphql(body!!)
            path.startsWith("$base/git/trees/") && request.method == "GET" -> {
                val ref = path.removePrefix("$base/git/trees/").let { java.net.URLDecoder.decode(it, "UTF-8") }
                val commit = refs[ref] ?: return error(404, "Not Found")
                val tree = trees.getValue(commits.getValue(commit).tree)
                ok(buildJsonObject {
                    put("sha", commits.getValue(commit).tree)
                    put("truncated", truncated)
                    put("tree", buildJsonArray {
                        tree.forEach { (p, s) -> add(buildJsonObject { put("path", p); put("type", "blob"); put("sha", s); put("mode", "100644") }) }
                    })
                })
            }
            path.startsWith("$base/contents/") -> {
                val file = java.net.URLDecoder.decode(path.removePrefix("$base/contents/"), "UTF-8")
                val ref = url.queryParameter("ref") ?: branch
                val commit = refs[ref] ?: ref.takeIf { it in commits }
                val sha = commit?.let { trees.getValue(commits.getValue(it).tree)[file] } ?: return error(404, "Not Found")
                ok(buildJsonObject { put("sha", sha); put("encoding", "base64"); put("content", Base64.getMimeEncoder().encodeToString(blobs.getValue(sha))) })
            }
            path == "$base/git/blobs" -> {
                val obj = json.parseToJsonElement(body!!).jsonObject
                val bytes = Base64.getDecoder().decode(obj.getValue("content").jsonPrimitive.content)
                ok(buildJsonObject { put("sha", putBlob(bytes)) }, 201)
            }
            path == "$base/git/ref/heads/$branch" -> ok(buildJsonObject { put("object", buildJsonObject { put("sha", head) }) })
            path.startsWith("$base/git/commits/") && request.method == "GET" -> {
                val c = commits[path.substringAfterLast('/')] ?: return error(404, "Not Found")
                ok(buildJsonObject { put("sha", c.sha); put("tree", buildJsonObject { put("sha", c.tree) }) })
            }
            path == "$base/git/trees" -> {
                val obj = json.parseToJsonElement(body!!).jsonObject
                val baseTree = obj["base_tree"]?.jsonPrimitive?.contentOrNull
                val tree = (baseTree?.let { trees.getValue(it) } ?: emptyMap()).toMutableMap()
                obj.getValue("tree").jsonArray.forEach {
                    val e = it.jsonObject
                    val p = e.getValue("path").jsonPrimitive.content
                    val s = e["sha"]
                    if (s == null || s == JsonNull) tree.remove(p) else tree[p] = s.jsonPrimitive.content
                }
                ok(buildJsonObject { put("sha", putTree(tree)) }, 201)
            }
            path == "$base/git/commits" -> {
                val obj = json.parseToJsonElement(body!!).jsonObject
                val parent = obj.getValue("parents").jsonArray.first().jsonPrimitive.content
                val tree = obj.getValue("tree").jsonPrimitive.content
                val message = obj.getValue("message").jsonPrimitive.content
                val sha = sha("commit", "$tree $parent $message ${System.nanoTime()}")
                commits[sha] = CommitRecord(sha, tree, parent, message)
                ok(buildJsonObject { put("sha", sha) }, 201)
            }
            path == "$base/git/refs/heads/$branch" && request.method == "PATCH" -> {
                beforeRefUpdate?.let { beforeRefUpdate = null; it() }
                val obj = json.parseToJsonElement(body!!).jsonObject
                val sha = obj.getValue("sha").jsonPrimitive.content
                if (commits.getValue(sha).parent != head) return error(422, "Update is not a fast forward")
                refs[branch] = sha
                if (loseNextRefAnswer) { loseNextRefAnswer = false; return error(502, "Bad gateway") }
                ok(buildJsonObject { put("object", buildJsonObject { put("sha", sha) }) })
            }
            path == "$base/actions/runs" -> {
                val sha = url.queryParameter("head_sha")
                ok(buildJsonObject {
                    put("workflow_runs", buildJsonArray {
                        runs[sha]?.let { (status, conclusion) ->
                            add(buildJsonObject {
                                put("name", "pages build and deployment"); put("status", status); put("head_sha", sha)
                                if (conclusion != null) put("conclusion", conclusion) else put("conclusion", JsonNull)
                                put("html_url", "https://github.com/$owner/$repo/actions/runs/1")
                                put("path", "dynamic/pages/pages-build-deployment")
                            })
                        }
                        otherRuns[sha].orEmpty().forEach { (name, status, conclusion) ->
                            add(buildJsonObject {
                                put("name", name); put("status", status); put("head_sha", sha); put("path", ".github/workflows/ci.yml")
                                if (conclusion != null) put("conclusion", conclusion) else put("conclusion", JsonNull)
                            })
                        }
                    })
                })
            }
            path == "$base/pages" -> ok(buildJsonObject { put("html_url", "https://$owner.github.io/$repo/"); put("build_type", "legacy") })
            else -> error(404, "Not Found: $path")
        }
    }

    private fun graphql(body: String): MockResponse {
        val query = json.parseToJsonElement(body).jsonObject.getValue("query").jsonPrimitive.content
        val fields = Regex("""(b\d+): object\(oid: "([0-9a-f]+)"\)""").findAll(query)
        return ok(buildJsonObject {
            put("data", buildJsonObject {
                put("repository", buildJsonObject {
                    fields.forEach { m ->
                        val bytes = blobs[m.groupValues[2]]
                        if (bytes == null) put(m.groupValues[1], JsonNull) else {
                            val binary = bytes.any { it == 0.toByte() }
                            put(m.groupValues[1], buildJsonObject {
                                put("isBinary", binary)
                                if (binary) put("text", JsonNull) else put("text", bytes.toString(Charsets.UTF_8))
                            })
                        }
                    }
                })
            })
        })
    }

    private fun repoJson() = buildJsonObject {
        put("full_name", "$owner/$repo"); put("name", repo); put("default_branch", branch); put("has_pages", true)
        put("owner", buildJsonObject { put("login", owner) })
        put("permissions", buildJsonObject { put("push", true) })
    }

    private fun putBlob(bytes: ByteArray): String = sha("blob ${bytes.size}\u0000", bytes).also { blobs[it] = bytes }

    private fun putTree(tree: Map<String, String>): String =
        sha("tree", tree.toSortedMap().entries.joinToString("\n") { "${it.key} ${it.value}" }).also { trees[it] = tree }

    private fun sha(prefix: String, content: String) = sha(prefix, content.toByteArray())

    private fun sha(prefix: String, content: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update(prefix.toByteArray()); digest.update(content)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun ok(body: Any, code: Int = 200) = MockResponse.Builder().code(code)
        .addHeader("Content-Type", "application/json").body(body.toString()).build()

    private fun error(code: Int, message: String) = MockResponse.Builder().code(code)
        .addHeader("Content-Type", "application/json").body(buildJsonObject { put("message", message) }.toString()).build()

    companion object {
        /** The sample blog in this repository, as the test task points to it. */
        fun sampleBlogFiles(): Map<String, ByteArray> {
            val root = File(System.getProperty("sampleBlog") ?: error("sampleBlog system property not set"))
            return root.walkTopDown().filter { it.isFile }.associate { it.relativeTo(root).path.replace('\\', '/') to it.readBytes() }
        }

        fun sampleBlog() = FakeGitHub(sampleBlogFiles())
    }
}
