package com.romanticreader.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.romanticreader.app.data.BookSourceRepository
import com.romanticreader.app.data.Novel
import com.romanticreader.app.data.NovelRepository
import com.romanticreader.app.source.RemoteBookBrowser
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val RosePaper = Color(0xFFFFF9F5)
private val RoseWhite = Color(0xFFFFFDFB)
private val RoseInk = Color(0xFF5B4145)
private val RoseMuted = Color(0xFF9D8583)
private val RosePink = Color(0xFFD28E9C)
private val RoseDeep = Color(0xFFB76D80)
private val RoseBlush = Color(0xFFF8E6E5)
private val RoseLine = Color(0xFFEED9D4)
private val Champagne = Color(0xFFB59B79)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RoseLetterApp(this) }
    }
}

private enum class AppTab(val label: String) { Home("首页"), Shelf("书架"), Discover("发现"), Me("我的") }

@Composable
private fun RoseLetterApp(context: Context) {
    val library = remember { NovelRepository(context) }
    val sources = remember { BookSourceRepository(context) }
    var tab by remember { mutableStateOf(AppTab.Home) }
    var books by remember { mutableStateOf(library.getBooks()) }
    var onlineBooks by remember { mutableStateOf(sources.getOnlineBooks()) }
    var sourceList by remember { mutableStateOf(sources.getSources()) }
    var subscriptions by remember { mutableStateOf(sources.getSubscriptions()) }
    var updateStatus by remember { mutableStateOf("尚未检查") }
    var pendingUpdate by remember { mutableStateOf<com.romanticreader.app.AppUpdateInfo?>(null) }
    val sourceScope = rememberCoroutineScope()
    var activeBook by remember { mutableStateOf<Novel?>(null) }
    var readerText by remember { mutableStateOf("") }
    var toast by remember { mutableStateOf("") }
    var sourcePage by remember { mutableStateOf(false) }
    var remoteReaderPage by remember { mutableStateOf(false) }
    var selectedOnlineBook by remember { mutableStateOf<com.romanticreader.app.data.SavedOnlineBook?>(null) }
    var discoveryQuery by remember { mutableStateOf("") }
    var discoveryAuthorMode by remember { mutableStateOf(false) }
    var settingsPage by remember { mutableStateOf(false) }

    val importTxt = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val imported = library.importNovel(it)
            books = library.getBooks()
            toast = if (imported != null) "《${imported.title}》已收藏进书架" else "导入失败，请检查文件"
        }
    }
    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val content = runCatching { context.contentResolver.openInputStream(it)?.bufferedReader(Charsets.UTF_8)?.use { r -> r.readText() } }.getOrNull()
            if (content != null) {
                val count = runCatching { sources.importJson(content) }.getOrElse { -1 }
                sourceList = sources.getSources()
                toast = if (count >= 0) "成功导入 $count 个书源" else "书源文件格式无法识别"
            } else toast = "无法读取书源文件"
        }
    }

    MaterialTheme(colorScheme = lightColorScheme(
        primary = RoseDeep, onPrimary = Color.White, secondary = Champagne,
        background = RosePaper, surface = RoseWhite, onSurface = RoseInk,
        surfaceVariant = RoseBlush, onSurfaceVariant = RoseMuted
    )) {
        if (activeBook != null) {
            RoseReader(
                book = activeBook!!,
                text = readerText,
                initialParagraph = activeBook!!.progressChapter,
                onBack = { activeBook = null; books = library.getBooks() },
                onProgress = { library.updateProgress(activeBook!!.id, it) }
            )
        } else {
            Scaffold(
                containerColor = RosePaper,
                bottomBar = {
                    NavigationBar(containerColor = RoseWhite, tonalElevation = 0.dp, modifier = Modifier.height(72.dp)) {
                        AppTab.entries.forEach { item ->
                            NavigationBarItem(
                                selected = tab == item && !sourcePage && !settingsPage,
                                onClick = { tab = item; sourcePage = false; settingsPage = false },
                                icon = {
                                    Icon(
                                        when (item) {
                                            AppTab.Home -> Icons.Rounded.Home
                                            AppTab.Shelf -> Icons.Rounded.LocalLibrary
                                            AppTab.Discover -> Icons.Rounded.Explore
                                            AppTab.Me -> Icons.Rounded.Person
                                        }, null, modifier = Modifier.size(22.dp)
                                    )
                                },
                                label = { Text(item.label, fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(indicatorColor = RoseBlush, selectedIconColor = RoseDeep, selectedTextColor = RoseDeep)
                            )
                        }
                    }
                }
            ) { padding ->
                when {
                    sourcePage -> SourcesScreen(Modifier.padding(padding), sourceList, subscriptions, onImport = { importJson.launch(arrayOf("application/json", "text/*")) }, onAddSubscription = { url ->
                        sourceScope.launch {
                            toast = "正在导入远程书源…"
                            val result = withContext(Dispatchers.IO) { runCatching { sources.addSubscription(url) } }
                            sourceList = sources.getSources(); subscriptions = sources.getSubscriptions()
                            toast = result.fold({ "订阅导入完成，新增或更新 $it 个书源" }, { "订阅失败：${it.message ?: "网络错误"}" })
                        }
                    }, onRefreshSubscription = { url ->
                        sourceScope.launch {
                            toast = "正在刷新订阅…"
                            val result = withContext(Dispatchers.IO) { runCatching { sources.syncSubscription(url) } }
                            sourceList = sources.getSources(); subscriptions = sources.getSubscriptions()
                            toast = result.fold({ "刷新完成，更新 $it 个书源" }, { "刷新失败：${it.message ?: "网络错误"}" })
                        }
                    }, onRemoveSubscription = { url -> sources.removeSubscription(url); subscriptions = sources.getSubscriptions() }, onToggle = { s, enabled -> sources.setEnabled(s.url, enabled); sourceList = sources.getSources() }, onDelete = { s -> sources.delete(s.url); sourceList = sources.getSources() }, onBack = { sourcePage = false })
                    settingsPage -> SettingsScreen(Modifier.padding(padding), books.size, sourceList.size, updateStatus, onSources = { sourcePage = true }, onCheckUpdate = { sourceScope.launch { updateStatus = "正在检查更新…"; val result = withContext(Dispatchers.IO) { runCatching { AppUpdater.check(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toLong()) } }; pendingUpdate = result.getOrNull(); updateStatus = result.fold({ if (it == null) "当前已是最新版本" else "发现新版本 ${it.version}" }, { "检查失败：${it.message}" }) } }, onBack = { settingsPage = false })
                    remoteReaderPage -> RemoteBookBrowser(sources, onBack = { remoteReaderPage = false; selectedOnlineBook = null }, onSaved = { onlineBooks = sources.getOnlineBooks() }, initialSavedBook = selectedOnlineBook, initialQuery = discoveryQuery, initialAuthorMode = discoveryAuthorMode)
                    tab == AppTab.Home -> HomeScreen(Modifier.padding(padding), books, onImport = { importTxt.launch(arrayOf("text/plain", "text/*")) }, onOpen = { book -> readerText = library.readText(book); activeBook = book }, onShelf = { tab = AppTab.Shelf }, onSources = { sourcePage = true })
                    tab == AppTab.Shelf -> ShelfScreen(Modifier.padding(padding), books, onlineBooks, onImport = { importTxt.launch(arrayOf("text/plain", "text/*")) }, onOpen = { book -> readerText = library.readText(book); activeBook = book }, onDelete = { book -> library.deleteBook(book.id); books = library.getBooks() }, onRemoveOnline = { book -> sources.removeOnlineBook(book.sourceUrl, book.bookUrl); onlineBooks = sources.getOnlineBooks() }, onOpenOnline = { book -> selectedOnlineBook = book; remoteReaderPage = true })
                    tab == AppTab.Discover -> DiscoverScreen(Modifier.padding(padding), onSources = { sourcePage = true }, onImport = { importJson.launch(arrayOf("application/json", "text/*")) }, onRemoteSearch = { q, author -> discoveryQuery = q; discoveryAuthorMode = author; remoteReaderPage = true })
                    else -> SettingsScreen(Modifier.padding(padding), books.size, sourceList.size, updateStatus, onSources = { sourcePage = true }, onCheckUpdate = { sourceScope.launch { updateStatus = "正在检查更新…"; val result = withContext(Dispatchers.IO) { runCatching { AppUpdater.check(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toLong()) } }; pendingUpdate = result.getOrNull(); updateStatus = result.fold({ if (it == null) "当前已是最新版本" else "发现新版本 ${it.version}" }, { "检查失败：${it.message}" }) } }, onBack = {})
                }
            }
            if (pendingUpdate != null) {
                AlertDialog(
                    onDismissRequest = { pendingUpdate = null },
                    title = { Text("发现更新 · ${pendingUpdate!!.version}") },
                    text = { Text(pendingUpdate!!.notes.ifBlank { "新版本已发布，建议更新。" }) },
                    confirmButton = {
                        TextButton(onClick = {
                            val info = pendingUpdate!!
                            pendingUpdate = null
                            sourceScope.launch {
                                updateStatus = "正在下载更新…"
                                val result = withContext(Dispatchers.IO) { runCatching { AppUpdater.downloadAndInstall(context, info) } }
                                updateStatus = result.fold({ "下载完成，等待安装确认" }, { "更新失败：${it.message}" })
                            }
                        }) { Text("下载并安装") }
                    },
                    dismissButton = { TextButton(onClick = { pendingUpdate = null }) { Text("稍后") } }
                )
            }
            if (toast.isNotBlank()) {
                LaunchedEffect(toast) { kotlinx.coroutines.delay(2400); toast = "" }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    Surface(color = RoseInk, shape = RoundedCornerShape(24.dp), modifier = Modifier.padding(bottom = 92.dp)) {
                        Text(toast, color = Color.White, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun RoseHeader(title: String, subtitle: String? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 29.sp, fontWeight = FontWeight.SemiBold, color = RoseInk, fontFamily = FontFamily.Serif)
            subtitle?.let { Text(it, fontSize = 13.sp, color = RoseMuted, modifier = Modifier.padding(top = 3.dp)) }
        }
        trailing?.invoke()
    }
}

@Composable
private fun RoseCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(22.dp), color = RoseWhite, border = androidx.compose.foundation.BorderStroke(1.dp, RoseLine.copy(alpha = .65f)), shadowElevation = 1.dp) {
        Column(content = content)
    }
}

@Composable
private fun HomeScreen(modifier: Modifier, books: List<Novel>, onImport: () -> Unit, onOpen: (Novel) -> Unit, onShelf: () -> Unit, onSources: () -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            RoseHeader("拾阅 ✧", "在文字里，遇见更大的世界。", trailing = {
                IconButton(onClick = onImport) { Icon(Icons.Rounded.Search, "导入 / 搜索", tint = RoseDeep) }
            })
        }
        item {
            RoseCard(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFFFFE9E8), Color(0xFFF8F0E9), Color(0xFFEDE5F4)))).padding(20.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("A LITTLE READING GARDEN", fontSize = 10.sp, letterSpacing = 2.sp, color = Champagne)
                        Text("把喜欢的故事\n收进自己的花园", fontSize = 24.sp, lineHeight = 32.sp, color = RoseInk, fontWeight = FontWeight.Medium)
                        Text("慢一点，读一段属于自己的时光。", fontSize = 12.sp, color = RoseMuted)
                        Button(onClick = onImport, colors = ButtonDefaults.buttonColors(containerColor = RoseDeep), shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(5.dp)); Text("导入一本书")
                        }
                    }
                    Icon(Icons.Rounded.LocalFlorist, null, tint = Color(0xFFD9A4AB).copy(alpha = .24f), modifier = Modifier.align(Alignment.BottomEnd).size(90.dp))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                HomeQuick("我的书架", Icons.Rounded.LocalLibrary, books.size.toString(), Modifier.weight(1f), onShelf)
                HomeQuick("书源管理", Icons.Rounded.Explore, "JSON", Modifier.weight(1f), onSources)
                HomeQuick("阅读记录", Icons.Rounded.History, "继续", Modifier.weight(1f), { books.firstOrNull()?.let(onOpen) })
            }
        }
        item { SectionTitle("继续阅读", "CONTINUE", onShelf) }
        if (books.isEmpty()) item { EmptyBooksCard(onImport) }
        else items(books.take(3), key = { it.id }) { book ->
            BookRow(book, onClick = { onOpen(book) }, trailing = {
                Text("继续阅读 ›", fontSize = 12.sp, color = RoseDeep)
            })
        }
        item { SectionTitle("为你留白", "A QUIET MOMENT") }
        item {
            RoseCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = Champagne, modifier = Modifier.size(28.dp))
                    Text("今日阅读，随心而始", color = RoseInk, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    Text("不必赶路，让故事陪你慢慢走。", color = RoseMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun HomeQuick(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, detail: String, modifier: Modifier, onClick: () -> Unit) {
    RoseCard(modifier.clickable(onClick = onClick)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, null, tint = RoseDeep, modifier = Modifier.size(24.dp))
            Text(title, color = RoseInk, fontSize = 12.sp)
            Text(detail, color = RoseMuted, fontSize = 10.sp)
        }
    }
}
@Composable
private fun ShelfScreen(modifier: Modifier, books: List<Novel>, onlineBooks: List<com.romanticreader.app.data.SavedOnlineBook>, onImport: () -> Unit, onOpen: (Novel) -> Unit, onDelete: (Novel) -> Unit, onRemoveOnline: (com.romanticreader.app.data.SavedOnlineBook) -> Unit, onOpenOnline: (com.romanticreader.app.data.SavedOnlineBook) -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (onlineBooks.isNotEmpty()) {
            item { SectionTitle("在线收藏", "SAVED FROM SOURCES") }
            items(onlineBooks, key = { "online_" + it.sourceUrl + it.bookUrl }) { online ->
                RoseCard(Modifier.fillMaxWidth().clickable { onOpenOnline(online) }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(58.dp, 78.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(Color(0xFFEED7DF), Color(0xFFF8EDE5)))), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.MenuBook, null, tint = RoseDeep.copy(alpha = .65f), modifier = Modifier.size(30.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(online.title, fontSize = 15.sp, color = RoseInk, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(online.author.ifBlank { "作者未知" }, fontSize = 11.sp, color = RoseMuted)
                            Text("在线书源 · " + online.kind, fontSize = 10.sp, color = RoseDeep)
                            if (online.lastChapter.isNotBlank()) Text("最新：" + online.lastChapter, fontSize = 10.sp, color = RoseMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { onRemoveOnline(online) }) { Icon(Icons.Rounded.DeleteOutline, "取消收藏", tint = RoseMuted) }
                    }
                }
            }
        }
        item { RoseHeader("我的书架", "MY LITTLE LIBRARY", trailing = { IconButton(onClick = onImport) { Icon(Icons.Rounded.Add, "添加书籍", tint = RoseDeep) } }) }
        item {
            RoseCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("藏书", fontSize = 13.sp, color = RoseMuted)
                        Text("${books.size} 本", fontSize = 25.sp, color = RoseInk, fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(onClick = onImport) { Icon(Icons.Rounded.Add, null); Text("导入 TXT") }
                }
            }
        }
        if (books.isEmpty() && onlineBooks.isEmpty()) item { EmptyBooksCard(onImport) }
        items(books, key = { it.id }) { book -> BookRow(book, onClick = { onOpen(book) }, trailing = { IconButton(onClick = { onDelete(book) }) { Icon(Icons.Rounded.DeleteOutline, "删除", tint = RoseMuted) } }) }
    }
}

