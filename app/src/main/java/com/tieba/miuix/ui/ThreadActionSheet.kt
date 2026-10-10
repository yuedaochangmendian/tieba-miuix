package com.tieba.miuix.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.tieba.miuix.protocol.ThreadInfo

/**
 * 帖子长按菜单。
 *
 * 这是工程里第一个「动作表」：MiuiX 的 Dropdown 是下拉选择器、ListPopupColumn 只是
 * 布局容器，都不适合做动作菜单，所以用 `Dialog` 自己搭一个底部卡片 —— 外壳与行样式的
 * 公共实现在 `SearchThreadActionSheet.kt` 的 [BottomActionSheet] / [ActionSheetRow]。
 *
 * @param thread 被长按的帖子
 * @param onDismiss 关闭菜单
 * @param onOpenThread 打开帖子
 * @param onOpenForum 进入帖子所属的吧
 * @param onOpenUser 打开楼主资料页，参数是 portrait
 * @param onNote 给用户一句提示（复制成功之类）
 * @param onBlockAuthor 屏蔽楼主；不传则不显示这一行
 */
@Composable
fun ThreadActionSheet(
    thread: ThreadInfo,
    onDismiss: () -> Unit,
    onOpenThread: () -> Unit,
    onOpenForum: (forumName: String) -> Unit,
    onOpenUser: (portrait: String) -> Unit,
    onNote: (String) -> Unit,
    onBlockAuthor: (() -> Unit)? = null,
) {
    ThreadActionSheet(
        title = thread.title,
        tid = thread.id,
        forumName = thread.fname,
        portrait = thread.author?.portrait.orEmpty(),
        authorUid = thread.authorId.takeIf { it > 0L } ?: thread.author?.id ?: 0L,
        authorName = thread.author?.displayName.orEmpty(),
        onDismiss = onDismiss,
        onOpenThread = onOpenThread,
        onOpenForum = onOpenForum,
        onOpenUser = onOpenUser,
        onNote = onNote,
        onBlockAuthor = onBlockAuthor,
    )
}

/**
 * 帖子长按菜单的通用版本。
 *
 * 不是所有列表都拿得到完整的 `ThreadInfo`：吧详情接口给的只是「标题 + tid + 回复数」，
 * 所以动作表真正需要的其实就是这几个字段，其余按需传。
 *
 * @param title 帖子标题，作为动作表抬头
 * @param tid 帖子 id，用于复制链接与分享
 * @param forumName 帖子所属吧名，空串则不显示「进入某吧」
 * @param portrait 楼主 portrait，空串则不显示「查看楼主」
 * @param authorUid / authorName 楼主标识，用于「屏蔽楼主」
 * @param onBlockAuthor 屏蔽楼主；为 null 时不显示这一行
 */
@Composable
fun ThreadActionSheet(
    title: String,
    tid: Long,
    forumName: String,
    portrait: String = "",
    authorUid: Long = 0L,
    authorName: String = "",
    onDismiss: () -> Unit,
    onOpenThread: () -> Unit,
    onOpenForum: (forumName: String) -> Unit,
    onOpenUser: (portrait: String) -> Unit,
    onNote: (String) -> Unit,
    onBlockAuthor: (() -> Unit)? = null,
) {
    val context = LocalContext.current

    BottomActionSheet(
        title = title.ifBlank { "（无标题）" },
        onDismiss = onDismiss,
    ) {
        ActionSheetRow(
            text = "打开帖子",
            onClick = onOpenThread,
            onAfter = onDismiss,
        )
        if (forumName.isNotBlank()) {
            ActionSheetRow(
                text = "进入「$forumName」",
                onClick = { onOpenForum(forumName) },
                onAfter = onDismiss,
            )
        }
        if (portrait.isNotEmpty()) {
            ActionSheetRow(
                text = "查看楼主",
                onClick = { onOpenUser(portrait) },
                onAfter = onDismiss,
            )
        }
        if (onBlockAuthor != null && (authorUid > 0L || authorName.isNotEmpty())) {
            ActionSheetRow(
                text = "屏蔽楼主",
                onClick = onBlockAuthor,
                onAfter = onDismiss,
            )
        }
        ActionSheetRow(
            text = "复制链接",
            onClick = {
                onNote(if (copyThreadLink(context, tid)) "链接已复制" else "复制失败")
            },
            onAfter = onDismiss,
        )
        ActionSheetRow(
            text = "分享",
            onClick = {
                if (!shareThread(context, tid, title)) {
                    onNote("没有找到可以分享的应用")
                }
            },
            onAfter = onDismiss,
        )
    }
}
