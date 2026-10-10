// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.tieba.miuix

import android.app.Application
import com.tieba.miuix.data.Credentials
import com.tieba.miuix.data.TiebaRepository

/**
 * 应用入口。
 *
 * 全局持有一个 [TiebaRepository]，供各个页面共享。
 * 当前是“匿名浏览”模式，[Credentials] 全部为空；
 * 以后接入登录后，只需要在这里换成读取持久化的凭证即可，UI 层不用改动。
 */
class TiebaApplication : Application() {

    val repository: TiebaRepository by lazy {
        TiebaRepository(Credentials())
    }
}
