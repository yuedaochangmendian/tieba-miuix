# 贴吧 Miuix

一个 **miuix（小米 HyperOS 设计语言）风格**的贴吧 Android 客户端。

使用 Kotlin + Jetpack Compose 编写，UI 层基于 [compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix)，
贴吧接口协议参考 [lumina37/aiotieba](https://github.com/lumina37/aiotieba) 复现（protobuf 编解码 + 签名算法），
工程结构与 CI 参考 [zzc10086/TiebaLite](https://github.com/zzc10086/TiebaLite)。

## 功能

当前定位为「浏览优先」，已实现：

- **吧列表页**：输入贴吧名进入；三种排序（回复时间 / 最新发布 / 热门）；
  帖子卡片展示标题、正文预览、精品标记、作者、回复数；下拉刷新 + 上拉加载更多。
- **帖子详情页**：帖子列表（楼主 / 楼层、时间、正文、楼中楼、赞数）；下拉刷新 + 上拉加载更多。
- 匿名浏览（无需登录），自动处理贴吧 `frs/page` 与 `pb/page` 两套接口。

## 技术要点

| 层面 | 说明 |
| --- | --- |
| UI | miuix Compose 组件（Scaffold / SmallTopAppBar / Card / Text / Button / TextField / PullToRefresh / MiuixIcons），`MiuixTheme` 提供浅色/深色与动态取色 |
| 协议 | 手写 protobuf 编解码（`ProtoCodec`），复现贴吧客户端签名（`Sign`，MD5 排序 + `tiebaclient!!!` 盐） |
| 网络 | OkHttp + multipart 请求（`TiebaHttp`），`TiebaRepository` 封装 `getThreads` / `getPosts` |
| 构建 | AGP 9.4.1、Kotlin 2.4.20、Gradle 9.8.0、JDK 21、minSdk 24 / targetSdk 37 |

## 目录结构

```
app/src/main/java/com/tieba/miuix/
├── MainActivity.kt              # 入口，页面切换状态机
├── TiebaApplication.kt          # Application，持有全局 Repository
├── data/TiebaRepository.kt      # 接口封装（吧列表 / 帖子）
├── protocol/                    # 贴吧协议层
│   ├── TiebaConst.kt            # 常量（host、cmd、salt、排序枚举…）
│   ├── ProtoCodec.kt            # protobuf 编解码
│   ├── Messages.kt              # 消息模型（ThreadInfo / Post / User …）
│   ├── CommonReq.kt             # 公共请求头
│   ├── FrsPageProto.kt          # 吧列表（cmd=301001）
│   ├── PbPageProto.kt           # 帖子列表（cmd=302001）
│   ├── Sign.kt                  # 签名算法
│   └── TiebaHttp.kt             # OkHttp + multipart 请求
└── ui/
    ├── ForumListPage.kt         # 吧列表 / 搜索页
    ├── ThreadDetailPage.kt      # 帖子详情页
    └── theme/Theme.kt           # MiuixTheme 应用主题
```

## 本地构建

```bash
# 需要 JDK 21 与 Android SDK（compileSdk 37）
./gradlew assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

未配置签名时会产出**未签名** APK，可直接安装用于调试。

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
