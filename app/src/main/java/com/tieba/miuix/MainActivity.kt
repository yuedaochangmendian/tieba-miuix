// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.tieba.miuix

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tieba.miuix.data.TiebaRepository
import com.tieba.miuix.ui.ForumDetailActivity
import com.tieba.miuix.ui.ForumListPage
import com.tieba.miuix.ui.HistoryPage
import com.tieba.miuix.ui.LocalAccountStore
import com.tieba.miuix.ui.LocalHistoryStore
import com.tieba.miuix.ui.LocalLoginNote
import com.tieba.miuix.ui.LocalRequestLogin
import com.tieba.miuix.ui.LocalRepository
import com.tieba.miuix.ui.LocalSearchHistoryStore
import com.tieba.miuix.ui.LocalSettingsStore
import com.tieba.miuix.ui.LoginActivity
import com.tieba.miuix.ui.SearchPage
import com.tieba.miuix.ui.SettingsPage
import com.tieba.miuix.ui.ThreadDetailActivity
import com.tieba.miuix.ui.ThreadDetailPage
import com.tieba.miuix.ui.UserPageActivity
import com.tieba.miuix.ui.theme.TiebaTheme
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/** 底部导航的四个入口。 */
private const val TAB_HOME = 0
private const val TAB_SEARCH = 1
private const val TAB_HISTORY = 2
private const val TAB_SETTINGS = 3
/**
 * 唯一的 Activity。
 *
 * 页面切换只用状态机，不引入 navigation 依赖：
 * - 底部四个 Tab 常驻，切 Tab 不重建 App；
 * - 帖子详情 / 吧详情是独立 Activity，点进去就压入系统任务栈，
 *   返回手势、返回键都由系统管，回到这里时 Tab 与滚动位置都还在。
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

        val app = application as TiebaApplication

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
                    TiebaApp(app)
                }
            }
        }
    }
}

private data class BottomTab(
    val label: String,
    val icon: ImageVector,
    val index: Int,
)

@Composable
private fun TiebaApp(app: TiebaApplication) {
    val repository: TiebaRepository = app.repository
    val accountStore = app.accountStore
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var selectedTab by rememberSaveable { mutableIntStateOf(TAB_HOME) }
    var keyword by rememberSaveable { mutableStateOf("") }
    var loginNote by remember { mutableStateOf("") }

    // 登录成功后：写入凭证 → 补取 tbs → 给一句反馈。
    val loginLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val data = result.data ?: return@rememberLauncherForActivityResult
        val bduss = data.getStringExtra(LoginActivity.RESULT_BDUSS).orEmpty()
        if (bduss.isEmpty()) return@rememberLauncherForActivityResult
        val stoken = data.getStringExtra(LoginActivity.RESULT_STOKEN).orEmpty()
        accountStore.save(bduss = bduss, stoken = stoken)
        loginNote = "登录成功，正在获取发帖令牌…"
        scope.launch {
            val tbs = runCatching { repository.fetchTbs() }.getOrDefault("")
            if (tbs.isNotEmpty()) {
                accountStore.saveTbs(tbs)
                loginNote = "登录成功"
            } else {
                loginNote = "登录成功，但没取到发帖令牌，可稍后在设置里刷新"
            }
        }
    }

    val requestLogin: () -> Unit = remember(loginLauncher, context) {
        { loginLauncher.launch(LoginActivity.intent(context)) }
    }

    val tabs = remember {
        listOf(
            BottomTab("首页", MiuixIcons.Home, TAB_HOME),
            BottomTab("搜索", MiuixIcons.Search, TAB_SEARCH),
            BottomTab("历史", MiuixIcons.Recent, TAB_HISTORY),
            BottomTab("设置", MiuixIcons.Settings, TAB_SETTINGS),
        )
    }
    val bottomPadding = 72.dp

    CompositionLocalProvider(
        LocalRequestLogin provides requestLogin,
        LocalLoginNote provides loginNote,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    TAB_SEARCH -> SearchPage(
                        bottomPadding = bottomPadding,
                        onOpenThread = { tid, forumName ->
                            context.startActivity(
                                ThreadDetailActivity.intent(context, tid, "", forumName),
                            )
                        },
                        onOpenForum = { name ->
                            keyword = name
                            selectedTab = TAB_HOME
                        },
                    )

                    TAB_HISTORY -> HistoryPage(
                        bottomPadding = bottomPadding,
                        onOpenThread = { record ->
                            context.startActivity(
                                ThreadDetailActivity.intent(
                                    context,
                                    record.tid,
                                    record.title,
                                    record.forumName,
                                ),
                            )
                        },
                        onOpenForum = { name ->
                            context.startActivity(ForumDetailActivity.intent(context, name))
                        },
                    )

                    TAB_SETTINGS -> SettingsPage(bottomPadding = bottomPadding)

                    else -> ForumListPage(
                        repository = repository,
                        initialKeyword = keyword,
                        onKeywordChange = { keyword = it },
                        onOpenThread = { tid, title, forumName ->
                            context.startActivity(
                                ThreadDetailActivity.intent(context, tid, title, forumName),
                            )
                        },
                        onOpenUser = { portrait ->
                            context.startActivity(UserPageActivity.intent(context, portrait))
                        },
                        bottomPadding = bottomPadding,
                    )
                }
            }

            BottomBar(
                tabs = tabs,
                selectedTab = selectedTab,
                onSelect = { selectedTab = it },
            )
        }
    }
}

/** 底部导航栏：四个等宽入口，选中项高亮。 */
@Composable
private fun BottomBar(
    tabs: List<BottomTab>,
    selectedTab: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            val selected = tab.index == selectedTab
            Card(
                modifier = Modifier.weight(1f),
                cornerRadius = 14.dp,
                insideMargin = PaddingValues(vertical = 8.dp),
                colors = CardDefaults.defaultColors(
                    color = if (selected) {
                        MiuixTheme.colorScheme.primaryContainer
                    } else {
                        MiuixTheme.colorScheme.surfaceContainer
                    },
                    contentColor = if (selected) {
                        MiuixTheme.colorScheme.onPrimaryContainer
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
                ),
                pressFeedbackType = PressFeedbackType.Sink,
                showIndication = true,
                onClick = { onSelect(tab.index) },
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = tab.label, style = MiuixTheme.textStyles.footnote2)
                }
            }
        }
    }
}
