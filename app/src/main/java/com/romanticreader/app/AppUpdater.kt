package com.romanticreader.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class AppUpdateInfo(val version: String, val notes: String, val downloadUrl: String, val sha256: String?)

object AppUpdater {
    private const val RELEASE_API = "https://api.github.com/repos/xtzhdhl/qiangwei-shujian-reader/releases/latest"
    fun check(currentVersion: String): AppUpdateInfo? {
        val release = JSONObject(requestText(RELEASE_API))
        val remote = release.optString("tag_name").removePrefix("v")
        if (!isNewer(remote, currentVersion)) return null
        val assets = release.optJSONArray("assets") ?: error("Release 没有 APK 附件")
        var apk: JSONObject? = null
        for (i in 0 until assets.length()) {
            val item = assets.optJSONObject(i) ?: continue
            if (item.optString("name").endsWith(".apk", true)) { apk = item; break }
        }
        val asset = apk ?: error("Release 中找不到 APK")
        val digest = asset.optString("digest").removePrefix("sha256:").takeIf { it.length == 64 }
        return AppUpdateInfo(remote, release.optString("body"), asset.optString("browser_download_url"), digest)
    }
    fun downloadAndInstall(context: Context, info: AppUpdateInfo) {
        val apkFile = File(context.cacheDir, "rose-letter-update.apk")
        val connection = (URL(info.downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout=20000; readTimeout=30000; instanceFollowRedirects=true
            setRequestProperty("User-Agent", "RoseLetter Android")
        }
        try {
            if (connection.responseCode !in 200..299) error("下载失败 HTTP ${connection.responseCode}")
            connection.inputStream.use { input -> apkFile.outputStream().use { output -> input.copyTo(output) } }
        } finally { connection.disconnect() }
        if (info.sha256 != null) {
            val digest = apkFile.inputStream().use { input ->
                val md = MessageDigest.getInstance("SHA-256"); val buffer=ByteArray(8192)
                while(true){ val n=input.read(buffer); if(n<0) break; md.update(buffer,0,n) }
                md.digest().joinToString("") { "%02x".format(it) }
            }
            require(digest.equals(info.sha256, true)) { "APK SHA-256 校验失败" }
        }
        install(context, apkFile)
    }
    fun install(context: Context, file: File) {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            error("请先在系统设置允许安装未知应用，再重新点安装")
        }
        val uri=FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
    private fun requestText(url:String):String {
        val c=(URL(url).openConnection() as HttpURLConnection).apply { connectTimeout=15000; readTimeout=20000; instanceFollowRedirects=true; setRequestProperty("Accept","application/vnd.github+json"); setRequestProperty("User-Agent","RoseLetter Android") }
        try { if(c.responseCode !in 200..299) error("检查更新 HTTP ${c.responseCode}"); return c.inputStream.bufferedReader().use{it.readText()} } finally { c.disconnect() }
    }
    private fun isNewer(remote:String,local:String):Boolean {
        val a=remote.split(".").map{it.toIntOrNull()?:0}; val b=local.split(".").map{it.toIntOrNull()?:0}
        for(i in 0..2){ if((a.getOrElse(i){0})!=(b.getOrElse(i){0})) return a.getOrElse(i){0}>b.getOrElse(i){0} }
        return false
    }
}
