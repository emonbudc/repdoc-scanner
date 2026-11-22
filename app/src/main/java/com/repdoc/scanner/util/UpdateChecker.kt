package com.repdoc.scanner.util

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import com.repdoc.scanner.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionName: String,
    val releaseUrl: String,
    val apkUrl: String?,
    val notes: String
)

object UpdateChecker {

    val releasesUrl = "https://github.com/${BuildConfig.UPDATE_REPO}/releases/latest"
    private val apiUrl =
        "https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest"

    suspend fun latest(): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(apiUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty(
                "User-Agent",
                "RepdocScanner/${BuildConfig.VERSION_NAME}"
            )
            try {
                val code = connection.responseCode
                if (code !in 200..299) error("Update check failed ($code)")
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val tag = json.optString("tag_name")
                    .trim()
                    .removePrefix("v")
                    .removePrefix("V")
                val releaseUrl = json.optString("html_url")
                    .takeIf { it.isNotBlank() }
                    ?: releasesUrl
                val apkUrl = run {
                    val assets = json.optJSONArray("assets") ?: return@run null
                    for (index in 0 until assets.length()) {
                        val asset = assets.optJSONObject(index) ?: continue
                        if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                            val url = asset.optString("browser_download_url")
                            if (url.isNotBlank()) return@run url
                        }
                    }
                    null
                }
                UpdateInfo(
                    versionName = tag.ifBlank { "latest" },
                    releaseUrl = releaseUrl,
                    apkUrl = apkUrl,
                    notes = json.optString("body").trim()
                )
            } finally {
                connection.disconnect()
            }
        }
    }

    fun isNewer(latest: String, current: String): Boolean {
        val latestParts = versionParts(latest)
        if (latestParts.isEmpty()) return false
        val currentParts = versionParts(current)
        for (index in 0 until maxOf(latestParts.size, currentParts.size)) {
            val latestPart = latestParts.getOrElse(index) { 0 }
            val currentPart = currentParts.getOrElse(index) { 0 }
            if (latestPart != currentPart) return latestPart > currentPart
        }
        return false
    }

    fun openReleasePage(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(releasesUrl))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun downloadUpdate(context: Context, info: UpdateInfo): Boolean {
        val url = info.apkUrl ?: return false
        return runCatching {
            val fileName = "repdoc-scanner-${info.versionName}.apk"
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("Repdoc Scanner ${info.versionName}")
                .setDescription("Downloading update")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                .setDestinationInExternalFilesDir(
                    context,
                    Environment.DIRECTORY_DOWNLOADS,
                    fileName
                )
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            true
        }.getOrDefault(false)
    }

    private fun versionParts(version: String): List<Int> =
        version.split(Regex("[^0-9]+"))
            .mapNotNull { it.toIntOrNull() }
}
