// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.protocol

/**
 * 贴吧接口中的公共消息定义。
 *
 * 所有字段编号均严格对应 aiotieba 的 `.proto` 文件，解析时按编号读取，
 * 遇到未知字段直接跳过，保证对服务端新增字段的兼容性。
 */

/** `Error` 消息。`errorno=1`、`errmsg=2`。 */
data class TiebaError(
    val errno: Int = 0,
    val errmsg: String = "",
) {
    val isOk: Boolean get() = errno == 0

    companion object {
        fun parse(r: ProtoReader): TiebaError {
            var errno = 0
            var errmsg = ""
            while (r.hasMore) {
                val tag = r.readTag()
                when (tag ushr 3) {
                    1 -> errno = r.readInt32()
                    2 -> errmsg = r.readString()
                    else -> r.skip(tag and 7)
                }
            }
            return TiebaError(errno, errmsg)
        }
    }
}

/** `Page` 分页信息。`page_size=1`、`current_page=3`、`total_count=4`、`total_page=5`、`has_more=6`、`has_prev=7`。 */
data class Page(
    val pageSize: Int = 0,
    val currentPage: Int = 0,
    val totalCount: Int = 0,
    val totalPage: Int = 0,
    val hasMore: Boolean = false,
    val hasPrev: Boolean = false,
) {
    companion object {
        fun parse(r: ProtoReader): Page {
            var pageSize = 0
            var currentPage = 0
            var totalCount = 0
            var totalPage = 0
            var hasMore = false
            var hasPrev = false
            while (r.hasMore) {
                val tag = r.readTag()
                when (tag ushr 3) {
                    1 -> pageSize = r.readInt32()
                    3 -> currentPage = r.readInt32()
                    4 -> totalCount = r.readInt32()
                    5 -> totalPage = r.readInt32()
                    6 -> hasMore = r.readBool()
                    7 -> hasPrev = r.readBool()
                    else -> r.skip(tag and 7)
                }
            }
            return Page(pageSize, currentPage, totalCount, totalPage, hasMore, hasPrev)
        }

        /**
         * 从 JSON 解析。
         *
         * 吧内搜索（`POST /c/s/searchpost`）的分页信息是 JSON，且所有字段都是字符串。
         */
        fun parse(json: org.json.JSONObject): Page = Page(
            pageSize = json.optIntFromString("page_size"),
            currentPage = json.optIntFromString("current_page"),
            totalCount = json.optIntFromString("total_count"),
            totalPage = json.optIntFromString("total_page"),
            hasMore = json.opt("has_more").toBooleanFlag(),
            hasPrev = json.opt("has_prev").toBooleanFlag(),
        )
    }
}

/** JSON 里的布尔可能是 `true`、`1` 或 `"1"`。 */
internal fun Any?.toBooleanFlag(): Boolean = when (this) {
    null -> false
    is Boolean -> this
    is Number -> toInt() != 0
    is String -> this == "1" || equals("true", ignoreCase = true)
    else -> false
}

/** `Agree` 顶/踩信息。`agree_num=1`、`disagree_num=4`。 */
data class Agree(
    val agreeNum: Int = 0,
    val disagreeNum: Int = 0,
) {
    companion object {
        fun parse(r: ProtoReader): Agree {
            var agree = 0
            var disagree = 0
            while (r.hasMore) {
                val tag = r.readTag()
                when (tag ushr 3) {
                    1 -> agree = r.readInt32()
                    4 -> disagree = r.readInt32()
                    else -> r.skip(tag and 7)
                }
            }
            return Agree(agree, disagree)
        }
    }
}

/** `SimpleForum` 吧信息。`id=1`、`name=2`、`first_class=7`、`second_class=8`、`member_num=12`、`post_num=13`。 */
data class SimpleForum(
    val id: Long = 0,
    val name: String = "",
    val firstClass: String = "",
    val secondClass: String = "",
    val memberNum: Int = 0,
    val postNum: Int = 0,
) {
    companion object {
        fun parse(r: ProtoReader): SimpleForum {
            var id = 0L
            var name = ""
            var firstClass = ""
            var secondClass = ""
            var memberNum = 0
            var postNum = 0
            while (r.hasMore) {
                val tag = r.readTag()
                when (tag ushr 3) {
                    1 -> id = r.readInt64()
                    2 -> name = r.readString()
                    7 -> firstClass = r.readString()
                    8 -> secondClass = r.readString()
                    12 -> memberNum = r.readInt32()
                    13 -> postNum = r.readInt32()
                    else -> r.skip(tag and 7)
                }
            }
            return SimpleForum(id, name, firstClass, secondClass, memberNum, postNum)
        }
    }
}