@Composable
private fun BookRow(book: Novel, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    RoseCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(58.dp, 78.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(Color(0xFFEED7DF), Color(0xFFF8EDE5)))), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.MenuBook, null, tint = RoseDeep.copy(alpha = .65f), modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(book.title, fontSize = 15.sp, color = RoseInk, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("TXT · ${(book.size / 1024).coerceAtLeast(1)} KB", fontSize = 11.sp, color = RoseMuted)
                Text(if (book.progressChapter > 0) "已读到第 ${book.progressChapter + 1} 段" else "尚未开始", fontSize = 11.sp, color = RoseDeep)
            }
            trailing()
        }
    }
}

@Composable
private fun EmptyBooksCard(onImport: () -> Unit) {
    RoseCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Book, null, tint = RoseDeep, modifier = Modifier.size(38.dp))
            Text("书架还在等待第一本故事", color = RoseInk, fontWeight = FontWeight.Medium)
            Text("把喜欢的 TXT 小说放进来吧。", color = RoseMuted, fontSize = 12.sp)
            Button(onClick = onImport, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = RoseDeep)) { Text("选择 TXT 文件") }
        }
    }
}

@Composable
private fun SectionTitle(title: String, english: String, action: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 19.sp, color = RoseInk, fontWeight = FontWeight.SemiBold)
            Text(english, fontSize = 9.sp, letterSpacing = 1.8.sp, color = Champagne)
        }
        action?.let { TextButton(onClick = it) { Text("更多 ›", color = RoseMuted, fontSize = 12.sp) } }
    }
}

