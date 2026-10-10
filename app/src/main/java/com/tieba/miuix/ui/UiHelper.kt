// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.ui

import android.content.Intent
import com.tieba.miuix.data.DarkMode
import com.tieba.miuix.data.ImageLoadMode
import com.tieba.miuix.data.Settings
import com.tieba.miuix.data.SettingsStore
import com.tieba.miuix.protocol.TiebaConst.ThreadSortType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 深色模式的三个选项。 */
val DARK_MODE_OPTIONS: List<Pair<String, Int>> = listOf(
    "跟随系统" to DarkMode.FOLLOW_SYSTEM,
    "始终关闭" to DarkMode.ALWAYS_OFF,
    "始终开启" to DarkMode.ALWAYS_ON,
)

/** 字号档位（与 [SettingsStore.FONT_SCALE] 一一对应）。 */
val FONT_SIZE_OPTIONS: List<String> = listOf("最小", "小", "标准", "大", "特大", "最大")

/** 图片加载策略（对齐 TiebaLite 的 `image_load_type`）。 */
val IMAGE_LOAD_OPTIONS: List<Pair<String, Int>> = listOf(
    "智能省流量（仅 Wi-Fi 加载）" to ImageLoadMode.SMART,
    "智能无图（仅 Wi-Fi 加载）" to ImageLoadMode.NO_IMAGE_ON_MOBILE,
    "始终高质量" to ImageLoadMode.ALWAYS,
    "始终无图" to ImageLoadMode.NEVER,
)

/** 吧内帖子默认排序。 */
val THREAD_SORT_OPTIONS: List<Pair<String, Int>> = listOf(
    "回复时间" to ThreadSortType.REPLY,
    "最新发布" to ThreadSortType.CREATE,
    "热门" to ThreadSortType.HOT,
)

/** 吧内浮动按钮的行为。 */
val FAB_OPTIONS: List<Pair<String, String>> = listOf(
    "刷新" to SettingsStore.FAB_REFRESH,
    "回到顶部" to SettingsStore.FAB_BACK_TO_TOP,
    "隐藏" to SettingsStore.FAB_NONE,
)

/** 把设置里的深色模式取值翻译成界面文案。 */
fun darkModeLabel(value: Int): String =
    DARK_MODE_OPTIONS.firstOrNull { it.second == value }?.first ?: DARK_MODE_OPTIONS.first().first

/** 把设置里的字号档位翻译成界面文案。 */
fun fontSizeLabel(level: Int): String =
    FONT_SIZE_OPTIONS.getOrElse(level) { FONT_SIZE_OPTIONS[2] }

/** 把设置里的图片策略翻译成界面文案。 */
fun imageLoadLabel(value: Int): String =
    IMAGE_LOAD_OPTIONS.firstOrNull { it.second == value }?.first ?: IMAGE_LOAD_OPTIONS.first().first

/** 把设置里的浮动按钮行为翻译成界面文案。 */
fun fabLabel(value: String): String =
    FAB_OPTIONS.firstOrNull { it.second == value }?.first ?: FAB_OPTIONS.first().first

/** 判断当前设置下是否允许联网加载图片。 */
fun Settings.shouldLoadImage(onWifi: Boolean): Boolean = when (imageLoadMode) {
    ImageLoadMode.ALWAYS -> true
    ImageLoadMode.NEVER -> false
    // 智能省流量 / 智能无图：只在 Wi-Fi 下加载。
    else -> onWifi
}

/** 把接口返回的秒级时间戳格式化成可读文本。 */
fun formatTime(seconds: Long): String {
    if (seconds <= 0L) return ""
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        .format(Date(seconds * 1000L))
}

/** 把数字按中文习惯缩写（12345 → 1.2万）。 */
fun formatCount(value: Int): String = when {
    value <= 0 -> "0"
    value < 10_000 -> value.toString()
    else -> {
        val wan = value / 1000 / 10.0
        val text = if (wan >= 100) wan.toInt().toString() else String.format(Locale.US, "%.1f", wan)
        text + "万"
    }
}

/**
 * 把「多久以前」的毫秒时间戳格式化成浏览记录里用的相对时间。
 *
 * 与 TiebaLite 的历史页一致：今天 / 昨天 / N 天前 / 具体日期。
 */
fun formatRelativeTime(millis: Long): String {
    if (millis <= 0L) return ""
    val now = System.currentTimeMillis()
    val diffDays = ((now - millis) / (24L * 60L * 60L * 1000L)).toInt()
    return when {
        diffDays <= 0 -> "今天"
        diffDays == 1 -> "昨天"
        diffDays < 7 -> diffDays.toString() + " 天前"
        else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(millis))
    }
}

/** 主题帖的网页地址，用于分享与深链。 */
fun threadWebUrl(tid: Long): String = "https://tieba.baidu.com/p/" + tid

/**
 * 调起系统分享面板分享一个主题帖。
 *
 * 这里刻意不带 `FLAG_ACTIVITY_NEW_TASK`：调用方是 Activity，
 * 加了反而会让分享面板跑到独立任务栈里。
 */
fun shareThread(context: android.content.Context, tid: Long, title: String): Boolean {
    if (tid <= 0L) return false
    return runCatching {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(
                Intent.EXTRA_TEXT,
                (if (title.isNotBlank()) title + "\n" else "") + threadWebUrl(tid),
            )
        }
        context.startActivity(Intent.createChooser(send, "分享帖子"))
    }.isSuccess
}
