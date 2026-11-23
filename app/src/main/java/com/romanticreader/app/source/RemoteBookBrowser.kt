package com.romanticreader.app.source

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Refresh
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.romanticreader.app.data.BookSourceRepository
import com.romanticreader.app.data.SavedOnlineBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SourceRose = Color(0xFFB76D80)
private val SourceInk = Color(0xFF5B4145)
private val SourceMuted = Color(0xFF9D8583)
private val SourcePaper = Color(0xFFFFF9F5)
private val SourceWhite = Color(0xFFFFFDFB)
private val SourceLine = Color(0xFFEED9D4)

@Composable
fun RemoteBookBrowser(repository: BookSourceRepository, onBack: () -> Unit, onSaved: () -> Unit = {}, initialSavedBook: SavedOnlineBook? = null, initialQuery: String = "", initialAuthorMode: Boolean = false, initialVerifySourceUrl: String = "") {
    val context = LocalContext.current
    val cookieJar = remember { PersistentCookieJar(context) }
    val engine = remember(cookieJar) { LegadoRuleEngine(cookieJar) }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(initialQuery) }
    var authorMode by remember { mutableStateOf(initialAuthorMode) }
    var hits by remember { mutableStateOf(emptyList<SourceBookHit>()) }
    var detail by remember { mutableStateOf<SourceBookDetail?>(null) }
    var chapters by remember { mutableStateOf(emptyList<SourceChapter>()) }
    var currentChapter by remember { mutableStateOf<SourceChapter?>(null) }
    var content by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("选择已启用书源，输入书名开始搜索。") }
    var stage by remember { mutableStateOf("search") }
    var searchPage by remember { mutableIntStateOf(1) }
    var cookieDialog by remember { mutableStateOf(false) }
    var cookieSourceUrl by remember { mutableStateOf("") }
    var cookieText by remember { mutableStateOf("") }
    var debugDialog by remember { mutableStateOf(false) }
    var debugRule by remember { mutableStateOf("@css:.book@text") }
    var debugSample by remember { mutableStateOf("<div class=\"book\">示例小说</div>") }
    var debugOutput by remember { mutableStateOf("输入规则与样例文本后点击测试。") }
    var verifySourceUrl by remember { mutableStateOf(initialVerifySourceUrl) }
    var verifyWebUrl by remember { mutableStateOf("") }
    var verifyDialog by remember { mutableStateOf(false) }
    var verifyStatus by remember { mutableStateOf("准备验证") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(initialQuery, initialAuthorMode) { query = initialQuery; authorMode = initialAuthorMode }
    LaunchedEffect(initialVerifySourceUrl) {
        if (initialVerifySourceUrl.isNotBlank()) {
            verifySourceUrl = initialVerifySourceUrl
            val src = repository.getRawSource(initialVerifySourceUrl, requireEnabled = false)
            verifyWebUrl = src?.optString("loginUrl")?.takeIf { it.isNotBlank() } ?: initialVerifySourceUrl
            verifyDialog = true
        }
    }
    LaunchedEffect(initialSavedBook?.bookUrl) {
        initialSavedBook?.let { saved ->
            detail = SourceBookDetail(
                sourceUrl = saved.sourceUrl, name = saved.title, author = saved.author,
                intro = saved.intro, coverUrl = saved.coverUrl, bookUrl = saved.bookUrl,
                tocUrl = saved.tocUrl, kind = saved.kind, lastChapter = saved.lastChapter
            )
            stage = "chapters"
            message = "这本书已收藏。点击加载目录后继续阅读。"
        }
    }
    fun backStep() {
        when(stage) {
            "content" -> { currentChapter = null; content = ""; stage = "chapters" }
            "chapters" -> { detail = null; chapters = emptyList(); stage = "results" }
            "results" -> { hits = emptyList(); stage = "search" }
            else -> onBack()
        }
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = SourceRose, background = SourcePaper, surface = SourceWhite, onSurface = SourceInk)) {
        Column(Modifier.fillMaxSize().background(SourcePaper).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { backStep() }) { Icon(Icons.Rounded.ArrowBack, "返回", tint = SourceInk) }
                Column(Modifier.weight(1f)) {
                    Text(when(stage) { "content" -> currentChapter?.name ?: "正文"; "chapters" -> detail?.name ?: "目录"; "results" -> "搜索结果"; else -> "在线找书" }, color = SourceInk, fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
                    Text("ROSE LETTER · SOURCE READER", color = SourceMuted, fontSize = 9.sp)
                }
            }
            if (debugDialog) {
                AlertDialog(
                    onDismissRequest = { debugDialog = false },
                    title = { Text("书源规则调试器") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("可测试 CSS、XPath、JSONPath、正则和基础 JS 规则。", fontSize = 12.sp, color = SourceMuted)
                            OutlinedTextField(debugRule, { debugRule = it }, label = { Text("规则") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            OutlinedTextField(debugSample, { debugSample = it }, label = { Text("样例 HTML / JSON / 文本") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
                            OutlinedButton(onClick = { debugOutput = engine.debugRule(debugSample, debugRule) }, modifier = Modifier.fillMaxWidth()) { Text("测试规则") }
                            Text(debugOutput, fontSize = 12.sp, color = SourceInk, lineHeight = 18.sp)
                        }
                    },
                    confirmButton = { TextButton(onClick = { debugDialog = false }) { Text("关闭") } }
                )
            }
            if (verifyDialog) {
                val source = repository.getRawSource(verifySourceUrl, requireEnabled = false)
                val startUrl = verifyWebUrl.ifBlank { source?.optString("loginUrl")?.takeIf { it.isNotBlank() } ?: verifySourceUrl }
                AlertDialog(
                    onDismissRequest = { verifyDialog = false; webViewRef = null },
                    title = { Text("书源验证 · ${repository.getSources().firstOrNull { it.url == verifySourceUrl }?.name ?: verifySourceUrl}") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("在此网页中手动完成登录或人机验证。不会自动绕过验证码。完成后点击“同步并检测”。", color = SourceMuted, fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { webViewRef?.reload() }) { Icon(Icons.Rounded.Refresh, null); Text("刷新") }
                                OutlinedButton(onClick = { webViewRef?.goBack() }, enabled = webViewRef?.canGoBack() == true) { Text("返回") }
                                OutlinedButton(onClick = { webViewRef?.goForward() }, enabled = webViewRef?.canGoForward() == true) { Text("前进") }
                            }
                            AndroidView(
                                modifier = Modifier.fillMaxWidth().height(390.dp),
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        settings.javaScriptEnabled = true
                                        settings.domStorageEnabled = true
                                        settings.loadsImagesAutomatically = true
                                        settings.userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36"
                                        CookieManager.getInstance().setAcceptCookie(true)
                                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                        webViewClient = object : WebViewClient() {
                                            override fun onPageFinished(view: WebView, url: String) {
                                                verifyWebUrl = url
                                                verifyStatus = "网页已加载，请完成页面要求的操作"
                                            }
                                        }
                                        webViewRef = this
                                        loadUrl(startUrl)
                                    }
                                },
                                update = { view -> if (view.url.isNullOrBlank()) view.loadUrl(startUrl) }
                            )
                            Text(verifyStatus, color = SourceMuted, fontSize = 11.sp)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val finalUrl = verifyWebUrl.ifBlank { startUrl }
                            val cookieManager = CookieManager.getInstance()
                            cookieManager.flush()
                            val cookieHeader = cookieManager.getCookie(finalUrl).orEmpty()
                            cookieJar.importCookieManagerCookies(finalUrl, cookieHeader)
                            verifyStatus = if (cookieHeader.isNotBlank()) "已同步网页 Cookie，正在检测书源…" else "未获取到 Cookie，仍将尝试检测…"
                            scope.launch {
                                loading = true
                                val result = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val json = repository.getRawSource(verifySourceUrl, requireEnabled = false) ?: error("找不到书源")
                                        engine.search(json, "测试", 1)
                                    }
                                }
                                result.onSuccess { found ->
                                    val status = if (found.isNotEmpty()) "正常" else "可访问但无结果"
                                    val msg = if (found.isNotEmpty()) "验证成功：测试搜索返回 ${found.size} 条结果" else "请求成功但规则未解析出书籍；请检查 searchUrl / ruleSearch"
                                    repository.saveVerificationRecord(verifySourceUrl, status, msg)
                                    verifyStatus = msg
                                }.onFailure { error ->
                                    val text = error.message ?: error.javaClass.simpleName
                                    val status = when {
                                        text.contains("captcha", true) || text.contains("verify", true) || text.contains("human", true) || text.contains("challenge", true) -> "需要验证"
                                        text.contains("401") || text.contains("403") || text.contains("login", true) || text.contains("登录", true) -> "需要登录"
                                        text.contains("timeout", true) || text.contains("timed out", true) -> "连接超时"
                                        else -> "检测失败"
                                    }
                                    repository.saveVerificationRecord(verifySourceUrl, status, text)
                                    verifyStatus = "$status：$text"
                                }
                                loading = false
                                onSaved()
                            }
                        }) { Text("同步并检测") }
                    },
                    dismissButton = {
                        TextButton(onClick = { verifyDialog = false; webViewRef = null }) { Text("关闭") }
                    }
                )
            }
            if (cookieDialog) {
                val enabledSources = repository.getSources().filter { it.enabled }
                AlertDialog(
                    onDismissRequest = { cookieDialog = false },
                    title = { Text("书源登录态 / Cookie") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Cookie 会保存在本机。验证页面完成登录/人机验证后可同步；手动粘贴仍可用。", fontSize = 12.sp, color = SourceMuted)
                            if (cookieSourceUrl.isBlank() && enabledSources.isNotEmpty()) cookieSourceUrl = enabledSources.first().url
                            enabledSources.forEach { source ->
                                Row(Modifier.fillMaxWidth().clickable { cookieSourceUrl = source.url }, verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = cookieSourceUrl == source.url, onClick = { cookieSourceUrl = source.url })
                                    Text(source.name, fontSize = 12.sp)
                                }
                            }
                            OutlinedTextField(cookieText, { cookieText = it }, label = { Text("Cookie 字符串") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                        }
                    },
                    confirmButton = { TextButton(onClick = {
                        cookieSourceUrl.takeIf { it.isNotBlank() }?.let { cookieJar.setManualCookieHeader(it, cookieText) }
                        cookieDialog = false
                        message = "Cookie 已保存到本机"
                    }) { Text("保存") } },
                    dismissButton = { TextButton(onClick = {
                        cookieSourceUrl.takeIf { it.isNotBlank() }?.let { cookieJar.clearForUrl(it) }
                        cookieText = ""
                        message = "已清除所选书源的 Cookie"
                    }) { Text("清除") } }
                )
            }
            when(stage) {
                "search" -> {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(selected = !authorMode, onClick = { authorMode = false }, label = { Text("书名") }); FilterChip(selected = authorMode, onClick = { authorMode = true }, label = { Text("作者") }) }
                        OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(if (authorMode) "作者名" else "小说名") }, trailingIcon = { Icon(Icons.Rounded.Search, null) })
                        OutlinedButton(onClick = { cookieDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("登录态 / Cookie 管理") }
                        OutlinedButton(onClick = {
                            val src = repository.getSources().firstOrNull { it.enabled }
                            if (src != null) { verifySourceUrl = src.url; verifyWebUrl = src.url; verifyDialog = true }
                            else message = "请先添加并启用一个书源"
                        }, modifier = Modifier.fillMaxWidth()) { Text("内置网页验证 / 同步 Cookie") }
                        OutlinedButton(onClick = { debugDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("书源规则调试器") }
                        Button(onClick = {
                            if (query.isBlank()) { message = if (authorMode) "先输入作者名哦" else "先输入小说名哦"; return@Button }
                            scope.launch {
                                loading = true; hits = emptyList(); searchPage = 1; message = "正在搜索已启用的书源…"
                                val result = withContext(Dispatchers.IO) {
                                    val list = mutableListOf<SourceBookHit>()
                                    val errors = mutableListOf<String>()
                                    val enabledSources = repository.getSources().filter { it.enabled }
                                    enabledSources.forEach { src ->
                                        val json = repository.getRawSource(src.url)
                                        if (json == null) errors += src.name + ": 书源数据读取失败"
                                        else runCatching { engine.search(json, query.trim(), 1) }
                                            .onSuccess { list += it }
                                            .onFailure { errors += src.name + ": " + (it.message ?: it.javaClass.simpleName) }
                                    }
                                    Pair(list, errors)
                                }
                                val rawHits = result.first.distinctBy { it.bookUrl }
                                hits = if (authorMode) rawHits.filter { it.author.contains(query.trim(), ignoreCase = true) } else rawHits
                                message = if (hits.isEmpty()) {
                                    val diagnostic = result.second.take(3).joinToString("；")
                                    "没有搜索结果。已查询 " + repository.getSources().count { it.enabled } + " 个启用书源，返回 0 本。" +
                                        if (diagnostic.isNotBlank()) "失败详情：" + diagnostic else "书源请求成功但解析不到书籍，请检查 searchUrl/ruleSearch。"
                                } else "找到 " + hits.size + " 个结果（" + result.second.size + " 个书源失败），点开查看详情与目录。" +
                                    if (result.second.isNotEmpty()) "失败书源：" + result.second.take(2).joinToString("；") else ""
                                loading = false
                                stage = "results"
                            }
                        }, enabled = !loading, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = SourceRose)) { Text(if (loading) "搜索中…" else "搜索小说") }
                        Text(message, color = SourceMuted, fontSize = 12.sp, lineHeight = 18.sp)
                        Text("当前支持 CSS、XPath、JSONPath、正则与基础 JavaScript 规则；JS 不开放 Java/系统访问，复杂 Legado 脚本仍可能不兼容。", color = SourceMuted, fontSize = 11.sp, lineHeight = 17.sp)
                    }
                }
                "results" -> {
                    Text(message, color = SourceMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
                    LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        item {
                            OutlinedButton(onClick = {
                                if (loading) return@OutlinedButton
                                val next = searchPage + 1
                                scope.launch {
                                    loading = true
                                    val more = withContext(Dispatchers.IO) {
                                        val list = mutableListOf<SourceBookHit>()
                                        val errors = mutableListOf<String>()
                                        repository.getSources().filter { it.enabled }.forEach { src ->
                                            val json = repository.getRawSource(src.url)
                                            if (json == null) errors += src.name + ": 读取失败"
                                            else runCatching { engine.search(json, query.trim(), next) }
                                                .onSuccess { list += it }
                                                .onFailure { errors += src.name + ": " + (it.message ?: "规则错误") }
                                        }
                                        Pair(list.distinctBy { it.bookUrl }, errors)
                                    }
                                    hits = (hits + (if (authorMode) more.first.filter { it.author.contains(query.trim(), ignoreCase = true) } else more.first)).distinctBy { it.bookUrl }
                                    searchPage = next
                                    message = if (more.first.isEmpty()) "第 " + next + " 页没有新结果。 " + more.second.take(2).joinToString("；") else "已加载第 " + next + " 页，新增 " + more.first.size + " 个结果" + (if (more.second.isNotEmpty()) "；部分书源失败：" + more.second.take(2).joinToString("；") else "")
                                    loading = false
                                }
                            }, modifier = Modifier.fillMaxWidth(), enabled = !loading) { Text("加载下一页（第 " + (searchPage + 1) + " 页）") }
                        }
                        items(hits) { hit ->
                            Surface(shape = RoundedCornerShape(18.dp), color = SourceWhite, border = androidx.compose.foundation.BorderStroke(1.dp, SourceLine), modifier = Modifier.fillMaxWidth().clickable {
                                scope.launch {
                                    loading = true
                                    val loaded = withContext(Dispatchers.IO) { runCatching {
                                        val src = repository.getRawSource(hit.sourceUrl) ?: error("书源已停用")
                                        engine.loadDetail(src, hit)
                                    } }
                                    loaded.onSuccess { detail = it; stage = "chapters" }.onFailure { message = "详情加载失败：${it.message ?: "规则不兼容"}" }
                                    loading = false
                                }
                            }) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(hit.name, color = SourceInk, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                    Text("${hit.author.ifBlank { "作者未知" }} · ${repository.getSources().firstOrNull { it.url == hit.sourceUrl }?.name ?: "书源"}", color = SourceMuted, fontSize = 11.sp)
                                    if (hit.lastChapter.isNotBlank()) Text("最新：${hit.lastChapter}", color = SourceMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (hit.intro.isNotBlank()) Text(hit.intro, color = SourceInk, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                "chapters" -> {
                    detail?.let { book ->
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(book.name, color = SourceInk, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Text("${book.author} · ${book.kind}", color = SourceMuted, fontSize = 12.sp)
                            OutlinedButton(onClick = {
                                val saved = repository.saveOnlineBook(SavedOnlineBook(
                                    sourceUrl = book.sourceUrl, bookUrl = book.bookUrl, title = book.name,
                                    author = book.author, intro = book.intro, coverUrl = book.coverUrl,
                                    tocUrl = book.tocUrl, kind = book.kind, lastChapter = book.lastChapter
                                ))
                                message = if (saved) "已收藏到书架 🌹" else "这本书已经在书架啦"
                                onSaved()
                            }, modifier = Modifier.fillMaxWidth()) {
                                Text(if (repository.isOnlineBookSaved(book.sourceUrl, book.bookUrl)) "已收藏" else "收藏到书架")
                            }
                            if (book.intro.isNotBlank()) Text(book.intro, color = SourceInk, fontSize = 12.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                            Button(onClick = {
                                scope.launch {
                                    loading = true
                                    val result = withContext(Dispatchers.IO) { runCatching {
                                        val src = repository.getRawSource(book.sourceUrl) ?: error("书源已停用")
                                        engine.loadChapters(src, book)
                                    } }
                                    result.onSuccess { chapters = it; message = "共 ${it.size} 章" }.onFailure { message = "目录加载失败：${it.message ?: "规则不兼容"}" }
                                    loading = false
                                }
                            }, enabled = !loading, colors = ButtonDefaults.buttonColors(containerColor = SourceRose)) { Text(if (loading) "加载目录中…" else if (chapters.isEmpty()) "加载目录" else "重新加载目录") }
                            Text(message, color = SourceMuted, fontSize = 11.sp)
                        }
                        LazyColumn(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                            items(chapters) { chapter ->
                                Row(Modifier.fillMaxWidth().clickable {
                                    scope.launch {
                                        loading = true
                                        currentChapter = chapter
                                        val result = withContext(Dispatchers.IO) { runCatching {
                                            val src = repository.getRawSource(chapter.sourceUrl) ?: error("书源已停用")
                                            engine.loadContent(src, chapter)
                                        } }
                                        result.onSuccess { content = it; stage = "content" }.onFailure { message = "正文加载失败：${it.message ?: "规则不兼容"}" }
                                        loading = false
                                    }
                                }.padding(vertical = 12.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.MenuBook, null, tint = SourceRose, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(9.dp))
                                    Text(chapter.name, color = SourceInk, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                    if (chapter.isVip) Text("VIP", color = SourceMuted, fontSize = 9.sp)
                                }
                                HorizontalDivider(color = SourceLine)
                            }
                        }
                    }
                }
                "content" -> {
                    Column(Modifier.fillMaxSize()) {
                        Text(detail?.name ?: "", color = SourceMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
                        Text(currentChapter?.name ?: "", color = SourceInk, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            item { Text(content.ifBlank { "正文内容为空，可能规则未匹配或站点要求登录。" }, color = SourceInk, fontSize = 18.sp, lineHeight = 32.sp) }
                        }
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = {
                                val idx = chapters.indexOf(currentChapter)
                                if (idx > 0) {
                                    val prev = chapters[idx - 1]
                                    scope.launch {
                                        loading = true; currentChapter = prev
                                        content = withContext(Dispatchers.IO) {
                                            val src = repository.getRawSource(prev.sourceUrl) ?: error("书源已停用")
                                            engine.loadContent(src, prev)
                                        }
                                        loading = false
                                    }
                                }
                            }) { Text("上一章", color = SourceRose) }
                            TextButton(onClick = {
                                val idx = chapters.indexOf(currentChapter)
                                if (idx >= 0 && idx < chapters.lastIndex) {
                                    val next = chapters[idx + 1]
                                    scope.launch {
                                        loading = true; currentChapter = next
                                        content = withContext(Dispatchers.IO) {
                                            val src = repository.getRawSource(next.sourceUrl) ?: error("书源已停用")
                                            engine.loadContent(src, next)
                                        }
                                        loading = false
                                    }
                                }
                            }) { Text("下一章", color = SourceRose) }
                        }
                    }
                }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = SourceRose)
        }
    }
}
