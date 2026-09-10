package com.mati.tradenotify.update

import android.util.Log
import com.mati.tradenotify.BuildConfig
import com.mati.tradenotify.util.TAG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

@Serializable
private data class GhRelease(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GhAsset> = emptyList(),
)

@Serializable
private data class GhAsset(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
)

data class AvailableUpdate(
    val versionName: String,
    /** Direct APK link when the release has one, so the download is a single tap. */
    val apkUrl: String?,
    val releaseUrl: String,
)

/**
 * Checks GitHub Releases for a newer build.
 *
 * A sideloaded app has no store to update it, so without this it silently goes stale forever —
 * which for an alarm app means running an old build with a bug you already fixed.
 */
object UpdateChecker {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(
        currentVersionName: String = BuildConfig.VERSION_NAME,
        repo: String = BuildConfig.GITHUB_REPO,
    ): AvailableUpdate? = withContext(Dispatchers.IO) {
        val release = fetchLatest(repo) ?: return@withContext null
        if (release.draft || release.prerelease) return@withContext null

        val latest = release.tagName.removePrefix("v")
        if (!isNewer(currentVersionName, latest)) return@withContext null

        AvailableUpdate(
            versionName = latest,
            apkUrl = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                ?.browserDownloadUrl,
            releaseUrl = release.htmlUrl,
        )
    }

    private fun fetchLatest(repo: String): GhRelease? = runCatching {
        val connection = (URL("https://api.github.com/repos/$repo/releases/latest")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            json.decodeFromString<GhRelease>(connection.inputStream.bufferedReader().readText())
        } finally {
            connection.disconnect()
        }
    }.onFailure { Log.w(TAG, "Update check failed: ${it.message}") }.getOrNull()

    /**
     * Compares dotted version names numerically, so "1.10" correctly beats "1.9" — a string
     * comparison would get that backwards.
     */
    fun isNewer(current: String, candidate: String): Boolean {
        val a = parts(current)
        val b = parts(candidate)
        for (i in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(i) { 0 }
            val right = b.getOrElse(i) { 0 }
            if (right != left) return right > left
        }
        return false
    }

    private fun parts(version: String): List<Int> =
        version.trim().removePrefix("v")
            .split('.', '-', '+')
            .mapNotNull { segment -> segment.takeWhile { it.isDigit() }.toIntOrNull() }
}
