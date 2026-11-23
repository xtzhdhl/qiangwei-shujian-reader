package com.romanticreader.app.source

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.nodes.Attribute
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.Locale

data class SourceBookHit(
    val sourceUrl: String,
    val name: String,
    val author: String,
    val intro: String,
    val coverUrl: String,
    val bookUrl: String,
    val kind: String = "",
    val lastChapter: String = ""
)

data class SourceBookDetail(
    val sourceUrl: String,
    val name: String,
    val author: String,
    val intro: String,
    val coverUrl: String,
    val bookUrl: String,
    val tocUrl: String,
    val kind: String = "",
    val lastChapter: String = ""
)

data class SourceChapter(
    val sourceUrl: String,
    val name: String,
    val url: String,
    val index: Int,
    val isVip: Boolean = false
)

class LegadoRuleEngine {
    private data class RequestSpec(
        val url: String,
        val method: String = "GET",
        val body: String = "",
        val charset: String = "UTF-8",
        val headers: Map<String, String> = emptyMap()
    )

    private data class PageData(val text: String, val finalUrl: String)

    fun search(source: JSONObject, keyword: String, page: Int = 1): List<SourceBookHit> {
        val searchRules = ruleObject(source, "ruleSearch")
        val searchTemplate = source.optString("searchUrl").ifBlank { searchRules.optString("url") }
        require(searchTemplate.isNotBlank()) { "该书源没有配置 searchUrl" }
        val pageData = request(source, searchTemplate, mapOf("key" to keyword, "page" to page.toString()))
        val listRule = searchRules.optString("bookList")
        require(listRule.isNotBlank()) { "该书源没有配置 ruleSearch.bookList" }
        val nodes = selectNodes(pageData.text, listRule)
        return nodes.mapNotNull { node ->
            val name = value(pageData.text, searchRules.optString("name"), node, pageData.finalUrl)
            val bookUrlRaw = value(pageData.text, searchRules.optString("bookUrl"), node, pageData.finalUrl)
            if (name.isBlank() || bookUrlRaw.isBlank()) return@mapNotNull null
            SourceBookHit(
                sourceUrl = source.optString("bookSourceUrl"),
                name = name,
                author = value(pageData.text, searchRules.optString("author"), node, pageData.finalUrl),
                intro = value(pageData.text, searchRules.optString("intro"), node, pageData.finalUrl),
                coverUrl = absolute(pageData.finalUrl, value(pageData.text, searchRules.optString("coverUrl"), node, pageData.finalUrl)),
                bookUrl = absolute(pageData.finalUrl, bookUrlRaw),
                kind = value(pageData.text, searchRules.optString("kind"), node, pageData.finalUrl),
                lastChapter = value(pageData.text, searchRules.optString("lastChapter"), node, pageData.finalUrl)
            )
        }.distinctBy { it.bookUrl }.take(80)
    }

    fun loadDetail(source: JSONObject, hit: SourceBookHit): SourceBookDetail {
        val page = request(source, hit.bookUrl, emptyMap())
        val rules = ruleObject(source, "ruleBookInfo")
        val ruleTocUrl = value(page.text, rules.optString("tocUrl"), null, page.finalUrl)
        return SourceBookDetail(
            sourceUrl = hit.sourceUrl,
            name = value(page.text, rules.optString("name"), null, page.finalUrl).ifBlank { hit.name },
            author = value(page.text, rules.optString("author"), null, page.finalUrl).ifBlank { hit.author },
            intro = value(page.text, rules.optString("intro"), null, page.finalUrl).ifBlank { hit.intro },
            coverUrl = absolute(page.finalUrl, value(page.text, rules.optString("coverUrl"), null, page.finalUrl).ifBlank { hit.coverUrl }),
            bookUrl = hit.bookUrl,
            tocUrl = absolute(page.finalUrl, ruleTocUrl).ifBlank { hit.bookUrl },
            kind = value(page.text, rules.optString("kind"), null, page.finalUrl).ifBlank { hit.kind },
            lastChapter = value(page.text, rules.optString("lastChapter"), null, page.finalUrl).ifBlank { hit.lastChapter }
        )
    }

