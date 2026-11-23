package com.romanticreader.app.source

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpCookie
import java.net.URI
import java.util.Locale

/** App-private persistent cookie storage for HTTP source requests. */
class PersistentCookieJar(context: Context) {
    private data class Entry(
        val name: String,
        val value: String,
        val domain: String,
        val path: String,
        val secure: Boolean,
        val expiresAt: Long
    )

    private val prefs = context.applicationContext.getSharedPreferences("rose_letter_cookies", Context.MODE_PRIVATE)

    @Synchronized
    fun cookieHeader(url: String): String {
        val uri = runCatching { URI(url) }.getOrNull() ?: return ""
        val host = uri.host?.lowercase(Locale.ROOT) ?: return ""
        val path = uri.path?.ifBlank { "/" } ?: "/"
        val secureRequest = uri.scheme.equals("https", true)
        val now = System.currentTimeMillis()
        return readAll()
            .filter { it.expiresAt > now && domainMatches(host, it.domain) && pathMatches(path, it.path) && (!it.secure || secureRequest) }
            .sortedByDescending { it.path.length }
            .joinToString("; ") { "${it.name}=${it.value}" }
    }

    @Synchronized
    fun saveResponse(url: String, setCookieHeaders: List<String>) {
        if (setCookieHeaders.isEmpty()) return
        val uri = runCatching { URI(url) }.getOrNull() ?: return
        val host = uri.host?.lowercase(Locale.ROOT) ?: return
        val all = readAll().associateBy { key(it) }.toMutableMap()
        val now = System.currentTimeMillis()
        setCookieHeaders.forEach { header ->
            runCatching { HttpCookie.parse(header) }.getOrDefault(emptyList()).forEach cookieLoop@ { cookie ->
                val domain = (cookie.domain?.removePrefix(".") ?: host).lowercase(Locale.ROOT)
                if (!domainMatches(host, domain)) return@cookieLoop
                val cookiePath = cookie.path?.takeIf { it.startsWith("/") } ?: defaultPath(uri.path)
                val entryKey = "${cookie.name.lowercase(Locale.ROOT)}|$domain|$cookiePath"
                if (cookie.maxAge == 0L) {
                    all.remove(entryKey)
                } else {
                    val expires = if (cookie.maxAge > 0) now + cookie.maxAge * 1000L else now + SESSION_TTL
                    val entry = Entry(cookie.name, cookie.value, domain, cookiePath, cookie.secure, expires)
                    all[key(entry)] = entry
                }
            }
        }
        writeAll(all.values.toList())
    }

    @Synchronized
    fun setManualCookieHeader(url: String, header: String) {
        val uri = runCatching { URI(url) }.getOrNull() ?: return
        val host = uri.host?.lowercase(Locale.ROOT) ?: return
        val all = readAll().associateBy { key(it) }.toMutableMap()
        val normalized = header.trim()
        if (normalized.isBlank()) {
            all.entries.removeAll { (_, item) -> domainMatches(host, item.domain) }
            writeAll(all.values.toList())
            return
        }
        normalized.split(";").map { it.trim() }.filter { it.contains("=") }.forEach { pair ->
            val name = pair.substringBefore("=").trim()
            val value = pair.substringAfter("=").trim()
            if (name.isBlank() || name.startsWith("$")) return@forEach
            val item = Entry(name, value, host, "/", false, System.currentTimeMillis() + SESSION_TTL)
            all[key(item)] = item
        }
        writeAll(all.values.toList())
    }

    @Synchronized
    fun importCookieManagerCookies(url: String, cookieHeader: String) {
        setManualCookieHeader(url, cookieHeader)
    }

    @Synchronized
    fun getCookieHeaderForDomain(url: String): String = cookieHeader(url)

    @Synchronized
    fun clearForUrl(url: String) {
        val host = runCatching { URI(url).host?.lowercase(Locale.ROOT) }.getOrNull() ?: return
        writeAll(readAll().filterNot { domainMatches(host, it.domain) })
    }

    private fun readAll(): List<Entry> {
        val raw = prefs.getString("cookies", "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                Entry(
                    item.optString("name"),
                    item.optString("value"),
                    item.optString("domain").lowercase(Locale.ROOT),
                    item.optString("path", "/").ifBlank { "/" },
                    item.optBoolean("secure"),
                    item.optLong("expiresAt")
                ).takeIf { it.name.isNotBlank() && it.domain.isNotBlank() }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeAll(items: List<Entry>) {
        val now = System.currentTimeMillis()
        val array = JSONArray()
        items.filter { it.expiresAt > now }.forEach { item ->
            array.put(JSONObject()
                .put("name", item.name)
                .put("value", item.value)
                .put("domain", item.domain)
                .put("path", item.path)
                .put("secure", item.secure)
                .put("expiresAt", item.expiresAt))
        }
        prefs.edit().putString("cookies", array.toString()).apply()
    }

    private fun key(item: Entry) = "${item.name.lowercase(Locale.ROOT)}|${item.domain}|${item.path}"

    private fun domainMatches(host: String, domain: String): Boolean = host == domain || host.endsWith(".$domain")

    private fun pathMatches(requestPath: String, cookiePath: String): Boolean =
        requestPath == cookiePath || requestPath.startsWith(if (cookiePath.endsWith("/")) cookiePath else "$cookiePath/")

    private fun defaultPath(path: String?): String {
        val value = path?.takeIf { it.startsWith("/") } ?: return "/"
        val lastSlash = value.lastIndexOf("/")
        return if (lastSlash <= 0) "/" else value.substring(0, lastSlash)
    }

    private companion object {
        const val SESSION_TTL = 30L * 24 * 60 * 60 * 1000
    }
}
