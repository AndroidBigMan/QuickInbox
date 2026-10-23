# 秒级收件箱 (QuickInbox)

<p align="center">
  <img src="app/src/main/res/drawable/ic_tile_inbox.xml" width="96" height="96" alt="QuickInbox Logo" />
</p>

<p align="center">
  <b>极简 · 毫秒级唤起 · 本地优先 · 完美兼容 Obsidian 的 Android 灵感速记利器</b>
</p>

<p align="center">
  <a href="https://github.com/AndroidBigMan/QuickInbox/blob/main/LICENSE"><img src="https://img.shields.io/badge/license-MIT-blue.svg" alt="License"></a>
  <a href="https://developer.android.com/about/versions/oreo"><img src="https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-green.svg" alt="Platform"></a>
  <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/UI-Jetpack%20Compose-purple.svg" alt="Compose"></a>
  <a href="https://obsidian.md/"><img src="https://img.shields.io/badge/Storage-Obsidian%20Compatible-7C3AED.svg" alt="Obsidian Compatible"></a>
  <img src="https://img.shields.io/badge/Network-Zero%20Permission-success.svg" alt="Zero Network Permission">
</p>

---

## 💡 为什么需要 QuickInbox？

在日常生活和工作中，灵感与待办事项往往转瞬即逝。现有的笔记软件普遍面临以下痛点：
1. **启动缓慢**：需要“返回桌面 -> 寻找 App -> 等待开屏广告/加载 -> 点击新建笔记 -> 键盘弹起”，耗时 5~10 秒，灵感早已被打断。
2. **云端绑架与隐私担忧**：强制依赖账号同步，有隐私泄露风险或离线不可用问题。
3. **格式壁垒**：许多备忘录使用专有数据库存储，难以与现代本地知识库（如 Obsidian、Logseq）无缝结合。

**QuickInbox（秒级收件箱）** 为解决这一痛点而生：
**从屏幕顶部下拉控制中心，轻点一下，半透明极简速记浮窗秒级弹出，键盘自动升起，打字或说话，回车或点击存入，窗口自动关闭。** 完全不打断你当前正在阅读或处理的任何任务。

---

## ✨ 核心特性

- ⚡ **控制中心秒级呼出 (`TileService`)**：
  - 在任何应用界面（刷社交软件、阅读、游戏中）或锁屏状态下，下拉系统控制中心点击「记灵感」磁贴，半透明毛玻璃浮窗瞬间弹起，软键盘自动对焦升起。
- 📝 **本地优先 & 完美兼容 Obsidian (`Storage Access Framework`)**：
  - 基于 Android SAF（存储访问框架），授权 Obsidian 仓库根目录即可。
  - 支持自定义追加文件名（默认 `Inbox.md`）与条目格式（如 `- **15:30** 灵感内容`）。
  - 若未选择外部目录，自动降级安全存放在 App 本地私有目录中，零门槛开箱即用。
- 🎙️ **智能语音自适应架构 (`SpeechRecognizer` + 输入法协同)**：
  - 针对系统底层离线模型进行自适应检测（Android 13+ `checkRecognitionSupport`）：
    - **系统支持离线中文**：自动在悬浮窗展示独立的麦克风录音按钮，支持一键语音输入与说完自动写入；
    - **系统缺少离线中文包**：如 Google 语音服务缺少中文离线包且无法连云端时，**智能隐藏不可用的录音按钮**，无缝引导用户直接点击软键盘上的语音键输入，界面极致简约无冗余；
    - **主设置页随时自检**：在主设置界面提供“重新检测”入口，用户随时可刷新底层语音引擎支持状态。
- 🔗 **系统级文本分享接入 (`ACTION_SEND`)**：
  - 在任何 App 中长按选中文字 -> 点击系统「分享」-> 选择「秒级收件箱」，直接将选中文本送入速记窗快速归档。
- 🔒 **纯粹安全的零网络权限 (`Zero Permission`)**：
  - **不申请 `android.permission.INTERNET` 权限**！从系统底层彻底杜绝任何数据外泄可能，完全离线运行，100% 保护私人笔记安全。
