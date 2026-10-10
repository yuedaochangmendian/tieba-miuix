// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.data

import android.content.Context
import coil3.SingletonImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 图片缓存的查看与清理。
 *
 * 缓存由 Coil 的单例 ImageLoader 管理（装配点在 [com.tieba.miuix.TiebaApplication]），
 * 这里只做两件事：算已占用体积、按需清空。清空会同时清内存与磁盘，
 * 因为只清磁盘的话，当前会话里已经解码好的图还会继续显示，用户会以为没生效。
 */
suspend fun imageCacheSize(context: Context): Long = withContext(Dispatchers.IO) {
    val loader = SingletonImageLoader.get(context)
    runCatching { loader.diskCache?.size ?: 0L }.getOrDefault(0L)
}

suspend fun clearImageCache(context: Context): Boolean = withContext(Dispatchers.IO) {
    val loader = SingletonImageLoader.get(context)
    val diskOk = runCatching {
        loader.diskCache?.clear()
        true
    }.getOrDefault(false)
    runCatching { loader.memoryCache?.clear() }
    diskOk
}

/** 把字节数写成人看的大小，例如 `12.4 MB`。 */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index++
    }
    return if (index == 0) {
        bytes.toString() + " " + units[index]
    } else {
        String.format(java.util.Locale.US, "%.1f %s", value, units[index])
    }
}