    fun loadChapters(source: JSONObject, detail: SourceBookDetail, maxPages: Int = 12): List<SourceChapter> {
        val rules = ruleObject(source, "ruleToc")
        val listRuleOriginal = rules.optString("chapterList")
        require(listRuleOriginal.isNotBlank()) { "该书源没有配置 ruleToc.chapterList" }
        val reverse = listRuleOriginal.startsWith("-")
        val listRule = if (reverse) listRuleOriginal.drop(1) else listRuleOriginal
        val chapters = mutableListOf<SourceChapter>()
        var url = detail.tocUrl
        val visited = mutableSetOf<String>()
        repeat(maxPages) {
            if (url.isBlank() || !visited.add(url)) return@repeat
            val page = request(source, url, emptyMap())
            val nodes = selectNodes(page.text, listRule)
            for (node in nodes) {
                val name = value(page.text, rules.optString("chapterName"), node, page.finalUrl)
                val chapterUrl = value(page.text, rules.optString("chapterUrl"), node, page.finalUrl)
                if (name.isBlank() || chapterUrl.isBlank()) continue
                val vipValue = value(page.text, rules.optString("isVip"), node, page.finalUrl).lowercase(Locale.ROOT)
                chapters += SourceChapter(
                    sourceUrl = detail.sourceUrl,
                    name = name,
                    url = absolute(page.finalUrl, chapterUrl),
                    index = chapters.size,
                    isVip = vipValue !in listOf("", "0", "false", "null", "no")
                )
            }
            val nextRule = rules.optString("nextTocUrl")
            val nextUrl = value(page.text, nextRule, null, page.finalUrl)
            if (nextUrl.isBlank()) return@repeat
            val resolved = absolute(page.finalUrl, nextUrl)
            if (resolved == url || resolved in visited) return@repeat
            url = resolved
        }
        return if (reverse) chapters.asReversed().mapIndexed { index, chapter -> chapter.copy(index = index) } else chapters.mapIndexed { index, chapter -> chapter.copy(index = index) }
    }

    fun loadContent(source: JSONObject, chapter: SourceChapter, maxPages: Int = 10): String {
        val rules = ruleObject(source, "ruleContent")
        val contentRule = rules.optString("content")
        require(contentRule.isNotBlank()) { "该书源没有配置 ruleContent.content" }
        val visited = mutableSetOf<String>()
        val parts = mutableListOf<String>()
        var url = chapter.url
        repeat(maxPages) {
            if (url.isBlank() || !visited.add(url)) return@repeat
            val page = request(source, url, emptyMap())
            val extracted = value(page.text, contentRule, null, page.finalUrl)
            if (extracted.isNotBlank()) parts += normalizeContent(extracted)
            val nextRule = rules.optString("nextContentUrl")
            val next = value(page.text, nextRule, null, page.finalUrl)
            if (next.isBlank()) return@repeat
            val resolved = absolute(page.finalUrl, next)
            if (resolved == url || resolved in visited) return@repeat
            url = resolved
        }
        return parts.filter { it.isNotBlank() }.joinToString("\n\n").trim()
    }

