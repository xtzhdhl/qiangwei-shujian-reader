package com.romanticreader.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class AppUpdateInfo(
    val version: String,
    val notes: String,
    val downloadUrl: String,
    val sha256: String?,
    val versionCode: Long
)

object AppUpdater {
    private const val RELEASES_API = "https://api.github.com/repos/xtzhdhl/qiangwei-shujian-reader/releases?per_page=10"

    /** Returns null only when a published release exists but none is newer. */
    fun check(currentVersion: String, currentVersionCode: Long): AppUpdateInfo? {
        val releases = JSONArray(requestText(RELEASES_API))
        val release = (0 until releases.length())
            .mapNotNull { releases.optJSONObject(it) }
            .firstOrNull { !it.optBoolean("draft", false) && !it.optBoolean("prerelease", false) }
            ?: error("仓库尚未发布正式版本。首次签名版发布后，应用内更新才会启用。")

        val tag = release.optString("tag_name")
        val remoteVersion = tag.removePrefix("v").removePrefix("V")
        val assets = release.optJSONArray("assets") ?: error("Release 没有附件")
        var apk: JSONObject? = null
        for (i in 0 until assets.length()) {
            val item = assets.optJSONObject(i) ?: continue
            if (item.optString("name").endsWith(".apk", true)) { apk = item; break }
        }
        val asset = apk ?: error("最新正式 Release 中找不到 APK 附件")
        val remoteCode = versionCodeFromTag(remoteVersion)
        if (remoteCode <= currentVersionCode && !isNewer(remoteVersion, currentVersion)) return null
        val digest = asset.optString("digest").removePrefix("sha256:").takeIf { it.length == 64 }
        val url = asset.optString("browser_download_url")
        require(url.startsWith("https://github.com/")) { "更新下载地址不是可信的 GitHub Release 地址" }
        return AppUpdateInfo(remoteVersion, release.optString("body"), url, digest, remoteCode)
    }

    fun downloadAndInstall(context: Context, info: AppUpdateInfo) {
        val apkFile = File(context.cacheDir, "rose-letter-update.apk")
        val connection = (URL(info.downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 45000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "RoseLetter Android updater")
            setRequestProperty("Accept", "application/octet-stream")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("下载更新失败：HTTP $code")
            val length = connection.contentLengthLong
            if (length <= 0L) error("服务器没有返回有效的 APK 文件")
            if (length > 150L * 1024 * 1024) error("更新包超过 150 MB，已停止下载")
            connection.inputStream.use { input ->
                apkFile.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > 150L * 1024 * 1024) error("更新包超过 150 MB，已停止下载")
                        output.write(buffer, 0, read)
                    }
                }
            }
        } finally { connection.disconnect() }

        if (info.sha256 != null) {
            val actual = apkFile.inputStream().use { input ->
                val md = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    md.update(buffer, 0, n)
                }
                md.digest().joinToString("") { "%02x".format(it) }
            }
            require(actual.equals(info.sha256, true)) { "APK SHA-256 校验失败，已阻止安装" }
        }

        val archiveInfo = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
        } ?: error("下载文件不是有效的 Android APK")
        require(archiveInfo.packageName == context.packageName) { "安装包应用标识不一致，已阻止安装" }
        install(context, apkFile)
    }

    fun install(context: Context, file: File) {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            error("请先在系统设置允许蔷薇书笺安装未知应用，再回到应用点击安装")
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    private fun requestText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 20000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "RoseLetter Android")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                val detail = runCatching { connection.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull().orEmpty()
                error("GitHub 更新服务返回 HTTP $status。$detail")
            }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally { connection.disconnect() }
    }

    private fun versionCodeFromTag(version: String): Long {
        val parts = version.split(".", "-", "+").take(3).map { it.toLongOrNull() ?: 0L }
        return parts.getOrElse(0) { 0L } * 100000L +
            parts.getOrElse(1) { 0L } * 1000L +
            parts.getOrElse(2) { 0L }
    }

    private fun isNewer(remote: String, local: String): Boolean {
        val a = remote.split(".", "-", "+").map { it.toIntOrNull() ?: 0 }
        val b = local.split(".", "-", "+").map { it.toIntOrNull() ?: 0 }
        for (i in 0..2) {
            val av = a.getOrElse(i) { 0 }
            val bv = b.getOrElse(i) { 0 }
            if (av != bv) return av > bv
        }
        return false
    }
}
