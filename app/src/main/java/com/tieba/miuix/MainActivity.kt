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
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import com.tieba.miuix.ui.FavoritesPage
import com.tieba.miuix.ui.ForumDetailActivity
import com.tieba.miuix.ui.ForumListPage
import com.tieba.miuix.ui.HistoryPage
import com.tieba.miuix.ui.HomeFeedPage
import com.tieba.miuix.ui.LocalAccountStore
import com.tieba.miuix.ui.LocalHistoryStore
import com.tieba.miuix.ui.LocalLoginNote
import com.tieba.miuix.ui.LocalRequestLogin
import com.tieba.miuix.ui.LocalRepository
import com.tieba.miuix.ui.LocalSearchHistoryStore
import com.tieba.miuix.ui.LocalSettingsStore
import com.tieba.miuix.ui.LoginActivity
import com.tieba.miuix.ui.MessagesPage
import com.tieba.miuix.ui.ProfilePage
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
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/** 底部导航的入口（只留四个主入口，收藏 / 历史 / 设置收进「我的」）。 */
private const val TAB_HOME = 0
private const val TAB_MESSAGES = 1
private const val TAB_SEARCH = 2
private const val TAB_PROFILE = 3

/** 「我的」里可以点开的二级页面。 */
private const val PROFILE_PAGE_NONE = 0
private const val PROFILE_PAGE_FAVORITES = 1
private const val PROFILE_PAGE_HISTORY = 2
private const val PROFILE_PAGE_SETTINGS = 3
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
    var profilePage by rememberSaveable { mutableIntStateOf(PROFILE_PAGE_NONE) }
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
            BottomTab("消息", MiuixIcons.Messages, TAB_MESSAGES),
            BottomTab("搜索", MiuixIcons.Search, TAB_SEARCH),
            BottomTab("我的", MiuixIcons.Favorites, TAB_PROFILE),
        )
    }
    val bottomPadding = 72.dp

    // 未读消息数：登录后拉一次，切回消息页时再对齐一次。
    var unreadCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(accountStore.isLoggedIn, selectedTab) {
        if (!accountStore.isLoggedIn) {
            unreadCount = 0
            return@LaunchedEffect
        }
        val counts = runCatching { repository.getMessageCounts() }.getOrNull()
        unreadCount = counts?.let { it.replyMe + it.atMe } ?: 0
    }

    CompositionLocalProvider(
        LocalRequestLogin provides requestLogin,
        LocalLoginNote provides loginNote,
    ) {
        if (profilePage != PROFILE_PAGE_NONE) {
            // 「我的」里的二级页面：铺满整屏，底栏让位，返回键回到「我的」。
            BackHandler { profilePage = PROFILE_PAGE_NONE }
            when (profilePage) {
                PROFILE_PAGE_FAVORITES -> FavoritesPage(
                    bottomPadding = 16.dp,
                    onOpenThread = { tid, title, forumName ->
                        context.startActivity(
                            ThreadDetailActivity.intent(context, tid, title, forumName),
                        )
                    },
                    onOpenForum = { name ->
                        context.startActivity(ForumDetailActivity.intent(context, name))
                    },
                )

                PROFILE_PAGE_HISTORY -> HistoryPage(
                    bottomPadding = 16.dp,
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

                else -> SettingsPage(bottomPadding = 16.dp)
            }
            return@CompositionLocalProvider
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    TAB_MESSAGES -> MessagesPage(
                        bottomPadding = bottomPadding,
                        onOpenThread = { tid, title, forumName, postId ->
                            context.startActivity(
                                ThreadDetailActivity.intent(
                                    context,
                                    tid,
                                    title,
                                    forumName,
                                    postId,
                                ),
                            )
                        },
                        onOpenUser = { portrait, uid ->
                            context.startActivity(UserPageActivity.intent(context, portrait, uid))
                        },
                    )

                    TAB_SEARCH -> SearchPage(
                        bottomPadding = bottomPadding,
                        onOpenThread = { tid, forumName ->
                            context.startActivity(
                                ThreadDetailActivity.intent(context, tid, "", forumName),
                            )
                        },
                        onOpenForum = { name ->
                            context.startActivity(ForumDetailActivity.intent(context, name))
                        },
                    )

                    TAB_PROFILE -> ProfilePage(
                        bottomPadding = bottomPadding,
                        onOpenForum = { name ->
                            context.startActivity(ForumDetailActivity.intent(context, name))
                        },
                        onOpenThread = { tid, title, forumName ->
                            context.startActivity(
                                ThreadDetailActivity.intent(context, tid, title, forumName),
                            )
                        },
                        onOpenSettings = { profilePage = PROFILE_PAGE_SETTINGS },
                        onOpenHistory = { profilePage = PROFILE_PAGE_HISTORY },
                        onOpenSearchHistory = { selectedTab = TAB_SEARCH },
                        onOpenFavorites = { profilePage = PROFILE_PAGE_FAVORITES },
                    )

                    else -> HomeFeedPage(
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
                badgeCount = unreadCount,
                badgeTabIndex = TAB_MESSAGES,
                onSelect = { selectedTab = it },
            )
        }
    }
}

/** 底部导航栏：等宽入口，选中项高亮，未读数用角标显示在「消息」上。 */
@Composable
private fun BottomBar(
    tabs: List<BottomTab>,
    selectedTab: Int,
    badgeCount: Int,
    badgeTabIndex: Int,
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
                    Box(contentAlignment = Alignment.TopEnd) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            modifier = Modifier.size(22.dp),
                        )
                        if (tab.index == badgeTabIndex && badgeCount > 0) {
                            Box(
                                modifier = Modifier
                                    .offset(x = 8.dp, y = (-4).dp)
                                    .background(
                                        color = MiuixTheme.colorScheme.error,
                                        shape = CircleShape,
                                    )
                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                            ) {
                                Text(
                                    text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.onError,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = tab.label, style = MiuixTheme.textStyles.footnote2)
                }
            }
        }
    }
}