/**
 * `User` 用户信息。
 *
 * `id=2`、`name=3`、`name_show=4`、`portrait=5`、`level_id=23`、`is_bawu=25`、
 * `fans_num=30`、`concern_num=31`、`sex=32`、`intro=34`、`post_num=37`、
 * `tb_age=38`、`is_mem=39`、`gender=42`、`is_friend=46`、`thread_num=87`、
 * `is_fans=91`、`is_default_avatar=106`、`tieba_uid=120`、`ip_address=127`。
 */
data class User(
    val id: Long = 0,
    val name: String = "",
    val nameShow: String = "",
    val portrait: String = "",
    val levelId: Int = 0,
    val isBawu: Boolean = false,
    val fansNum: Int = 0,
    val concernNum: Int = 0,
    val sex: Int = 0,
    val intro: String = "",
    val postNum: Int = 0,
    val tbAge: String = "",
    val isMem: Boolean = false,
    val gender: Int = 0,
    val isFriend: Boolean = false,
    val threadNum: Int = 0,
    val isFans: Boolean = false,
    val isDefaultAvatar: Boolean = false,
    val tiebaUid: Long = 0,
    val ipAddress: String = "",
) {
    /** 用于展示的名字（优先展示昵称）。 */
    val displayName: String get() = nameShow.ifEmpty { name }

    /**
     * 按设置决定展示名。
     *
     * [showBoth] 为真且这个用户既有用户名又有昵称时，展示成「用户名（昵称）」，
     * 对应设置里的「同时显示用户名与昵称」；否则等价于 [displayName]。
     */
    fun displayNameWith(showBoth: Boolean): String =
        if (showBoth && name.isNotEmpty() && nameShow.isNotEmpty() && name != nameShow) {
            "$name（$nameShow）"
        } else {
            displayName
        }

    /** 头像的远程地址；为空时 UI 会退化为文字头像。 */
    val portraitUrl: String
        get() = when {
            portrait.isEmpty() -> ""
            portrait.startsWith("http", ignoreCase = true) -> portrait
            else -> "https://tb.himg.baidu.com/sys/portrait/item/$portrait"
        }

    companion object {
        fun parse(r: ProtoReader): User {
            var id = 0L
            var name = ""
            var nameShow = ""
            var portrait = ""
            var levelId = 0
            var isBawu = false
            var fansNum = 0
            var concernNum = 0
            var sex = 0
            var intro = ""
            var postNum = 0
            var tbAge = ""
            var isMem = false
            var gender = 0
            var isFriend = false
            var threadNum = 0
            var isFans = false
            var isDefaultAvatar = false
            var tiebaUid = 0L
            var ipAddress = ""
            while (r.hasMore) {
                val tag = r.readTag()
                when (tag ushr 3) {
                    2 -> id = r.readInt64()
                    3 -> name = r.readString()
                    4 -> nameShow = r.readString()
                    5 -> portrait = r.readString()
                    23 -> levelId = r.readInt32()
                    25 -> isBawu = r.readBool()
                    30 -> fansNum = r.readInt32()
                    31 -> concernNum = r.readInt32()
                    32 -> sex = r.readInt32()
                    34 -> intro = r.readString()
                    37 -> postNum = r.readInt32()
                    38 -> tbAge = r.readString()
                    39 -> isMem = r.readBool()
                    42 -> gender = r.readInt32()
                    46 -> isFriend = r.readBool()
                    87 -> threadNum = r.readInt32()
                    91 -> isFans = r.readBool()
                    106 -> isDefaultAvatar = r.readBool()
                    120 -> tiebaUid = r.readInt64()
                    127 -> ipAddress = r.readString()
                    else -> r.skip(tag and 7)
                }
            }
            return User(
                id = id, name = name, nameShow = nameShow, portrait = portrait,
                levelId = levelId, isBawu = isBawu, fansNum = fansNum, concernNum = concernNum,
                sex = sex, intro = intro, postNum = postNum, tbAge = tbAge, isMem = isMem,
                gender = gender, isFriend = isFriend, threadNum = threadNum, isFans = isFans,
                isDefaultAvatar = isDefaultAvatar, tiebaUid = tiebaUid, ipAddress = ipAddress,
            )
        }
    }
}