    private fun request(source: JSONObject, rawUrl: String, variables: Map<String, String>): PageData {
        val rendered = renderTemplate(rawUrl, variables, null)
        val spec = parseRequestSpec(rendered, source)
        val resolvedRequestUrl = absolute(source.optString("bookSourceUrl"), spec.url)
        val url = URL(resolvedRequestUrl)
        require(url.protocol.equals("https", true) || url.protocol.equals("http", true)) { "书源请求只支持 HTTP/HTTPS" }
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 20000
            instanceFollowRedirects = true
            requestMethod = spec.method
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/122 Mobile Safari/537.36")
            setRequestProperty("Accept", "text/html,application/json,application/xhtml+xml,*/*")
            setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.7")
            spec.headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }
        try {
            if (spec.method == "POST") {
                connection.doOutput = true
                if (spec.headers.keys.none { it.equals("Content-Type", true) }) {
                    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                }
                connection.outputStream.use { it.write(spec.body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            if (status !in 200..299) error("网站返回 HTTP $status")
            val length = connection.contentLengthLong
            if (length > 12L * 1024 * 1024) error("页面超过 12 MB，已停止读取")
            val bytes = connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > 12 * 1024 * 1024) error("页面超过 12 MB，已停止读取")
                    out.write(buffer, 0, count)
                }
                out.toByteArray()
            }
            val charset = runCatching { Charset.forName(spec.charset) }.getOrDefault(Charsets.UTF_8)
            return PageData(String(bytes, charset), connection.url.toString())
        } finally {
            connection.disconnect()
        }
    }

    private fun parseRequestSpec(raw: String, source: JSONObject): RequestSpec {
        var url = raw.trim()
        var method = "GET"
        var body = ""
        var charset = "UTF-8"
        val headers = linkedMapOf<String, String>()

        val sourceHeader = source.opt("header")
        if (sourceHeader is JSONObject) {
            sourceHeader.keys().forEach { key -> headers[key] = sourceHeader.optString(key) }
        } else if (sourceHeader is String && sourceHeader.isNotBlank()) {
            runCatching { JSONObject(sourceHeader) }.getOrNull()?.let { obj ->
                obj.keys().forEach { key -> headers[key] = obj.optString(key) }
            }
        }

        val optionStart = url.indexOf(",{")
        if (optionStart >= 0) {
            val optionText = url.substring(optionStart + 1).trim()
            url = url.substring(0, optionStart).trim()
            val normalized = optionText
                .replace(Regex("'([A-Za-z][A-Za-z0-9_]*)'\\s*:"), "\"$1\":")
                .replace(Regex(":\\s*'([^']*)'([,}])"), ":\"$1\"$2")
            val options = runCatching { JSONObject(normalized) }.getOrNull()
            if (options != null) {
                method = options.optString("method", "GET").uppercase(Locale.ROOT)
                body = options.optString("body")
                charset = options.optString("charset", "UTF-8")
                options.optJSONObject("headers")?.let { h ->
                    h.keys().forEach { key -> headers[key] = h.optString(key) }
                }
            }
        }
        require(method == "GET" || method == "POST") { "暂不支持书源请求方式：$method" }
        return RequestSpec(url, method, body, charset, headers)
    }

    private fun renderTemplate(template: String, variables: Map<String, String>, node: Any?): String {
        val withVars = template
            .replace("{{key}}", URLEncoder.encode(variables["key"] ?: "", "UTF-8"))
            .replace("{{page}}", variables["page"] ?: "1")
        val regex = Regex("\\{\\{(.*?)\\}\\}")
        return regex.replace(withVars) { match ->
            val expression = match.groupValues[1].trim()
            when {
                expression == "key" -> URLEncoder.encode(variables["key"] ?: "", "UTF-8")
                expression == "page" -> variables["page"] ?: "1"
                expression.startsWith("$.") -> jsonPathValue(node, expression).firstOrNull().orEmpty()
                expression.startsWith("@json:") -> jsonPathValue(node, expression.removePrefix("@json:")).firstOrNull().orEmpty()
                else -> ""
            }
        }
    }

    private fun selectNodes(input: String, rule: String): List<Any> {
        val trimmed = rule.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (trimmed.startsWith("@js:", true) || trimmed.contains("@js:")) {
            throw IllegalArgumentException("该规则包含 JavaScript；当前版本暂不执行 JS 书源脚本")
        }
        if (trimmed.startsWith("@regex:", true) || trimmed.startsWith(":")) {
            val pattern = if (trimmed.startsWith("@regex:", true)) trimmed.substringAfter(":") else trimmed.removePrefix(":")
            return runCatching { Regex(pattern, setOf(RegexOption.DOT_MATCHES_ALL)).findAll(input).map { match -> match.groupValues.getOrElse(1) { match.value } }.toList() }.getOrDefault(emptyList())
        }
        val raw = parseJsonIfPossible(input)
        val expression = trimmed.removePrefix("@json:").trim()
        if (trimmed.startsWith("@json:", true) || expression.startsWith("$.") || expression.startsWith("$[")) {
            return jsonPath(raw ?: return emptyList(), expression)
        }
        val document = Jsoup.parse(input)
        if (trimmed.startsWith("@css:", true)) {
            val selector = trimmed.removePrefix("@css:").substringBeforeLast("@", trimmed.removePrefix("@css:")).trim()
            val valueSuffix = trimmed.substringAfterLast("@", "text").takeIf { it in valueTypes }
            val elements = runCatching { document.select(selector) }.getOrDefault(org.jsoup.select.Elements())
            return if (valueSuffix != null && valueSuffix != "text" && valueSuffix !in listOf("href", "src", "html", "ownText", "textNodes", "all") && valueSuffix.startsWith("attr:")) {
                elements.toList()
            } else elements.toList()
        }
        if (trimmed.startsWith("@xpath:", true) || trimmed.startsWith("//") || trimmed.startsWith("(//")) {
            val xpath = if (trimmed.startsWith("@xpath:", true)) trimmed.substringAfter(":") else trimmed
            return runCatching { xpathExtract(document, xpath) }.getOrDefault(emptyList())
        }
        return defaultSelect(document, trimmed)
    }

    private fun xpathExtract(document: Document, expression: String): List<Any> {
        val attr = Regex("/@([A-Za-z_:][A-Za-z0-9_.:-]*)$").find(expression)
        if (attr != null) {
            val base = expression.substring(0, attr.range.first)
            val name = attr.groupValues[1]
            return runCatching { document.selectXpath(base, Element::class.java).map { it.attr(name) } }.getOrDefault(emptyList())
        }
        if (expression.endsWith("/text()")) {
            val base = expression.removeSuffix("/text()")
            return runCatching { document.selectXpath(base, Element::class.java).flatMap { it.textNodes().map { node -> node.text() } } }.getOrDefault(emptyList())
        }
        return runCatching { document.selectXpath(expression, Element::class.java).toList() }.getOrDefault(emptyList())
    }

    private val valueTypes = setOf("text", "textNodes", "ownText", "href", "src", "html", "all")

    private fun defaultSelect(document: Document, rule: String): List<Any> {
        val parts = rule.split("@").filter { it.isNotBlank() }
        var current: List<Any> = listOf(document)
        for (part in parts) {
            val token = part.trim()
            if (token in valueTypes || token.startsWith("attr:")) break
            current = current.flatMap { node ->
                val element = node as? Element ?: return@flatMap emptyList()
                selectDefaultStep(element, token)
            }
        }
        return current
    }

    private fun selectDefaultStep(parent: Element, token: String): List<Any> {
        val indexMatch = Regex("^(.+?)\\.(-?\\d+)$").matchEntire(token)
        val selectorPart = indexMatch?.groupValues?.get(1) ?: token
        val index = indexMatch?.groupValues?.get(2)?.toIntOrNull()
        val nodes = when {
            selectorPart.startsWith("class.") -> parent.select("." + selectorPart.removePrefix("class."))
            selectorPart.startsWith("id.") -> parent.select("#" + selectorPart.removePrefix("id."))
            selectorPart.startsWith("tag.") -> parent.select(selectorPart.removePrefix("tag."))
            selectorPart == "children" -> parent.children()
            selectorPart.startsWith("children[") -> {
                val indexText = selectorPart.substringAfter("children[").substringBefore("]")
                val childIndex = indexText.toIntOrNull() ?: 0
                val children = parent.children()
                val i = if (childIndex < 0) children.size + childIndex else childIndex
                if (i in 0 until children.size) listOf(children[i]) else emptyList()
            }
            selectorPart.startsWith(".") || selectorPart.startsWith("#") || selectorPart.startsWith("[") -> parent.select(selectorPart)
            else -> parent.select(selectorPart)
        }.toList()
        if (index == null) return nodes
        val actual = if (index < 0) nodes.size + index else index
        return if (actual in nodes.indices) listOf(nodes[actual]) else emptyList()
    }

    private fun value(input: String, rule: String, context: Any?, baseUrl: String): String {
        var r = rule.trim()
        if (r.isBlank()) return ""
        if (r.startsWith("@js:", true) || r.contains("@js:")) {
            throw IllegalArgumentException("该规则包含 JavaScript；当前版本暂不执行 JS 书源脚本")
        }
        val cleanStart = r.indexOf("##")
        val baseRule = if (cleanStart >= 0) r.substring(0, cleanStart).trim() else r
        val selected: List<Any> = when {
            baseRule.startsWith("@text:") -> listOf(baseRule.removePrefix("@text:"))
            baseRule.startsWith("@regex:", true) || baseRule.startsWith(":") -> {
                val pattern = if (baseRule.startsWith("@regex:", true)) baseRule.substringAfter(":") else baseRule.removePrefix(":")
                runCatching { Regex(pattern, setOf(RegexOption.DOT_MATCHES_ALL)).findAll(input).map { it.groupValues.getOrElse(1) { it.value } }.toList() }.getOrDefault(emptyList())
            }
            baseRule.startsWith("@json:", true) || baseRule.startsWith("$.") || baseRule.startsWith("$[") -> {
                val json = (context as? JSONObject) ?: (context as? JSONArray) ?: parseJsonIfPossible(input) ?: return ""
                jsonPath(json, baseRule.removePrefix("@json:"))
            }
            baseRule.startsWith("@css:", true) -> {
                val rest = baseRule.removePrefix("@css:")
                val split = rest.lastIndexOf('@')
                val selector = if (split > 0) rest.substring(0, split) else rest
                val doc = (context as? Document) ?: (context as? Element)?.ownerDocument() ?: Jsoup.parse(input, baseUrl)
                runCatching { doc.select(selector).toList() }.getOrDefault(emptyList())
            }
            baseRule.startsWith("@xpath:", true) || baseRule.startsWith("//") || baseRule.startsWith("(//") -> {
                val xpath = if (baseRule.startsWith("@xpath:", true)) baseRule.substringAfter(":") else baseRule
                val doc = (context as? Document) ?: (context as? Element)?.ownerDocument() ?: Jsoup.parse(input, baseUrl)
                runCatching { xpathExtract(doc, xpath) }.getOrDefault(emptyList())
            }
            else -> {
                if (context is Element) extractDefaultFromContext(context, baseRule)
                else if (context is JSONObject || context is JSONArray) jsonPath(context, baseRule)
                else selectNodes(input, baseRule)
            }
        }
        var suffix = ""
        val marker = r.lastIndexOf("@")
        if (marker >= 0) {
            val tail = r.substring(marker + 1)
            if (tail in valueTypes || tail.startsWith("attr:")) suffix = tail
        }
        var result = selected.map { node -> stringifyNode(node, suffix) }
            .filter { it.isNotBlank() }
        if (r.startsWith("@text:")) result = listOf(r.removePrefix("@text:"))
        var text = result.joinToString("\n")
        if (cleanStart >= 0) {
            val pieces = r.substring(cleanStart + 2).split("##")
            val pattern = pieces.firstOrNull().orEmpty()
            val replacement = pieces.getOrNull(1).orEmpty()
            if (pattern.isNotBlank()) text = runCatching { Regex(pattern, setOf(RegexOption.DOT_MATCHES_ALL)).replace(text, replacement) }.getOrDefault(text)
        }
        return text.trim()
    }

    private fun extractDefaultFromContext(context: Element, rule: String): List<Any> {
        val parts = rule.split("@").filter { it.isNotBlank() }
        var current: List<Any> = listOf(context)
        for (part in parts) {
            val token = part.trim()
            if (token in valueTypes || token.startsWith("attr:")) break
            current = current.flatMap { item ->
                val element = item as? Element ?: return@flatMap emptyList()
                selectDefaultStep(element, token)
            }
        }
        return current
    }

    private fun stringifyNode(node: Any, suffix: String): String {
        return when (node) {
            is Attribute -> node.value
            is TextNode -> node.text()
            is Node -> node.outerHtml()
            is Element -> when (suffix) {
                "href" -> node.absUrl("href").ifBlank { node.attr("href") }
                "src" -> node.absUrl("src").ifBlank { node.attr("src") }
                "html" -> node.html()
                "ownText" -> node.ownText()
                "textNodes" -> node.textNodes().joinToString("") { it.text() }
                "all" -> node.outerHtml()
                else -> if (suffix.startsWith("attr:")) node.attr(suffix.removePrefix("attr:")) else node.text()
            }
            is JSONObject, is JSONArray -> node.toString()
            null -> ""
            else -> node.toString()
        }
    }

    private fun ruleObject(source: JSONObject, key: String): JSONObject {
        source.optJSONObject(key)?.let { return it }
        val raw = source.optString(key).trim()
        return runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
    }

    private fun parseJsonIfPossible(input: String): Any? {
        val trimmed = input.trim()
        return runCatching {
            when {
                trimmed.startsWith("{") -> JSONObject(trimmed)
                trimmed.startsWith("[") -> JSONArray(trimmed)
                else -> null
            }
        }.getOrNull()
    }

    private fun jsonPath(root: Any, expression: String): List<Any> {
        var path = expression.trim().removePrefix("@json:").trim()
        if (path.isBlank()) return listOf(root)
        var values: List<Any> = listOf(root)
        if (path.startsWith("$..")) {
            val key = path.removePrefix("$..").substringBefore(".").substringBefore("[")
            val found = mutableListOf<Any>()
            fun visit(value: Any) {
                when (value) {
                    is JSONObject -> {
                        val keys = value.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            val child = value.opt(k) ?: continue
                            if (k == key) found += child
                            visit(child)
                        }
                    }
                    is JSONArray -> for (i in 0 until value.length()) value.opt(i)?.let(::visit)
                }
            }
            visit(root)
            values = found
            path = path.removePrefix("$..$key")
        } else {
            path = path.removePrefix("$")
        }
        val tokens = Regex("\\.([A-Za-z0-9_\\-$]+)|\\[(\\d+|\\*|'[^']+'|\"[^\"]+\")\\]").findAll(path).toList()
        for (token in tokens) {
            val key = token.groupValues[1]
            val indexToken = token.groupValues[2]
            values = values.flatMap { value ->
                when (value) {
                    is JSONObject -> if (key.isNotBlank()) listOfNotNull(value.opt(key)).filterNot { it === JSONObject.NULL } else if (indexToken.isNotBlank() && indexToken != "*") {
                        val k = indexToken.trim('\'', '"')
                        listOfNotNull(value.opt(k)).filterNot { it === JSONObject.NULL }
                    } else emptyList()
                    is JSONArray -> when {
                        indexToken == "*" -> (0 until value.length()).mapNotNull { value.opt(it) }.filterNot { it === JSONObject.NULL }
                        indexToken.isNotBlank() -> indexToken.toIntOrNull()?.let { listOfNotNull(value.opt(it)).filterNot { item -> item === JSONObject.NULL } }.orEmpty()
                        else -> emptyList()
                    }
                    else -> emptyList()
                }
            }
        }
        return values
    }

    private fun jsonPathValue(node: Any?, expression: String): List<String> {
        val root = node ?: return emptyList()
        return jsonPath(root, expression).map { if (it is JSONObject || it is JSONArray) it.toString() else it.toString() }
    }

    private fun absolute(base: String, target: String): String {
        val value = target.trim()
        if (value.isBlank()) return ""
        if (value.startsWith("http://", true) || value.startsWith("https://", true)) return value
        return runCatching { URI(base).resolve(value).toString() }.getOrDefault(value)
    }

    private fun normalizeContent(raw: String): String {
        if (!raw.contains('<')) return raw.replace("\u00a0", " ").replace(Regex("[ \\t]+\\n"), "\n").trim()
        val doc = Jsoup.parseBodyFragment(raw)
        doc.select("script,style,iframe,ins,button").remove()
        doc.select("br").append("\\n")
        doc.select("p,div").prepend("\\n")
        return doc.body().wholeText()
            .replace("\u00a0", " ")
            .replace(Regex("\\n[ \\t]+"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }
}
