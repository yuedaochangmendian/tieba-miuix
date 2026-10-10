// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.tieba.miuix.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tieba.miuix.TiebaApplication
import com.tieba.miuix.data.TiebaRepository
import com.tieba.miuix.protocol.ForumDetailProto
import com.tieba.miuix.ui.theme.TiebaTheme
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * 吧详情页。
 *
 * 单独开 Activity 的理由：这是从帖子详情 / 搜索 / 历史里点进来的二级页面，
 * 独立 Activity 能自然获得系统返回手势，也不用把 MainActivity 的状态机撑大。
 *
 * 页面内容全部来自 App 协议 `/c/f/forum/getforumdetail`，匿名可用。
 */
class ForumDetailActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as TiebaApplication
        val forumName = intent.getStringExtra(EXTRA_FORUM_NAME).orEmpty()

        setContent {
            TiebaTheme(
                settings = app.settingsStore.settings,
                fontScale = app.settingsStore.fontScale,
            ) {
                CompositionLocalProvider(
                    LocalSettingsStore provides app.settingsStore,
                    LocalHistoryStore provides app.historyStore,
                    LocalSearchHistoryStore provides app.searchHistoryStore,
                    LocalAccountStore provides app.accountStore,
                    LocalRepository provides app.repository,
                ) {
                    ForumDetailScreen(
                        app = app,
                        forumName = forumName,
                        onBack = { finish() },
                        onOpenThread = { tid, title ->
                            startActivity(ThreadDetailActivity.intent(this, tid, title, forumName))
                        },
                    )
                }
            }
        }
    }

    companion object {
        private const val EXTRA_FORUM_NAME = "forum_name"

        fun intent(context: Context, forumName: String): Intent =
            Intent(context, ForumDetailActivity::class.java)
                .putExtra(EXTRA_FORUM_NAME, forumName)
    }
}

@Composable
private fun ForumDetailScreen(
    app: TiebaApplication,
    forumName: String,
    onBack: () -> Unit,
    onOpenThread: (Long, String) -> Unit,
) {
    val repository = app.repository
    var detail by remember { mutableStateOf<ForumDetailProto.Detail?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    val scrollBehavior = MiuixScrollBehavior()

    LaunchedEffect(forumName, reloadKey) {
        if (forumName.isBlank()) {
            errorMessage = "没有指定要查看的吧"
            return@LaunchedEffect
        }
        isLoading = true
        errorMessage = null
        try {
            val id = resolveForumId(repository, forumName)
            if (id <= 0L) {
                errorMessage = "没找到「" + forumName + "」这个吧"
            } else {
                val response = repository.getForumDetail(id)
                if (response.error.isOk) {
                    detail = response.detail
                    if (response.detail == null) errorMessage = "接口没有返回吧资料"
                } else {
                    errorMessage = response.error.errmsg.ifBlank {
                        "接口返回错误（errno = " + response.error.errno + "）"
                    }
                }
            }
        } catch (e: Exception) {
            errorMessage = e.message ?: ("网络请求失败：" + e.javaClass.simpleName)
        } finally {
            isLoading = false
        }
    }

    BackHandler { onBack() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = forumName.ifBlank { "吧详情" },
                subtitle = detail?.let { it.memberCount.toString() + " 会员 · " + it.threadCount + " 帖" }
                    .orEmpty(),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = MiuixIcons.Back, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { reloadKey += 1 }) {
                        Icon(imageVector = MiuixIcons.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
    ) { innerPadding ->
        val contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = innerPadding.calculateTopPadding() + 8.dp,
            bottom = innerPadding.calculateBottomPadding() + 24.dp,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            val message = errorMessage
            if (message != null) {
                item(key = "error") { MessageCard(text = message, isError = true) }
            }
            val info = detail
            if (info != null) {
                item(key = "info") { ForumInfoCard(detail = info) }
                if (info.threads.isNotEmpty()) {
                    item(key = "threads-title") { SectionTitle(text = "吧内主题帖") }
                    info.threads.forEach { thread ->
                        item(key = "thread-" + thread.tid) {
                            Box(modifier = Modifier.padding(bottom = 10.dp)) {
                                ForumThreadCard(
                                    thread = thread,
                                    onClick = { onOpenThread(thread.tid, thread.title) },
                                )
                            }
                        }
                    }
                } else {
                    item(key = "empty") {
                        MessageCard(text = "这个吧暂时没有取到主题帖。", isError = false)
                    }
                }
            } else if (message == null && isLoading) {
                item(key = "loading") { MessageCard(text = "正在读取吧资料…", isError = false) }
            }
        }
    }
}

@Composable
private fun ForumInfoCard(detail: ForumDetailProto.Detail) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        cornerRadius = 12.dp,
        insideMargin = PaddingValues(16.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (detail.avatarUrl.isNotEmpty()) {
                SafeImage(
                    url = detail.avatarUrl,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentDescription = detail.forumName,
                    cornerRadius = 12.dp,
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = detail.forumName.ifBlank { "未知吧" } + "吧",
                    style = MiuixTheme.textStyles.headline1,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (detail.lv1Name.isNotEmpty() || detail.lv2Name.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = listOf(detail.lv1Name, detail.lv2Name)
                            .filter { it.isNotEmpty() }
                            .joinToString(" · "),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
        if (detail.slogan.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = detail.slogan,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceContainer,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            StatItem(label = "会员", value = detail.memberCount.toString())
            StatItem(label = "帖子", value = detail.threadCount.toString())
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column {
        Text(
            text = value,
            style = MiuixTheme.textStyles.headline2,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

/** 吧内主题帖卡片：标题 + 摘要 + 回复数。 */
@Composable
private fun ForumThreadCard(thread: ForumDetailProto.SimpleThread, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 12.dp,
        insideMargin = PaddingValues(16.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = true,
        onClick = onClick,
    ) {
        Text(
            text = thread.title.ifBlank { "（无标题）" },
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val preview = thread.preview
        if (preview.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = preview,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = formatCount(thread.replyNum) + " 回复",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            if (thread.lastTimeInt > 0) {
                Text(
                    text = formatTime(thread.lastTimeInt.toLong()),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

/**
 * 把吧名解析成数字 id。
 *
 * `/c/f/forum/getforumdetail` 只吃 `forum_id`，而 UI 层到处传的是吧名，
 * 所以先用网页端搜索接口换一次 id。
 */
private suspend fun resolveForumId(repository: TiebaRepository, name: String): Long {
    if (name.isBlank()) return 0L
    val result = repository.searchForum(name)
    result.exact?.let { if (it.id > 0L) return it.id }
    result.fuzzy.firstOrNull { it.name == name }?.let { if (it.id > 0L) return it.id }
    return result.fuzzy.firstOrNull()?.id ?: 0L
}