@Composable
private fun DiscoverScreen(modifier: Modifier, onSources: () -> Unit, onImport: () -> Unit, onRemoteSearch: (String, Boolean) -> Unit) {
    var searchText by remember { mutableStateOf("") }
    var authorMode by remember { mutableStateOf(false) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { RoseHeader("发现", "DISCOVER YOUR NEXT STORY") }
        item {
            RoseCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("在线搜书", fontSize = 22.sp, color = RoseInk, fontWeight = FontWeight.Medium)
                    Text("跨已启用书源查找小说，也可以按作者查作品。", color = RoseMuted, fontSize = 13.sp, lineHeight = 21.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !authorMode, onClick = { authorMode = false }, label = { Text("书名搜索") })
                        FilterChip(selected = authorMode, onClick = { authorMode = true }, label = { Text("作者搜索") })
                    }
                    OutlinedTextField(searchText, { searchText = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(if (authorMode) "输入作者名" else "输入小说名") }, trailingIcon = { Icon(Icons.Rounded.Search, null) })
                    Button(onClick = { if (searchText.isNotBlank()) onRemoteSearch(searchText.trim(), authorMode) }, enabled = searchText.isNotBlank(), modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = RoseDeep)) {
                        Icon(Icons.Rounded.Search, null); Spacer(Modifier.width(6.dp)); Text(if (authorMode) "搜索该作者作品" else "搜索小说")
                    }
                }
            }
        }
        item {
            RoseCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("书源花园", fontSize = 20.sp, color = RoseInk, fontWeight = FontWeight.Medium)
                    Text("管理书源、添加订阅，或导入 JSON 文件。", color = RoseMuted, fontSize = 13.sp, lineHeight = 21.sp)
                    Button(onClick = onSources, colors = ButtonDefaults.buttonColors(containerColor = RoseDeep)) { Text("管理书源") }
                    OutlinedButton(onClick = onImport) { Icon(Icons.Rounded.FileOpen, null); Spacer(Modifier.width(6.dp)); Text("导入书源 JSON") }
                }
            }
        }
        item {
            RoseCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Legado JSON", fontWeight = FontWeight.SemiBold, color = RoseInk)
                    Text("兼容常见书源 JSON 数组、单源对象及嵌套订阅结构。", fontSize = 12.sp, color = RoseMuted, lineHeight = 19.sp)
                    OutlinedButton(onClick = onImport) { Icon(Icons.Rounded.FileOpen, null); Spacer(Modifier.width(6.dp)); Text("选择 JSON 文件") }
                }
            }
        }
    }
}

