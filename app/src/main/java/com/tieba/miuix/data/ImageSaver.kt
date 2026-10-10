// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 把一张网络图片存进系统相册。
 *
 * 分两条路：
 * - Android 10（API 29）起走 `MediaStore`，不需要任何存储权限就能写进 `Pictures/贴吧`;
 * - 更早的系统只能直接写 `Environment.DIRECTORY_PICTURES`，同样无需权限（应用私有目录之外的公有图片目录
 *   在旧版本上由 `WRITE_EXTERNAL_STORAGE` 管控，这里只在 29 以下尝试，失败就如实报错）。
 *
 * 保存成功后返回相册里的显示路径，失败抛异常由调用方兜住。
 */
suspend fun saveImageToGallery(
    context: Context,
    url: String,
    fileName: String,
): String = withContext(Dispatchers.IO) {
    val safeName = fileName
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .ifBlank { "tieba_" + System.currentTimeMillis() }
        .let { if (it.contains('.')) it else it + ".jpg" }

    val bytes = downloadBytes(url)
    if (bytes.isEmpty()) throw IllegalStateException("图片内容为空")

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, safeName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeOf(safeName))
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + File.separator + ALBUM_NAME,
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("系统没有返回可写入的位置")
        try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IllegalStateException("无法打开输出流")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (error: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
        return@withContext Environment.DIRECTORY_PICTURES + "/" + ALBUM_NAME + "/" + safeName
    }

    val dir = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
        ALBUM_NAME,
    )
    if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("无法创建相册目录")
    val target = File(dir, safeName)
    FileOutputStream(target).use { it.write(bytes) }
    return@withContext target.absolutePath
}

/** 存图用的相册名，和系统相册里的文件夹名一致。 */
private const val ALBUM_NAME = "贴吧 Miuix"

private fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "png" -> "image/png"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    else -> "image/jpeg"
}

/**
 * 下载图片字节。
 *
 * 贴吧图床对没有 Referer 的直连是放行的，但这里仍补一个主站 Referer——
 * 部分 CDN 节点在没有 Referer 时会返回 403。
 */
private fun downloadBytes(url: String): ByteArray {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 25_000
        instanceFollowRedirects = true
        setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Version/4.0 Chrome/135.0.0.0 Mobile Safari/537.36",
        )
        setRequestProperty("Referer", "https://tieba.baidu.com/")
    }
    try {
        val code = connection.responseCode
        if (code !in 200..299) throw IllegalStateException("图片下载失败（HTTP " + code + "）")
        return connection.inputStream.use { it.readBytes() }
    } finally {
        connection.disconnect()
    }
}