- 🎨 **现代混合架构体验 (`Hybrid Architecture`)**：
  - 核心存储、系统服务与配置基于 Java 健壮实现；UI 层基于 Kotlin Jetpack Compose 与 Material 3 打造，配合轻量触觉反馈（Haptics）与动效，手感顺滑。

---

## 🏗️ 架构设计

项目采用 **Java 业务服务层 + Kotlin Jetpack Compose 现代化 UI** 混合架构：

```
app/src/main/java/com/ai/quickcapture/
├── activity/
│   ├── MainActivity.java            # 主设置界面（Obsidian 库目录授权、模板配置与语音状态管理）
│   └── CaptureDialogActivity.java   # 悬浮速记浮窗 Activity（控制中心/分享唤起，秒开秒关）
├── service/
│   └── QuickCaptureTileService.java # Android 快捷设置磁贴服务 (Quick Settings Tile)
├── storage/
│   ├── SettingsManager.java         # SharedPreferences 配置持久化管理
│   └── MarkdownStorageManager.java  # SAF 线程安全 Markdown 追加写入引擎
├── voice/
│   └── SpeechVoiceManager.java      # 系统离线语音探测与生命周期调度
└── ui/
    ├── bridge/
    │   ├── CaptureUiState.kt        # 跨语言不可变 UI 状态契约 (Immutable State)
    │   └── UiBridge.kt              # ComposeView 与 Java 的 @JvmStatic 绑定桥梁
    ├── screen/
    │   ├── CaptureDialogScreen.kt   # 速记悬浮卡片 Compose 界面（自适应按键显隐）
    │   └── MainSettingsScreen.kt    # 主配置页 Compose 界面
    └── theme/
        └── Theme.kt                 # Material 3 主题系统与动态色彩
```

---

## 🚀 快速上手

### 1. 配置存储路径
1. 打开应用，进入主设置界面。
2. 点击 **选择 Obsidian 库目录**，系统会弹出文件选择器，导航到你的 Obsidian Vault（笔记库）根目录并点击「使用此文件夹」授权。
3. （可选）自定义目标文件名（如 `Inbox.md` 或 `Daily.md`）和行模板（支持占位符 `%TIME%` 与 `%CONTENT%`）。
4. 点击 **测试写入**，提示成功后即可在 Obsidian 中看到追加的内容。

### 2. 添加控制中心快捷磁贴
1. 从手机屏幕顶部向下滑动两次，打开快捷设置（控制中心）完整面板。
2. 点击「编辑」（笔状图标）。
3. 在下方未添加的快捷开关中找到 **「记灵感」**，将其拖动到前几位常用位置。
4. 之后在任何界面下拉轻点「记灵感」，即可秒级呼出速记窗口！

---

## 🔒 隐私与安全性

- **零网络访问**：未声明网络权限，App 无法发起任何网络请求，杜绝一切隐私收集与遥测。
- **沙盒权限隔离**：采用 Android 官方推荐的 SAF（Storage Access Framework）机制，仅对用户明确授权的笔记目录拥有读写权限，无法窥探设备其他文件。
- **无敏感日志泄露**：生产代码中不打印任何用户输入的笔记文字、剪贴板内容或敏感存储路径。
- **无第三方追踪 SDK**：整个项目只依赖 Google 官方 AndroidX 与 Jetpack Compose 核心基础库。

---

## 🛠️ 本地编译与构建

### 开发环境要求
- Android Studio Iguana (2023.2.1) 或更高版本
- JDK 17
- Android SDK 34 (Android 14)

### 编译命令
克隆仓库到本地：
```bash
git clone https://github.com/AndroidBigMan/QuickInbox.git
cd QuickInbox
```

使用 Gradle Wrapper 进行构建：
```bash
./gradlew assembleDebug
```

编译产物位置：
`app/build/outputs/apk/debug/app-debug.apk`

通过 ADB 直接安装至设备：
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 开源许可证

本项目采用 [MIT License](LICENSE) 开源许可证。