/**
 * `PbContent` 帖子富文本片段。
 *
 * 常用字段：`type=1`、`text=2`、`link=3`、`src=4`、`bsize=5`、`cdn_src=8`、
 * `big_cdn_src=9`、`c=11`、`voice_md5=12`、`during_time=13`、`width=18`、
 * `height=19`、`origin_src=25`。
 */
data class PbContent(
    val type: Int = 0,
    val text: String = "",
    val link: String = "",
    val src: String = "",
    val bsize: String = "",
    val cdnSrc: String = "",
    val bigCdnSrc: String = "",
    val c: String = "",
    val voiceMd5: String = "",
    val duringTime: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val originSrc: String = "",
) {
    /** 该片段的最佳图片地址。 */
    val bestImageUrl: String get() = bigCdnSrc.ifEmpty { cdnSrc.ifEmpty { src.ifEmpty { originSrc } } }

    companion object {
        fun parse(r: ProtoReader): PbContent {
            var type = 0
            var text = ""
            var link = ""
            var src = ""
            var bsize = ""
            var cdnSrc = ""
            var bigCdnSrc = ""
            var c = ""
            var voiceMd5 = ""
            var duringTime = 0
            var width = 0
            var height = 0
            var originSrc = ""
            while (r.hasMore) {
                val tag = r.readTag()
                when (tag ushr 3) {
                    1 -> type = r.readInt32()
                    2 -> text = r.readString()
                    3 -> link = r.readString()
                    4 -> src = r.readString()
                    5 -> bsize = r.readString()
                    8 -> cdnSrc = r.readString()
                    9 -> bigCdnSrc = r.readString()
                    11 -> c = r.readString()
                    12 -> voiceMd5 = r.readString()
                    13 -> duringTime = r.readInt32()
                    18 -> width = r.readInt32()
                    19 -> height = r.readInt32()
                    25 -> originSrc = r.readString()
                    else -> r.skip(tag and 7)
                }
            }
            return PbContent(
                type = type, text = text, link = link, src = src, bsize = bsize,
                cdnSrc = cdnSrc, bigCdnSrc = bigCdnSrc, c = c, voiceMd5 = voiceMd5,
                duringTime = duringTime, width = width, height = height, originSrc = originSrc,
            )
        }

        /** 解析一个 repeated PbContent 字段。 */
        fun parseList(reader: ProtoReader): List<PbContent> {
            val list = ArrayList<PbContent>()
            while (reader.hasMore) {
                val tag = reader.readTag()
                if (tag ushr 3 == 1) {
                    list += parse(reader.readMessage())
                } else {
                    reader.skip(tag and 7)
                }
            }
            return list
        }
    }
}

/** 将 [PbContent] 列表转为便于展示的纯文本。 */
fun List<PbContent>.toPlainText(): String = buildString {
    for (item in this@toPlainText) {
        when {
            item.text.isNotEmpty() -> append(item.text)
            item.type == 3 || item.type == 20 -> append("[图片]")
            item.type == 4 -> append("[表情]")
            item.type == 10 -> append("[语音]")
            item.type == 5 -> append("[视频]")
        }
    }
}.trim()

/**
 * `SubPostList` 楼中楼楼层。
 * `id=1`、`content=2`(repeated PbContent)、`time=3`、`author_id=4`、
 * `title=5`、`floor=6`、`author=7`(`User`)、`agree=9`(`Agree`)。
 */