@Composable
private fun SourcesScreen(modifier: Modifier, sources: List<com.romanticreader.app.data.BookSource>, subscriptions: List<com.romanticreader.app.data.BookSourceSubscription>, onImport: () -> Unit, onAddSubscription: (String) -> Unit, onRefreshSubscription: (String) -> Unit, onRemoveSubscription: (String) -> Unit, onToggle: (com.romanticreader.app.data.BookSource, Boolean) -> Unit, onDelete: (com.romanticreader.app.data.BookSource) -> Unit, onBack: () -> Unit) {
    var subscriptionUrl by remember { mutableStateOf("") }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { RoseHeader("书源管理", "SOURCE GARDEN", trailing = { IconButton(onClick = onBack) { Icon(Icons.Rounded.Close, "关闭") } }) }
        item {
            RoseCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("导入书源", fontSize = 18.sp, color = RoseInk)
                    Text("支持 JSON 数组或单个书源对象；同一书源网址会自动跳过重复项。", color = RoseMuted, fontSize = 12.sp, lineHeight = 18.sp)
                    Button(onClick = onImport, colors = ButtonDefaults.buttonColors(containerColor = RoseDeep)) { Icon(Icons.Rounded.Add, null); Text("选择 JSON 文件") }
                }
            }
        }
        item {
            RoseCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("远程书源订阅", color = RoseInk, fontSize = 17.sp)
                    Text("粘贴 Legado JSON 订阅链接，导入后可以一键刷新。", color = RoseMuted, fontSize = 12.sp)
                    OutlinedTextField(subscriptionUrl, { subscriptionUrl = it }, modifier = Modifier.fillMaxWidth(), label = { Text("HTTPS 订阅链接") }, singleLine = true)
                    Button(onClick = { if (subscriptionUrl.isNotBlank()) onAddSubscription(subscriptionUrl.trim()) }, colors = ButtonDefaults.buttonColors(containerColor = RoseDeep)) { Text("添加并导入订阅") }
                    Row {
                        TextButton(onClick = { onAddSubscription("https://legado.aoaostar.com/sources/71e56d4f.json") }) { Text("导入示例源 A", color = RoseDeep) }
                        TextButton(onClick = { onAddSubscription("https://e-c.coding.net/p/yicheng/d/YD/git/raw/master/sy.json") }) { Text("导入示例源 B", color = RoseDeep) }
                    }
                }
            }
        }
        if (subscriptions.isNotEmpty()) {
            item { SectionTitle("我的订阅", "SUBSCRIPTIONS") }
            items(subscriptions, key = { "sub:"+it.url }) { sub ->
                RoseCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(sub.name, color = RoseInk, fontWeight = FontWeight.Medium)
                        Text(sub.url, color = RoseMuted, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("包含 ${sub.sourceCount} 个书源 · 最近更新：${if (sub.lastSyncAt == 0L) "尚未刷新" else java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(sub.lastSyncAt))}", color = RoseMuted, fontSize = 10.sp)
                        Row {
                            TextButton(onClick = { onRefreshSubscription(sub.url) }) { Text("刷新订阅", color = RoseDeep) }
                            TextButton(onClick = { onRemoveSubscription(sub.url) }) { Text("移除订阅", color = RoseMuted) }
                        }
                    }
                }
            }
        }
        item { SectionTitle("已收藏的书源", "SAVED SOURCES") }
        if (sources.isEmpty()) item { EmptyBooksCard(onImport) }
        items(sources, key = { it.url }) { source ->
            RoseCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(source.name, fontSize = 14.sp, color = RoseInk, fontWeight = FontWeight.Medium)
                        Text(source.url, fontSize = 10.sp, color = RoseMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (source.group.isNotBlank()) Text(source.group, fontSize = 10.sp, color = Champagne)
                    }
                    Switch(source.enabled, onCheckedChange = { onToggle(source, it) })
                    IconButton(onClick = { onDelete(source) }) { Icon(Icons.Rounded.DeleteOutline, "删除", tint = RoseMuted) }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(modifier: Modifier, bookCount: Int, sourceCount: Int, updateStatus: String, onSources: () -> Unit, onCheckUpdate: () -> Unit, onBack: () -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { RoseHeader("我的", "A PERSONAL READING SPACE") }
        item {
            RoseCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("蔷薇书笺", fontSize = 22.sp, fontFamily = FontFamily.Serif, color = RoseInk)
                    Text("Read · More · Beautiful", color = Champagne, fontSize = 11.sp)
                    Text("藏书 $bookCount 本    书源 $sourceCount 个", color = RoseMuted, fontSize = 12.sp)
                }
            }
        }
        item { SettingItem(Icons.Rounded.Explore, "书源管理", "导入、启用或移除书源", onSources) }
        item { SettingItem(Icons.Rounded.Palette, "阅读主题", "纸白、奶油、粉雾、夜间主题将在阅读设置中扩展", {}) }
        item { SettingItem(Icons.Rounded.TextFields, "字体", "系统字体与自定义字体支持正在扩展", {}) }
        item { SettingItem(Icons.Rounded.Backup, "备份与恢复", "本地书架与书源备份规划中", {}) }
        item { SettingItem(Icons.Rounded.SystemUpdate, "检查更新", updateStatus, onCheckUpdate) }
        item { SettingItem(Icons.Rounded.Info, "关于", "蔷薇书笺 · 0.3.0", {}) }
    }
}

