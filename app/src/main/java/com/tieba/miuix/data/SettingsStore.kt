// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tieba.miuix.protocol.TiebaConst.ThreadSortType

/** 深色模式。 */
object DarkMode {
    const val FOLLOW_SYSTEM = 0
    const val ALWAYS_OFF = 1
    const val ALWAYS_ON = 2
}

/** 图片加载策略，对齐 TiebaLite 的 `image_load_type`。 */
object ImageLoadMode {
    /** 智能省流量（默认）：仅 Wi-Fi 下加载。 */
    const val SMART = 0

    /** 智能无图：仅 Wi-Fi 下加载。 */
    const val NO_IMAGE_ON_MOBILE = 1

    /** 始终高质量。 */
    const val ALWAYS = 2

    /** 始终无图。 */
    const val NEVER = 3
}

/**
 * 帖子列表的展示形态，对齐 TiebaLite 的 `listSingle`。
 */
object ThreadListMode {
    /** 卡片：每层楼是一张卡片，信息更全。 */
    const val CARD = 0

    /** 单行：一行一条，一屏能看更多帖子（默认）。 */
    const val SINGLE = 1
}

/**
 * 应用设置。
 *
 * 全部字段都通过 [SettingsStore] 持久化到 SharedPreferences，并且是
 * Compose 可观察状态：修改后界面会自动重组。
 */
@Stable
class Settings(
    darkMode: Int,
    fontSizeLevel: Int,
    defaultThreadSort: Int,
    imageLoadMode: Int,
    imageDarkenInDarkTheme: Boolean,
    showBothNameAndNickname: Boolean,
    recordHistory: Boolean,
    homeShowRecentForum: Boolean,
    forumFabAction: String,
    threadListMode: Int,
) {
    var darkMode by mutableStateOf(darkMode)
    var fontSizeLevel by mutableStateOf(fontSizeLevel)
    var defaultThreadSort by mutableStateOf(defaultThreadSort)
    var imageLoadMode by mutableStateOf(imageLoadMode)
    var imageDarkenInDarkTheme by mutableStateOf(imageDarkenInDarkTheme)
    var showBothNameAndNickname by mutableStateOf(showBothNameAndNickname)
    var recordHistory by mutableStateOf(recordHistory)
    var homeShowRecentForum by mutableStateOf(homeShowRecentForum)
    var forumFabAction by mutableStateOf(forumFabAction)
    var threadListMode by mutableStateOf(threadListMode)
}

/**
 * 设置的读写入口。
 *
 * 写入策略是「先改内存状态、再异步落盘」，因此 UI 永远不会因为 IO 而卡顿。
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val settings: Settings = Settings(
        darkMode = prefs.getInt(KEY_DARK_MODE, DarkMode.FOLLOW_SYSTEM),
        fontSizeLevel = prefs.getInt(KEY_FONT_SIZE, 2),
        defaultThreadSort = prefs.getInt(KEY_THREAD_SORT, ThreadSortType.REPLY),
        imageLoadMode = prefs.getInt(KEY_IMAGE_LOAD, ImageLoadMode.SMART),
        imageDarkenInDarkTheme = prefs.getBoolean(KEY_IMAGE_DARKEN, false),
        showBothNameAndNickname = prefs.getBoolean(KEY_BOTH_NAME, false),
        recordHistory = prefs.getBoolean(KEY_RECORD_HISTORY, true),
        homeShowRecentForum = prefs.getBoolean(KEY_HOME_RECENT_FORUM, true),
        forumFabAction = prefs.getString(KEY_FAB_ACTION, FAB_REFRESH) ?: FAB_REFRESH,
        threadListMode = prefs.getInt(KEY_LIST_MODE, ThreadListMode.CARD),
    )

    fun setDarkMode(value: Int) {
        settings.darkMode = value
        prefs.edit().putInt(KEY_DARK_MODE, value).apply()
    }

    fun setFontSizeLevel(value: Int) {
        settings.fontSizeLevel = value.coerceIn(0, FONT_SCALE.size - 1)
        prefs.edit().putInt(KEY_FONT_SIZE, settings.fontSizeLevel).apply()
    }

    fun setDefaultThreadSort(value: Int) {
        settings.defaultThreadSort = value
        prefs.edit().putInt(KEY_THREAD_SORT, value).apply()
    }

    fun setImageLoadMode(value: Int) {
        settings.imageLoadMode = value
        prefs.edit().putInt(KEY_IMAGE_LOAD, value).apply()
    }

    fun setImageDarkenInDarkTheme(value: Boolean) {
        settings.imageDarkenInDarkTheme = value
        prefs.edit().putBoolean(KEY_IMAGE_DARKEN, value).apply()
    }

    fun setShowBothNameAndNickname(value: Boolean) {
        settings.showBothNameAndNickname = value
        prefs.edit().putBoolean(KEY_BOTH_NAME, value).apply()
    }

    fun setRecordHistory(value: Boolean) {
        settings.recordHistory = value
        prefs.edit().putBoolean(KEY_RECORD_HISTORY, value).apply()
    }

    fun setHomeShowRecentForum(value: Boolean) {
        settings.homeShowRecentForum = value
        prefs.edit().putBoolean(KEY_HOME_RECENT_FORUM, value).apply()
    }

    fun setForumFabAction(value: String) {
        settings.forumFabAction = value
        prefs.edit().putString(KEY_FAB_ACTION, value).apply()
    }

    fun setThreadListMode(value: Int) {
        settings.threadListMode = value
        prefs.edit().putInt(KEY_LIST_MODE, value).apply()
    }

    /** 当前字号倍率。 */
    val fontScale: Float get() = FONT_SCALE[settings.fontSizeLevel.coerceIn(0, FONT_SCALE.size - 1)]

    companion object {
        private const val PREFS_NAME = "tieba_miuix_settings"
        private const val KEY_DARK_MODE = "dark_theme"
        private const val KEY_FONT_SIZE = "font_size_level"
        private const val KEY_THREAD_SORT = "default_sort_type"
        private const val KEY_IMAGE_LOAD = "image_load_type"
        private const val KEY_IMAGE_DARKEN = "image_darken_when_dark_theme"
        private const val KEY_BOTH_NAME = "show_both_username_and_nickname"
        private const val KEY_RECORD_HISTORY = "record_history"
        private const val KEY_HOME_RECENT_FORUM = "home_page_show_history_forum"
        private const val KEY_FAB_ACTION = "forum_fab_function"
        private const val KEY_LIST_MODE = "list_single"

        /** 浮动按钮的行为。 */
        const val FAB_REFRESH = "refresh"
        const val FAB_BACK_TO_TOP = "back_to_top"
        const val FAB_NONE = "hide"

        /** 字号档位：0.85x ~ 1.30x。 */
        val FONT_SCALE = floatArrayOf(0.85f, 0.92f, 1.0f, 1.08f, 1.16f, 1.30f)
    }
}
