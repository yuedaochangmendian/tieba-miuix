// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.tieba.miuix.data.AccountStore
import com.tieba.miuix.data.HistoryStore
import com.tieba.miuix.data.SearchHistoryStore
import com.tieba.miuix.data.SettingsStore
import com.tieba.miuix.data.TiebaRepository
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 应用入口。
 *
 * 全局持有仓库与各个 Store，供所有页面共享：
 *  - [accountStore]：登录凭证（BDUSS / STOKEN / tbs）；
 *  - [repository]：接口访问，凭证从 [accountStore] 动态读取，登录后无需重建；
 *  - [settingsStore]：应用设置；
 *  - [historyStore]：浏览历史；
 *  - [searchHistoryStore]：搜索历史。
 *
 * 同时实现 [SingletonImageLoader.Factory] 作为 Coil 的单例装配点：
 * 图片走 OkHttp（自动共享连接池），并配内存 / 磁盘两级缓存。
 */
class TiebaApplication : Application(), SingletonImageLoader.Factory {

    val accountStore: AccountStore by lazy { AccountStore(this) }

    val repository: TiebaRepository by lazy {
        TiebaRepository { accountStore.credentials() }
    }

    val settingsStore: SettingsStore by lazy { SettingsStore(this) }

    val historyStore: HistoryStore by lazy { HistoryStore(this) }

    val searchHistoryStore: SearchHistoryStore by lazy { SearchHistoryStore(this) }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()
        return ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .build()
    }

    companion object {
        /** 从任意地方拿到全局实例（Compose 里一般用 LocalContext 更合适）。 */
        fun from(application: Application): TiebaApplication = application as TiebaApplication

        /**
         * 当前是否在 Wi-Fi（或以太网）上。
         *
         * 图片加载策略要据此判断「省流量」是否生效；拿不到网络信息时保守地按「非 Wi-Fi」处理。
         */
        fun isOnWifi(context: Context): Boolean {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = manager.activeNetwork ?: return false
            val capabilities = manager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        }
    }
}