data class SubPostList(
    val id: Long = 0,
    val content: List<PbContent> = emptyList(),
    val time: Long = 0,
    val authorId: Long = 0,
    val title: String = "",
    val floor: Int = 0,
    val author: User? = null,
    val agree: Agree? = null,
) {
    companion object {
        fun parse(r: ProtoReader): SubPostList {
            var id = 0L
            var content = emptyList<PbContent>()
            var time = 0L
            var authorId = 0L
            var title = ""
            var floor = 0
            var author: User? = null
            var agree: Agree? = null
            while (r.hasMore) {
                val tag = r.readTag()
                when (tag ushr 3) {
                    1 -> id = r.readInt64()
                    2 -> content = content + PbContent.parse(r.readMessage())
                    3 -> time = r.readInt64()
                    4 -> authorId = r.readInt64()
                    5 -> title = r.readString()
                    6 -> floor = r.readInt32()
                    7 -> author = User.parse(r.readMessage())
                    9 -> agree = Agree.parse(r.readMessage())
                    else -> r.skip(tag and 7)
                }
            }
            return SubPostList(id, content, time, authorId, title, floor, author, agree)
        }
    }
}

/** `SubPost` 楼中楼容器。`pid=1`、`sub_post_list=2`(repeated `SubPostList`)。 */
data class SubPost(
    val pid: Long = 0,
    val subPostList: List<SubPostList> = emptyList(),
) {
    companion object {
        fun parse(r: ProtoReader): SubPost {
            var pid = 0L
            var list = emptyList<SubPostList>()
            while (r.hasMore) {
                val tag = r.readTag()
                when (tag ushr 3) {
                    1 -> pid = r.readInt64()
                    2 -> list = list + SubPostList.parse(r.readMessage())
                    else -> r.skip(tag and 7)
                }
            }
            return SubPost(pid, list)
        }
    }
}

/**
 * `ThreadInfo` 主题帖摘要。
 *
 * `id=1`、`title=3`、`reply_num=4`、`view_num=5`、`last_time_int=7`、
 * `is_top=9`、`is_good=10`、`author=18`、`last_replyer=19`、`thread_type=26`、
 * `fid=27`、`fname=28`、`first_post_id=40`、`create_time=45`、`post_id=52`、
 * `author_id=56`、`valid_post_num=57`、`is_ad=59`、`agree=126`、
 * `share_num=135`、`first_post_content=142`、`is_deleted=181`、`collect_num=190`。
 */
data class ThreadInfo(
    val id: Long = 0,
    val title: String = "",
    val replyNum: Int = 0,
    val viewNum: Int = 0,
    val lastTimeInt: Long = 0,
    val isTop: Boolean = false,
    val isGood: Boolean = false,
    val threadType: Int = 0,
    val fid: Long = 0,
    val fname: String = "",
    val firstPostId: Long = 0,
    val createTime: Long = 0,
    val postId: Long = 0,
    val authorId: Long = 0,
    val validPostNum: Int = 0,
    val isAd: Boolean = false,
    val agreeNum: Int = 0,
    val shareNum: Int = 0,
    val isDeleted: Boolean = false,
    val collectNum: Int = 0,
    val author: User? = null,
    val lastReplyer: User? = null,
    val firstPostContent: List<PbContent> = emptyList(),
) {
    val authorName: String get() = author?.displayName ?: ""

    /**
     * 帖子作者名，按设置决定是否同时显示用户名与昵称。
     *
     * 拿不到作者对象（例如吧详情里只有昵称字符串）时回退到 [authorName]。
     */
    fun authorName(showBoth: Boolean): String =
        (author?.displayNameWith(showBoth) ?: authorName).ifBlank { "匿名用户" }

    companion object {
        fun parse(r: ProtoReader): ThreadInfo {
            var id = 0L
            var title = ""
            var replyNum = 0
            var viewNum = 0
            var lastTimeInt = 0L
            var isTop = false
            var isGood = false
            var threadType = 0
            var fid = 0L
            var fname = ""
            var firstPostId = 0L
            var createTime = 0L
            var postId = 0L
            var authorId = 0L
            var validPostNum = 0
            var isAd = false
            var agreeNum = 0
            var shareNum = 0
            var isDeleted = false
            var collectNum = 0
            var author: User? = null
            var lastReplyer: User? = null
            val firstPostContent = ArrayList<PbContent>()
            while (r.hasMore) {
                val tag = r.readTag()
                when (tag ushr 3) {
                    1 -> id = r.readInt64()
                    3 -> title = r.readString()
                    4 -> replyNum = r.readInt32()
                    5 -> viewNum = r.readInt32()
                    7 -> lastTimeInt = r.readInt64()
                    9 -> isTop = r.readBool()
                    10 -> isGood = r.readBool()
                    18 -> author = User.parse(r.readMessage())
                    19 -> lastReplyer = User.parse(r.readMessage())
                    26 -> threadType = r.readInt32()
                    27 -> fid = r.readInt64()
                    28 -> fname = r.readString()
                    40 -> firstPostId = r.readInt64()
                    45 -> createTime = r.readInt64()
                    52 -> postId = r.readInt64()
                    56 -> authorId = r.readInt64()
                    57 -> validPostNum = r.readInt32()
                    59 -> isAd = r.readBool()
                    126 -> agreeNum = Agree.parse(r.readMessage()).agreeNum
                    135 -> shareNum = r.readInt32()
                    142 -> firstPostContent += PbContent.parse(r.readMessage())
                    181 -> isDeleted = r.readBool()
                    190 -> collectNum = r.readInt32()
                    else -> r.skip(tag and 7)
                }
            }
            return ThreadInfo(
                id = id, title = title, replyNum = replyNum, viewNum = viewNum,
                lastTimeInt = lastTimeInt, isTop = isTop, isGood = isGood,
                threadType = threadType, fid = fid, fname = fname, firstPostId = firstPostId,
                createTime = createTime, postId = postId, authorId = authorId,
                validPostNum = validPostNum, isAd = isAd, agreeNum = agreeNum,
                shareNum = shareNum, isDeleted = isDeleted, collectNum = collectNum,
                author = author, lastReplyer = lastReplyer, firstPostContent = firstPostContent,
            )
        }
    }
}

