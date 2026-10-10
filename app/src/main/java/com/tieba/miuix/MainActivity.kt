// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.tieba.miuix

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.tieba.miuix.data.TiebaRepository
import com.tieba.miuix.ui.ForumListPage
import com.tieba.miuix.ui.ThreadDetailPage
import com.tieba.miuix.ui.theme.TiebaTheme

/**
 * 唯一的 Activity。
 *
 * 页面切换只用最简单的状态机（列表页 <-> 帖子详情页），
 * 不引入 navigation 依赖，保持工程可编译的最小依赖面。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 全屏沉浸式，状态栏/导航栏交给 miuix 主题自己控制颜色。
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Xiaomi moment：不让系统在导航栏上再叠一层半透明遮罩。
            window.isNavigationBarContrastEnforced = false
        }

        val repository = (application as TiebaApplication).repository

        setContent {
            TiebaTheme {
                TiebaApp(repository)
            }
        }
    }
}

/** 当前打开的主题帖；为空表示停留在列表页。 */
private data class OpenedThread(
    val tid: Long,
    val title: String,
    val forumName: String,
)

@Composable
private fun TiebaApp(repository: TiebaRepository) {
    var openedThread by remember { mutableStateOf<OpenedThread?>(null) }
    var keyword by rememberSaveable { mutableStateOf("") }

    val opened = openedThread
    if (opened == null) {
        ForumListPage(
            repository = repository,
            initialKeyword = keyword,
            onKeywordChange = { keyword = it },
            onOpenThread = { tid, title, forumName ->
                openedThread = OpenedThread(tid = tid, title = title, forumName = forumName)
            },
        )
    } else {
        BackHandler { openedThread = null }
        ThreadDetailPage(
            repository = repository,
            tid = opened.tid,
            title = opened.title,
            forumName = opened.forumName,
            onBack = { openedThread = null },
        )
    }
}
