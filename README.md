# 贴吧 Miuix

一个 **miuix（小米 HyperOS 设计语言）风格**的贴吧 Android 客户端。

使用 Kotlin + Jetpack Compose 编写，UI 层基于 [compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix)，
贴吧接口协议参考 [lumina37/aiotieba](https://github.com/lumina37/aiotieba) 复现（protobuf 编解码 + 签名算法），
工程结构与功能对标 [zzc10086/TiebaLite](https://github.com/zzc10086/TiebaLite)。

## 功能

**浏览（不需要登录）**

- **首页推荐流**：默认展示全站推荐帖（`/c/f/forum/hotThreadList`，cmd=309661，匿名可读），
  卡片含作者、回复数、赞数、首楼正文预览与配图；首页顶部可在「推荐 / 热聊 / 逛吧」之间切换。
- **热聊（热话题榜）**：`/c/f/recommend/topicList`（cmd=309289，匿名可读），
  双列卡片展示话题配图、话题名、简介与讨论数，点进去是话题详情页
  （`/mo/q/newtopic/topicDetail`），列出该话题下最热的帖子，可直接点进帖子。
- **吧列表页**：输入吧名进入（输入时会实时给出候选吧，避免拼错）；三种排序（回复时间 / 最新发布 / 热门）；
  帖子卡片展示标题、正文预览、精品标记、作者、回复数、赞数；下拉刷新 + 上拉加载更多；
  悬浮按钮可切换为回顶 / 刷新 / 隐藏。
- **帖子详情页**：楼层列表（楼主 / N 楼、时间、正文、楼中楼、赞数）、只看楼主、倒序、
  跳页、下拉刷新 + 上拉加载更多、分享、复制链接、进入所属吧。
- **楼中楼**：楼层内「查看 N 条回复」按需拉取（`/c/f/pb/floor`，匿名可读），
  可继续「查看更多回复」，也能直接回复某条楼中楼（带 `quote_id` / `reply_uid`）。
- **阅读位置记忆**：离开帖子时记住读到哪一层，下次打开自动跳回并提示。
- **图片查看器**：多图网格 → 全屏查看，支持双击放大、双指缩放与拖动平移；多图之间左右滑动。
- **搜索页**：三种搜索并列——搜吧 / 搜帖 / 搜人，外加「吧内搜帖」；
  带搜索历史（可单条删除、可清空）。
- **浏览历史页**：帖子与吧两个 Tab，按今天 / 昨天 / N 天前 / 日期分组。
- **吧详情页**：吧头像、会员数、帖子数、分类、吧简介，以及吧内主题帖列表。
- **用户资料页**：头像、昵称、性别、吧龄、IP 归属地、获赞 / 粉丝 / 关注数、个性签名，
  以及「他的主题 / 他的回复」两个 Tab（点击帖子标题或作者昵称可进入）。
- **帖子长按菜单**：打开、进入所属吧、看该作者、复制链接、分享。

**需要登录**

- **登录**：内置 WebView 打开官方登录页，登录成功后从 Cookie 里取 `BDUSS` / `STOKEN`，
  再单独取一次发帖令牌 `tbs`（只带 BDUSS 能读不能写）。
- **账号管理**（设置页）：显示昵称与登录状态、一键刷新 `tbs`、退出登录（带确认）。
- **回帖**：帖子详情页右下角悬浮按钮，未登录 / 缺 `tbs` 时给出明确提示而不是让服务端拒绝；
  也可以回复某条楼中楼。
- **点赞**：楼层底部的点赞 / 取消赞（`/c/c/agree/opAgree`，楼层用 `obj_type=1`、楼中楼用 `3`）。
- **发主题帖**：吧详情页右下角悬浮按钮，可发带标题或不带标题的主题帖。
- **关注用户**：用户资料页的关注 / 取关（`/c/c/user/follow`、`/c/c/user/unfollow`）。
- **收藏**：帖子详情顶栏收藏按钮、帖子长按菜单收藏；「我的」页可以查看收藏的帖子
  （长按可取消收藏）。
- **我的页**：账号卡片（未登录时可直接登录），**关注的吧列表**（含等级与签到状态，可一键签到全部），
  最近逛过的吧，以及「我的帖子与回复」、收藏、浏览历史、搜索历史、设置的快捷入口；
  二级入口点开是全屏二级页面，返回键即可回到「我的」。
- **屏蔽规则**（设置 → 屏蔽管理）：关键词（支持正则）与用户的**黑名单 / 白名单**，
  白名单优先于黑名单；列表页命中即整条隐藏，帖子详情页折叠成「这一层被屏蔽规则过滤了 / 仍要查看」，
  帖子长按菜单里可以直接「屏蔽楼主」。
- **关注吧**：「我的 → 关注的吧」列出所有关注的吧；吧详情页可关注 / 取关。
- **签到**：吧详情页签到单个吧；「我的 → 关注的吧」可一键签到全部关注的吧。
- **消息中心**：底部导航「消息」Tab，分「回复我的」与「@我的」两栏，支持下拉刷新与翻页；
  点一条消息直接跳到对应帖子并定位到那一层，点头像进入用户页，未读消息在 Tab 上显示角标。
  数据走 App 表单通道（`/c/u/feed/replyme`、`/c/u/feed/atme`），未读数取 `/c/s/msg` 的 `bookmark=1`。

**外观与设置**：深色模式（跟随系统 / 常开 / 常关）、6 档字号缩放、深色模式下降低图片亮度、
图片加载策略（智能省流量 / 仅 Wi-Fi 不加载 / 始终加载 / 始终不加载）、默认排序、
悬浮按钮行为、是否同时显示用户名与昵称、是否记录浏览历史。

> 「赞我的」**没有实现**：对应的网页版接口（`/mo/q/message/agreeme`）在客户端请求头下只会
> 返回 HTML 错误页，需要 App 私有公共参数才能拿到 JSON，做出来也只会是空壳。

## 技术要点

| 层面 | 说明 |
| --- | --- |
| UI | miuix Compose 组件（Scaffold / SmallTopAppBar / Card / Button / TextField / PullToRefresh / TabRow / NavigationBar / OverlayDialog / MiuixIcons），`MiuixTheme` 提供浅色/深色与动态取色 |
| App 协议 | 手写 protobuf 编解码（`ProtoCodec`），复现贴吧客户端签名（`Sign`，参数按 key 升序拼接 + `tiebaclient!!!` 盐，MD5），multipart 请求（`TiebaHttp`，`x_bd_data_type: protobuf`） |
| 网页协议 | `WebHttp` 走 `tieba.baidu.com` 的 `/mo/q/*`、`/mg/o/*`、`/c/*` 通道；搜索类接口用 `PC_SALT` 签名，表单接口用 `APP_SALT` 签名。**必须带 Chrome Mobile UA + `X-Requested-With: com.baidu.tieba`**，否则返回 HTML 而不是 JSON |
| 图片 | Coil 3（`coil-compose` + `coil-network-okhttp`），按设置决定是否真的发起请求 |
| 本地存储 | SharedPreferences + `org.json`：设置、浏览历史、搜索历史、账号凭证（`SettingsStore` / `HistoryStore` / `SearchHistoryStore` / `AccountStore`） |
| 状态传递 | 刻意不引入 navigation 依赖：一级页面用底部导航切换，二级页面（帖子详情 / 吧详情 / 用户资料 / 登录）各开一个 Activity，通过 `AppLocals.kt` 里的 `CompositionLocal` 拿到仓库与各 Store |
| 构建 | AGP 9.4.1、Kotlin 2.4.20、Gradle 9.8.0、JDK 21、minSdk 24 / targetSdk 37 |

## 目录结构

```
app/src/main/java/com/tieba/miuix/
├── MainActivity.kt                  # 入口：底部导航（首页 / 消息 / 搜索 / 我的）
├── TiebaApplication.kt              # Application：全局 Repository 与各 Store
├── data/
│   ├── TiebaRepository.kt           # 所有接口调用（读取 / 搜索 / 登录 / 写操作）
│   ├── AccountStore.kt              # 账号凭证（BDUSS / STOKEN / tbs / uid）
│   ├── SettingsStore.kt             # 设置项
│   ├── HistoryStore.kt              # 浏览历史（帖子 + 吧）
│   └── SearchHistoryStore.kt        # 搜索历史
├── protocol/                        # 贴吧协议层
│   ├── TiebaConst.kt                # 常量（host、cmd、salt、排序枚举…）
│   ├── ProtoCodec.kt                # protobuf 编解码（ProtoWriter / ProtoReader）
│   ├── Messages.kt                  # App 通道消息模型（ThreadInfo / Post / User / Page…）
│   ├── CommonReq.kt                 # 公共请求头
│   ├── FrsPageProto.kt              # 吧列表（cmd=301001）
│   ├── PbPageProto.kt               # 帖子列表（cmd=302001）
│   ├── HotThreadListProto.kt        # 首页推荐流（cmd=309661）
│   ├── TopicListProto.kt            # 热话题榜（cmd=309289）
│   ├── TopicDetail.kt               # 话题详情（/mo/q/newtopic/topicDetail）
│   ├── Messages2.kt                 # 消息中心模型（回复我的 / @我的 / 未读数）
│   ├── ForumDetailProto.kt          # 吧详情（cmd=303021，含吧内主题帖）
│   ├── ForumHome.kt                 # 首页热门吧 / 关注的吧
│   ├── BlockStore.kt               # 屏蔽规则（黑/白名单、正则）
│   ├── UserCard.kt                  # 用户名片
│   ├── UserPostFeed.kt              # 用户主题 / 回复
│   ├── WebMessages.kt               # 网页通道搜索结果模型
│   ├── FavoriteMessages.kt          # 收藏 / 关注 / 签到结果模型
│   ├── ReplyResult.kt               # 回帖结果模型
│   ├── Sign.kt                      # 签名算法
│   └── TiebaHttp.kt / WebHttp.kt    # OkHttp 请求（protobuf 通道 / 网页通道）
└── ui/
    ├── AppLocals.kt                 # CompositionLocal（仓库与各 Store）
    ├── ForumListPage.kt             # 吧列表页（也可嵌入首页的「逛吧」模式）
    ├── HomeFeedPage.kt              # 首页（推荐流 / 热聊 / 逛吧 三模式）
    ├── HotTopicPage.kt              # 热话题榜（双列卡片）
    ├── TopicDetailPage.kt           # 话题详情页
    ├── TopicDetailActivity.kt       # 话题详情 Activity 外壳
    ├── ProfilePage.kt               # 「我的」页（账号 / 关注的吧 / 最近逛过 / 快捷入口）
    ├── BlockPage.kt                 # 屏蔽管理（关键词 / 用户 黑名单与白名单）
    ├── ThreadDetailPage.kt          # 帖子详情页
    ├── ThreadDetailActivity.kt      # 帖子详情 Activity 外壳
    ├── ForumDetailActivity.kt       # 吧详情页
    ├── UserPage.kt / UserPageActivity.kt   # 用户资料页
    ├── SearchPage.kt                # 搜索页
    ├── MessagesPage.kt              # 消息中心（回复我的 / @我的）
    ├── FavoritesPage.kt             # 收藏的帖子
    ├── HistoryPage.kt               # 浏览历史页
    ├── SettingsPage.kt              # 设置页（含账号）
    ├── LoginActivity.kt             # WebView 登录
    ├── ImageViewer.kt               # 多图网格 + 全屏图片查看器
    ├── ReplyDialog.kt               # 回帖编辑框
    ├── ThreadActionSheet.kt         # 帖子长按菜单
    ├── SearchThreadActionSheet.kt   # 搜索结果长按菜单
    ├── UiComponents.kt              # 公共组件（SafeImage / MessageCard / 设置行…）
    ├── UiHelper.kt                  # 格式化与分享工具
    └── theme/Theme.kt               # MiuixTheme 应用主题（深色模式 + 字号缩放）
```

## 本地构建

```bash
# 需要 JDK 21 与 Android SDK（compileSdk 37 / build-tools 37.0.0）
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk

./gradlew assembleRelease
# 产物：app/build/outputs/apk/release/app-release-unsigned.apk
```

未配置签名时会产出**未签名** APK，可直接安装用于调试。

> Windows 提示：工程路径不能包含非 ASCII 字符（AGP 会拒绝），必要时用目录联接
> （`mklink /J`）映射到一个纯 ASCII 路径后再构建。`local.properties` 里写
> `sdk.dir=...` 指向 Android SDK。

### 签名（可选）

在项目根目录的 `local.properties` 中写入：

```properties
KEYSTORE_PATH=/绝对路径/release.jks
KEYSTORE_PASS=你的口令
KEY_ALIAS=你的别名
KEY_PASSWORD=密钥口令
```

也可改用同名环境变量。

## 使用 GitHub Actions 构建

工作流位于 [`.github/workflows/build.yml`](.github/workflows/build.yml)：

- **触发**：手动（workflow_dispatch）、推送到 `main`/`master`、推送 `v*` tag、对 `main`/`master` 的 PR。
- **流程**：Checkout → 安装 JDK 21 (temurin) → `chmod +x gradlew` →（可选）解码 keystore →
  `./gradlew assembleRelease`（失败自动回退 `assembleDebug`）→ 上传 APK artifact。
- **发版**：推送 `v*` tag 时，APK 会自动附加到对应 Release。

构建完成后在 Actions 运行页面的 **Artifacts** 里下载 `tieba-miuix-apk`，
解压后是 `release/app-release-unsigned.apk`。

### 配置签名 Secret（可选）

在仓库 `Settings → Secrets and variables → Actions` 添加：

| Secret | 说明 |
| --- | --- |
| `KEYSTORE_BASE64` | keystore 文件的 base64 字符串，生成：`base64 -w0 release.jks` |
| `KEYSTORE_PASS` | keystore 口令 |
| `KEY_ALIAS` | 密钥别名 |
| `KEY_PASSWORD` | 密钥口令 |

不配置也能正常构建（release 为未签名 APK）。

## 免责声明

本项目仅用于学习与技术研究。贴吧接口与数据版权归百度所有，请勿用于商业用途。
