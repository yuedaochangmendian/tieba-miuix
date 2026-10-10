// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.tieba.miuix.data.AccountStore
import com.tieba.miuix.data.HistoryStore
import com.tieba.miuix.data.SearchHistoryStore
import com.tieba.miuix.data.Settings
import com.tieba.miuix.data.SettingsStore
import com.tieba.miuix.data.TiebaRepository

/**
 * 全局依赖的传递方式。
 *
 * 这个工程刻意不引入 navigation / hilt 之类的依赖，跨层取依赖统一走
 * `CompositionLocal`：`MainActivity` 在最外层 provide 一次，页面里直接读。
 */

val LocalSettingsStore = staticCompositionLocalOf<SettingsStore> {
    error("LocalSettingsStore 尚未提供，请在 MainActivity 的 setContent 里 provide")
}

val LocalAccountStore = staticCompositionLocalOf<AccountStore> {
    error("LocalAccountStore 尚未提供，请在 MainActivity 的 setContent 里 provide")
}

val LocalRepository = staticCompositionLocalOf<TiebaRepository> {
    error("LocalRepository 尚未提供，请在 MainActivity 的 setContent 里 provide")
}

val LocalHistoryStore = staticCompositionLocalOf<HistoryStore> {
    error("LocalHistoryStore 尚未提供，请在 MainActivity 的 setContent 里 provide")
}

val LocalSearchHistoryStore = staticCompositionLocalOf<SearchHistoryStore> {
    error("LocalSearchHistoryStore 尚未提供，请在 MainActivity 的 setContent 里 provide")
}

/**
 * 触发登录流程。
 *
 * 登录要启动 [LoginActivity] 并等它的返回值，而返回值的处理在 Activity 层，
 * 所以这里只放一个「请求登录」的回调；`MainActivity` 在最外层 provide。
 * 默认值是空实现——测试或预览环境里点了没反应，总好过直接抛异常。
 */
val LocalRequestLogin = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * 登录相关的临时提示，由 `MainActivity` 在登录流程结束后写入，设置页展示。
 */
val LocalLoginNote = staticCompositionLocalOf { "" }

/**
 * 当前设置快照。
 *
 * [Settings] 的字段本身是 Compose 状态，读它们就会自动重组；
 * 这个便捷属性只是为了少写一层 `.settings`。
 *
 * 因为要读 [androidx.compose.runtime.CompositionLocal]，只能在组合中调用，
 * 所以标成 `@Composable`。
 */
val currentSettings: Settings
    @Composable get() = LocalSettingsStore.current.settings
