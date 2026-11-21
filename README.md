<p align="center">
  <img src="app/src/main/res/drawable-xxxhdpi/wraive_mark.png" width="112" height="112" alt="Wraive+ 图标" />
</p>

# Wraive+

为 Wear OS 打造的 AI 助手，在手腕上开启文字与语音对话。

Wraive+ 是 [Wraive](https://github.com/youshen2/Wraive) 的二次开发版本，由 [Foxelf Studio](https://github.com/Foxelf-Studio) 维护，基于原作者的 Kotlin 与 Jetpack Compose 实现继续开发。界面围绕手表的圆形屏幕、旋转表冠与底部边缘按钮设计；连接你选择的模型服务后，即可在手表上提问、翻译、使用工具并管理会话。

[本仓库](https://github.com/Foxelf-Studio/WraivePlus) · [上游仓库](https://github.com/youshen2/Wraive) · [问题反馈](https://github.com/Foxelf-Studio/WraivePlus/issues)

## 功能

- **模型与助手**：支持 OpenAI Chat Completions、OpenAI Responses、Anthropic 和 Google 接口；按供应商管理模型，自定义助手与生成参数。
- **文字与语音**：流式回复、语音输入、回复朗读、Markdown 与 LaTeX 公式显示；支持相册图片、拍照和文档附件；AI 回复显示助手名称。
- **语音转写**：系统识别、OpenAI 兼容、Google Cloud，以及 DeepSeek ASR（16 kHz PCM WAV，语种自动识别）。
- **搜索与工具**：联网搜索、MCP 工具、长期记忆、世界书、提示词转换和翻译工作台。
- **会话管理**：标题编辑、置顶、归档、临时对话、全局搜索，以及 Markdown / HTML 导出。
- **备份与恢复**：本地备份、S3 兼容云端加密备份；供应商配置通过加密文本分享和导入。
- **手表体验**：圆屏适配、表冠滚动、内置文件选择器、原生 Wear Material 3 组件、动态表面配色、自定义字体和中英文界面。

## 相对上游的改动

- 品牌更名为 **Wraive+**，强调色调整为 `#EEF3FF`。
- 新增 DeepSeek ASR 转写引擎：`POST /v1/tools/asr`，使用 16 kHz 单声道 PCM WAV，单次最长 120 秒。
- 输入页新增相册图片与拍照上传；图片读取支持 EXIF 方向校正。
- 输入草稿按会话持久化，退出或切后台不丢失；附件在进程重启后可恢复。
- 优化含图片会话的滚动：限制解码尺寸与内存、缓存 Markwon 解析结果、减少重复布局。
- 供应商配置分享由二维码改为 **AES-GCM 加密文本**，支持复制、系统分享、剪贴板粘贴导入；旧明文分享文本仍可导入。
- 修复无系统语音识别设备上的闪退，以及消息内图片空白的问题。

## 开始使用

1. 从首页底部菜单进入 **设置 → 模型与助手 → 模型供应商**，添加供应商，填写接口类型、服务地址和 API Key。
2. 打开对应的供应商详情页，拉取模型列表或手动添加模型。
3. 在 **设置 → 模型与助手 → 助手** 中创建或编辑助手，为其选择模型。
4. 回到首页，通过底部菜单创建新对话或临时对话。

搜索、MCP、语音和云备份服务可在设置中按需配置。

连接 DeepSeek 官方 API（`api.deepseek.com`）时，`deepseek-flash` 及其官方兼容别名默认启用联网搜索和链接读取，由 DeepSeek 服务端执行，并保留引用来源与多轮网页上下文。`deepseek-v4-pro` 使用 Responses 或 Anthropic 协议时默认启用官方联网搜索。能力与接入格式见 [DeepSeek Web Tools](https://api-docs.deepseek.com/guides/web_tools/)、[Responses](https://api-docs.deepseek.com/guides/responses_api/) 和 [Anthropic](https://api-docs.deepseek.com/guides/anthropic_api/) 文档。

## 构建与安装

### 环境

- JDK 21。
- Android SDK Platform 37，以及 Android SDK Build-Tools、Platform-Tools。
- Android Studio 或命令行环境；使用仓库内的 Gradle Wrapper。
- Wear OS 手表或模拟器，应用最低 Android API 为 26。

### 构建调试版本

```bash
git clone --recurse-submodules https://github.com/Foxelf-Studio/WraivePlus.git
cd WraivePlus
./gradlew :app:assembleDebug
```

使用 Android Studio 打开项目时可由 IDE 配置 SDK 路径。命令行构建可通过 `ANDROID_HOME` 指定 SDK，或在本地 `local.properties` 中填写 `sdk.dir`。

已有工作副本先运行 `git submodule update --init --recursive`，以获取构建所需的 CN Wear Overlay 插件。

Windows 下使用 `gradlew.bat :app:assembleDebug`。调试 APK 输出至：

```text
app/build/outputs/apk/debug/app-debug.apk
```

开启手表的开发者选项和调试功能，完成 ADB 连接后安装：

```bash
adb devices
adb -s <device-serial> install -r app/build/outputs/apk/debug/app-debug.apk
```

### 验证

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebugAndroidTest
```

界面验证用例位于 `app/src/androidTest`，可在 Android Studio 中选择目标 Wear OS 模拟器运行。

### 发布签名

发布版请使用你自己的 release keystore，不要把 `.jks`、`keystore.properties` 或密码提交到仓库（`.gitignore` 已覆盖这些文件）。同一个 `applicationId` 必须始终使用同一把签名密钥，否则无法覆盖升级。签名后可用 `apksigner verify --print-certs app-release.apk` 校验。

## 项目结构

```text
app/src/main/java/moye/wear/wraive/
├── ui/          界面、导航、组件与主题
├── chat/        会话生成、提示词与工具调度
├── network/     模型服务、搜索、MCP 与网络连接
├── data/        数据存储、导入导出与备份
├── audio/       语音输入与朗读
├── model/       数据模型与配置
└── service/     后台生成服务

app/src/main/res/    Android 资源与自适应图标
```

## 作者与致谢

- 二次开发：[Foxelf Studio](https://github.com/Foxelf-Studio)
- 设计与开发（上游）：爅峫

本项目参考了 [Kelivo](https://github.com/Chevey339/kelivo)，感谢其带来的启发。

通过 [CN Wear Overlay](https://github.com/Star-ZER0/cnwearoverlay) 的 Gradle 插件适配小米 / OPPO 手表的表冠触觉与小米触觉常量；小米 Watch 5（M2505W1、M2501W1）使用系统返回导航。接入方式参考 [Orbit](https://github.com/PianoEthan/Orbit)。

第三方组件包括 Jetpack Compose、OkHttp、Gson、Markwon、ZXing（Apache-2.0）与 CN Wear Overlay（LGPL-3.0），分发时请保留各自的版权与许可声明。

## 修改版说明

本项目是 [Wraive](https://github.com/youshen2/Wraive) 的修改版，修改日期 2026-11-22，主要改动见上文。修改部分由 Foxelf Studio 完成。

## 许可证

Copyright (C) 2026 爅峫（上游作者）  
Copyright (C) 2026 Foxelf Studio（修改部分）

本项目采用 [GNU Affero General Public License v3.0](LICENSE) 发布，SPDX 标识为 `AGPL-3.0-only`。完整条款见 [LICENSE](LICENSE)。

修改版同样按 `AGPL-3.0-only` 发布，并保留原始版权与许可声明。分发本应用（包括通过网络提供服务）时，需要按 AGPL 第 13 条向用户提供对应的完整源代码；本仓库即为对应源码。