/**
 * `Post` 楼层。
 *
 * `id=1`、`floor=3`、`time=4`、`content=5`(repeated `PbContent`)、
 * `sub_post_number=13`、`author_id=19`、`sub_post_list=15`(`SubPost`)、
 * `author=23`(`User`)、`agree=37`(`Agree`)、`tid=46`。
 */
data class Post(
    val id: Long = 0,
    val floor: Int = 0,
    val time: Long = 0,
    val content: List<PbContent> = emptyList(),
    val subPostNumber: Int = 0,
    val authorId: Long = 0,
    val subPostList: SubPost? = null,
    val author: User? = null,
    val agreeNum: Int = 0,
    val disagreeNum: Int = 0,
    val tid: Long = 0,
) {
    val authorName: String get() = author?.displayName ?: ""
    val plainText: String get() = content.toPlainText()
    val subPosts: List<SubPostList> get() = subPostList?.subPostList ?: emptyList()

    companion object {
        fun parse(r: ProtoReader): Post {
            var id = 0L
            var floor = 0
            var time = 0L
            val content = ArrayList<PbContent>()
            var subPostNumber = 0
            var authorId = 0L
            var subPostList: SubPost? = null
            var author: User? = null
            var agreeNum = 0
            var disagreeNum = 0
            var tid = 0L
            while (r.hasMore) {
                val tag = r.readTag()
                when (tag ushr 3) {
                    1 -> id = r.readInt64()
                    3 -> floor = r.readInt32()
                    4 -> time = r.readInt64()
                    5 -> content += PbContent.parse(r.readMessage())
                    13 -> subPostNumber = r.readInt32()
                    15 -> subPostList = SubPost.parse(r.readMessage())
                    19 -> authorId = r.readInt64()
                    23 -> author = User.parse(r.readMessage())
                    37 -> {
                        val a = Agree.parse(r.readMessage())
                        agreeNum = a.agreeNum
                        disagreeNum = a.disagreeNum
                    }
                    46 -> tid = r.readInt64()
                    else -> r.skip(tag and 7)
                }
            }
            return Post(
                id = id, floor = floor, time = time, content = content,
                subPostNumber = subPostNumber, authorId = authorId, subPostList = subPostList,
                author = author, agreeNum = agreeNum, disagreeNum = disagreeNum, tid = tid,
            )
        }
    }
}