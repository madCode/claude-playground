package com.app.jekyllposter.core.github

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/**
 * "Sign in with GitHub" for an app with no server: GitHub's device flow for a GitHub App. The
 * phone shows a short code, the writer enters it on github.com, and the app polls until GitHub
 * hands over a token. Only the app's client ID is needed; it isn't a secret.
 */
class DeviceFlow(
    private val http: Call.Factory,
    private val clientId: String,
    private val base: HttpUrl = "https://github.com/".toHttpUrl(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class Code(
        @SerialName("device_code") val deviceCode: String,
        @SerialName("user_code") val userCode: String,
        @SerialName("verification_uri") val verificationUri: String,
        @SerialName("expires_in") val expiresIn: Int,
        val interval: Int = 5,
    )

    /** A user token. GitHub Apps' tokens expire (8 hours) unless the app turns that off. */
    @Serializable
    data class Tokens(
        @SerialName("access_token") val accessToken: String,
        @SerialName("expires_in") val expiresIn: Long? = null,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("refresh_token_expires_in") val refreshTokenExpiresIn: Long? = null,
    )

    @Serializable
    private data class Reply(
        @SerialName("access_token") val accessToken: String? = null,
        @SerialName("expires_in") val expiresIn: Long? = null,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("refresh_token_expires_in") val refreshTokenExpiresIn: Long? = null,
        val error: String? = null,
        @SerialName("error_description") val errorDescription: String? = null,
        val interval: Int? = null,
    )

    suspend fun start(): Code {
        val body = post("login/device/code", FormBody.Builder().add("client_id", clientId).build())
        return try {
            json.decodeFromString<Code>(body)
        } catch (e: IllegalArgumentException) {
            // An error reply instead: usually the app doesn't have the device flow turned on.
            throw GitHubException(GitHubException.Kind.Other, "GitHub wouldn't start sign-in for this app. Is Device Flow turned on in its settings?", cause = e)
        }
    }

    /**
     * Waits for the writer to approve [code] on GitHub. Throws a [GitHubException] if they deny it
     * or the code expires; cancel the coroutine to stop waiting.
     */
    suspend fun await(code: Code, sleep: suspend (Long) -> Unit = { delay(it) }): Tokens {
        var interval = code.interval.coerceAtLeast(1)
        while (true) {
            sleep(interval * 1000L)
            val reply = try {
                token(
                    FormBody.Builder()
                        .add("client_id", clientId)
                        .add("device_code", code.deviceCode)
                        .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                        .build(),
                )
            } catch (e: GitHubException) {
                // The writer is off in a browser, often on mobile data: a dropped poll is no
                // reason to throw away a code they may be entering right now.
                if (e.retryable) continue else throw e
            }
            when (reply.error) {
                null -> return reply.tokens()
                "authorization_pending" -> Unit
                // GitHub asks for a longer wait between polls, and says how long.
                "slow_down" -> interval = reply.interval ?: (interval + 5)
                "expired_token" -> throw GitHubException(GitHubException.Kind.Other, "The code expired. Start again for a new one.")
                "access_denied" -> throw GitHubException(GitHubException.Kind.Other, "Sign-in was cancelled on GitHub.")
                else -> throw GitHubException(GitHubException.Kind.Other, reply.errorDescription ?: reply.error)
            }
        }
    }

    /** A new token from a refresh token, without the app's secret (allowed for device-flow apps). */
    suspend fun refresh(refreshToken: String): Tokens {
        val reply = token(
            FormBody.Builder()
                .add("client_id", clientId)
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .build(),
        )
        if (reply.error != null) throw GitHubException(GitHubException.Kind.Unauthorized, reply.errorDescription ?: reply.error)
        return reply.tokens()
    }

    private suspend fun token(form: FormBody): Reply = try {
        json.decodeFromString(post("login/oauth/access_token", form))
    } catch (e: IllegalArgumentException) {
        throw GitHubException(GitHubException.Kind.Network, "GitHub's answer didn't come through", cause = e)
    }

    private fun Reply.tokens() = Tokens(accessToken ?: error("no token"), expiresIn, refreshToken, refreshTokenExpiresIn)

    private suspend fun post(path: String, form: FormBody): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(base.toString().trimEnd('/') + "/" + path)
            .header("Accept", "application/json").post(form).build()
        try {
            http.newCall(request).execute().use {
                val body = it.body.string()
                if (!it.isSuccessful) {
                    val kind = if (it.code >= 500 || it.code == 429) GitHubException.Kind.Network else GitHubException.Kind.Other
                    throw GitHubException(kind, "GitHub said ${it.code}", it.code)
                }
                body
            }
        } catch (e: IOException) {
            throw GitHubException(GitHubException.Kind.Network, "Couldn't reach GitHub", cause = e)
        }
    }
}
