package com.romanticreader.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.LocalLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Paper = Color(0xFFFAF6F0)
private val Ink = Color(0xFF493A3C)
private val Rose = Color(0xFFB87583)
private val Muted = Color(0xFF8E7779)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { QiangweiApp() }
    }
}

@Composable
private fun QiangweiApp() {
    var selected by remember { mutableIntStateOf(0) }
    val tabs = listOf("书架", "发现", "设置")
    val icons = listOf(Icons.Rounded.LocalLibrary, Icons.Rounded.Explore, Icons.Rounded.Settings)

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Rose,
            onPrimary = Color.White,
            background = Paper,
            surface = Color(0xFFFFFCF8),
            onSurface = Ink
        )
    ) {
        Scaffold(
            containerColor = Paper,
            bottomBar = {
                NavigationBar(containerColor = Color(0xFFFFFCF8)) {
                    tabs.forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = selected == index,
                            onClick = { selected = index },
                            icon = { Icon(icons[index], contentDescription = label) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        ) { padding ->
            when (selected) {
                0 -> Bookshelf(Modifier.padding(padding))
                1 -> Discovery(Modifier.padding(padding))
                else -> SettingsPage(Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun Bookshelf(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 24.dp)
    ) {
        Text("蔷薇书笺", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Text("把喜欢的故事，妥帖收藏。", color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(26.dp))
        Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFFF1E4E1)) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text("今日阅读", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Text("让故事慢慢展开", color = Ink, fontSize = 21.sp, fontWeight = FontWeight.Medium)
                Text("导入一本 TXT 小说，开启你的阅读时光。", color = Muted, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("我的书架", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Spacer(Modifier.height(12.dp))
        Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFFFFFCF8)) {
            Row(
                Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Book, contentDescription = null, tint = Rose, modifier = Modifier.size(34.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("书架尚空", color = Ink, fontWeight = FontWeight.Medium)
                    Text("后续版本将支持本地导入与在线书源。", color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun Discovery(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(22.dp)) {
        Text("发现", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Text("探索书源与下一段故事。", color = Muted)
        Spacer(Modifier.height(24.dp))
        Text("书源管理与在线搜索正在开发中。", color = Ink)
    }
}

@Composable
private fun SettingsPage(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(22.dp)) {
        Text("设置", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Text("让阅读体验更合心意。", color = Muted)
        Spacer(Modifier.height(24.dp))
        Text("版本 0.1.0 · 蔷薇书笺", color = Ink)
        Text("自动更新、字体管理与阅读排版将在后续版本接入。", color = Muted)
    }
}