@Composable
private fun SettingItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String, onClick: () -> Unit) {
    RoseCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = RoseDeep)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = RoseInk, fontWeight = FontWeight.Medium)
                Text(desc, color = RoseMuted, fontSize = 11.sp)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = RoseMuted)
        }
    }
}

@Composable
private fun RoseReader(book: Novel, text: String, initialParagraph: Int, onBack: () -> Unit, onProgress: (Int) -> Unit) {
    var fontSize by remember { mutableFloatStateOf(19f) }
    var lineSpace by remember { mutableFloatStateOf(1.8f) }
    var dark by remember { mutableStateOf(false) }
    var fontMode by remember { mutableIntStateOf(0) }
    var paperMode by remember { mutableIntStateOf(0) }
    var pageMargin by remember { mutableFloatStateOf(24f) }
    var controls by remember { mutableStateOf(false) }
    val paragraphs = remember(text) { formatNovelText(text) }
    val background = if (dark) Color(0xFF282426) else listOf(Color(0xFFF8F2E9), Color(0xFFFFF8F0), Color(0xFFF0E9DC), Color(0xFFE8E1D3))[paperMode]
    val foreground = if (dark) Color(0xFFE8DCD2) else Color(0xFF493C3B)
    val readingFont = when(fontMode) { 1 -> FontFamily.Serif; 2 -> FontFamily.Monospace; else -> FontFamily.SansSerif }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialParagraph.coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0)))
    val scope = rememberCoroutineScope()
    val safePage = listState.firstVisibleItemIndex.coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0))
    LaunchedEffect(listState.firstVisibleItemIndex) { onProgress(listState.firstVisibleItemIndex) }

    Column(Modifier.fillMaxSize().background(background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回", tint = foreground) }
            Text(book.title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = foreground, fontSize = 14.sp)
            IconButton(onClick = { controls = !controls }) { Icon(Icons.Rounded.Tune, "阅读设置", tint = foreground) }
            IconButton(onClick = { dark = !dark }) { Icon(if (dark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode, "切换日夜", tint = foreground) }
        }
        if (paragraphs.isNotEmpty()) {
            Text("第 ${safePage + 1} / ${paragraphs.size} 段", color = RoseMuted, fontSize = 10.sp, modifier = Modifier.padding(start = 26.dp, top = 6.dp, bottom = 10.dp))
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = pageMargin.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(paragraphs.indices.toList()) { index ->
                    val paragraph = paragraphs[index]
                    val chapter = paragraph.matches(Regex("^(第[0-9一二三四五六七八九十百千零〇两]+[章节回卷].{0,50}|正文|序章|楔子|尾声|番外.*)$"))
                    Text(paragraph, color = foreground, fontSize = if (chapter) (fontSize + 2).sp else fontSize.sp, fontWeight = if (chapter) FontWeight.SemiBold else FontWeight.Normal, lineHeight = (fontSize * lineSpace).sp, letterSpacing = .3.sp, fontFamily = readingFont, modifier = if (chapter) Modifier.fillMaxWidth().padding(vertical = 10.dp) else Modifier.fillMaxWidth())
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { scope.launch { listState.animateScrollToItem((safePage - 1).coerceAtLeast(0)) } }, enabled = safePage > 0) { Text("上一段", color = RoseDeep) }
                Slider(safePage.toFloat(), onValueChange = { scope.launch { listState.scrollToItem(it.toInt()) } }, valueRange = 0f..paragraphs.lastIndex.coerceAtLeast(1).toFloat(), modifier = Modifier.weight(1f), colors = SliderDefaults.colors(thumbColor = RoseDeep, activeTrackColor = RoseDeep))
                TextButton(onClick = { scope.launch { listState.animateScrollToItem((safePage + 1).coerceAtMost(paragraphs.lastIndex)) } }, enabled = safePage < paragraphs.lastIndex) { Text("下一段", color = RoseDeep) }
            }
        } else Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("这本书暂时没有正文", color = foreground) }
        if (controls) {
            Surface(color = if (dark) Color(0xFF332D31) else RoseWhite, shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("字号 ${fontSize.toInt()}", color = foreground, fontSize = 12.sp)
                        Slider(fontSize, { fontSize = it }, valueRange = 14f..30f, modifier = Modifier.weight(1f), colors = SliderDefaults.colors(thumbColor = RoseDeep, activeTrackColor = RoseDeep))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("行距", color = foreground, fontSize = 12.sp)
                        Slider(lineSpace, { lineSpace = it }, valueRange = 1.4f..2.2f, modifier = Modifier.weight(1f), colors = SliderDefaults.colors(thumbColor = RoseDeep, activeTrackColor = RoseDeep))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("字体", color = foreground, fontSize = 12.sp)
                        TextButton(onClick = { fontMode = (fontMode + 1) % 3 }) { Text(listOf("无衬线", "衬线", "等宽")[fontMode], color = RoseDeep) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("纸张", color = foreground, fontSize = 12.sp)
                        TextButton(onClick = { paperMode = (paperMode + 1) % 4 }) { Text(listOf("米白", "奶油", "暖灰", "复古")[paperMode], color = RoseDeep) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("页边距", color = foreground, fontSize = 12.sp)
                        Slider(pageMargin, { pageMargin = it }, valueRange = 16f..42f, modifier = Modifier.weight(1f), colors = SliderDefaults.colors(thumbColor = RoseDeep, activeTrackColor = RoseDeep))
                    }
                }
            }
        }
    }
}

private fun formatNovelText(raw: String): List<String> {
    val cleaned = raw.replace("\r\n", "\n").replace('\r', '\n').trim()
    if (cleaned.isBlank()) return emptyList()
    return cleaned.split(Regex("\n{2,}"))
        .flatMap { block -> block.split('\n').map { it.trim() }.filter { it.isNotBlank() } }
}
