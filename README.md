# 做题小助手

基于 [HanakoAI](https://github.com/zyf2007/HanakoAI) 改良的 Android 悬浮窗 AI 搜题工具，由 [lxy005tt-collab](https://github.com/lxy005tt-collab) 独立维护。感谢原作者与上游贡献者。

[项目主页](https://github.com/lxy005tt-collab/ZuotiAssistant) · [问题反馈](https://github.com/lxy005tt-collab/ZuotiAssistant/issues) · [发行页面](https://github.com/lxy005tt-collab/ZuotiAssistant/releases) · [来源说明](NOTICE.md) · [AGPL-3.0](LICENSE)

左球打开设置，右球截取整屏并交给 AI。答案在同一个悬浮窗口里逐字显示，复制、重试和追问也都可以在小窗中完成。

当前源码版本为 `0.1.0-alpha`。本仓库尚未发布正式 APK，可按照下方说明自行构建；发行页面有安装包后再直接下载。本项目不是 HanakoAI 官方发行版。

## 相比原版的主要改良

- **一体式双球悬浮窗**：入口、设置和答案共用一个窗口，支持拖动；点击外部空白处可收起。
- **整屏截图直送 AI**：右球一键截图并发送，不再要求框选区域；等待时向右展开并显示蓝色流光，不显示截图或思考文字提示。
- **精简答案气泡**：第一段答案到来后淡入白底黑字气泡，无圆角外投影，随文字增长至高度上限后滚动；输出结束后显示复制、重试、追问三个图标。
- **模型思考强度**：可选自动、低、中、高等档位，实际可用选项取决于模型和接口。
- **两种截图方案**：主页直接切换系统录屏或 Shizuku，问号按钮说明各自优缺点。
- **可调悬浮窗**：尺寸默认 75%，支持调整尺寸、背景透明度、答案字号、逐字显示和空白处收起。
- **更清晰的主页**：多模态模式排在前面，首次使用默认多模态；浅色与深色模式使用独立配色。
- **精简服务接入**：移除 Kirari Network 的登录、额度和专属网关，使用自行配置的模型 API；兼容旧设置及历史记录。
- **图标与稳定性调整**：更新应用及悬浮窗图标，修复展开闪退、标签裁切，调整气泡收起动画。

## 快速开始

1. 安装应用，在「设置 → 模型提供方」填写 API 地址、API Key 和接口类型。
2. 在「模型设置」中选择多模态模型。默认模式会发送截图，因此所选模型必须支持图像输入；也可改用 OCR 模式。
3. 在主页选择截图方案，打开悬浮窗权限，再启动悬浮球。
4. 切换到需要阅读的题目页面，点击右球开始截图解题。
5. 答案输出结束后，可点击下方图标复制、重试或追问。下一题直接再次点击右球。

收起气泡会保留当前答案和正在生成的任务。首次模型配置与系统授权仍需在主界面完成。

### 左球快捷菜单

| 入口 | 用途 |
| --- | --- |
| 答案 | 展开当前答案 |
| 视觉 | 切换多模态 / OCR；高亮表示多模态 |
| 联网 | 开启或关闭联网搜索 |
| 设置 | 打开应用主界面 |

### 两种处理模式

| 模式 | 处理方式 | 适用场景 |
| --- | --- | --- |
| 多模态视觉（默认） | 直接把截图发送给支持视觉的模型 | 图表、几何图、公式与混合内容 |
| OCR | 先识别文字，再交给文本模型 | 以文字为主的题目 |

Full 版本内置 ML Kit 中文 OCR；Lite 版本不包含本地 OCR，可配置云端 OCR。

## 截图方案

| 方案 | 优点 | 使用条件与限制 |
| --- | --- | --- |
| 系统录屏 | 无需安装额外工具，按系统提示授权即可 | 新的录屏会话需要确认，使用时会有屏幕共享提示 |
| Shizuku | 按需截取单张图片，无需启动系统录屏会话 | 需安装、启动并授权 Shizuku；通过调试启动后，手机重启需重新启动服务 |

两种方案都可能无法截取受保护的页面，Shizuku 不保证避开应用检测。切换方案会停止当前悬浮窗，需要重新启动。

截图会发送至你配置的模型服务；启用联网搜索后，模型可能将搜索词交给配置的搜索服务。

正常解题的截图会保存在应用私有目录的 `history_images` 中，可从「设置 → 历史记录 → 题目详情」查看，不会自动保存到系统相册。右球每次截图创建独立题目，不会附带上一题的聊天；只有当前题目的「追问」会延续该题上下文。

Android 12 及以上会尝试在截图合成时排除本应用的悬浮窗图层，让屏幕上的悬浮窗和展开动画保持可见、截图中的题目保持完整。这依赖系统内部接口，兼容性因系统而异；接口不可用时会短暂隐藏悬浮窗，截取后再展开。此功能只处理本应用的悬浮窗。

## 模型与功能

支持 OpenAI Compatible、OpenAI Responses、Anthropic 和 Google Gemini 接口。OCR、文本、多模态任务可分别指定提供方和模型。

- 支持流式答案、Markdown / LaTeX、历史记录、答案版本切换、片段引用和追问。
- 支持 Tavily、Brave Search、Serper 及 Tavily 兼容搜索接口。
- 支持手动输入模型名、收藏模型和连接测试。
- 思考强度默认「自动」，不额外附加思考参数；OCR 识字请求不受该设置影响。较高强度可能增加等待时间与用量。
- 应用更新与下载入口指向本仓库；原项目链接仅用于来源说明。

## 本地构建

需要 JDK 17、Android SDK 36，以及项目自带的 Gradle Wrapper。Windows 建议使用纯英文路径（如 `D:\Projects\ZuotiAssistant`），避免 AIDL 工具处理路径时出错。

```bash
git clone https://github.com/lxy005tt-collab/ZuotiAssistant.git
cd ZuotiAssistant
./gradlew :app:assembleFullDebug
```

Windows 使用 `gradlew.bat`。Android SDK 路径可通过本机 `local.properties` 或环境变量配置，勿提交本机配置与密钥。

如需与已有不同签名的 Hanako 并存：

```bash
./gradlew :app:assembleFullDebug -PhanakoParallelDebug=true
```

该选项沿用旧构建参数名，生成「做题小助手 测试版」，应用 ID 为 `fun.kirari.hanako.dev`。默认包名保留为 `fun.kirari.hanako`，便于同签名版本覆盖升级并保留数据；独立仓库并不改变 Android 的签名规则。

运行检查：

```bash
./gradlew :app:testFullDebugUnitTest :kirari-llm-core:testDebugUnitTest
```

签名发布与 Full / Lite、arm64 / 32 位变体说明见 [发布流程](docs/release-process.md)。`kirari-llm-core` 是保留的通用模型协议模块名称，不包含 Kirari Network 专属服务接入。

## 来源与许可证

本项目基于 [zyf2007/HanakoAI](https://github.com/zyf2007/HanakoAI) 修改，保留上游提交历史、原版权声明和 [GNU AGPL-3.0 许可证](LICENSE)。改良说明及修改日期见 [NOTICE.md](NOTICE.md)。

源码以 AGPL-3.0 许可提供，不附带任何担保。第三方依赖适用各自许可证。

## Changelog

### 0.1.0-alpha · 2026-11-08

- 以「做题小助手」名称建立独立维护的项目，保留 HanakoAI 来源与许可证。
- 集成双球一体式悬浮窗、整屏截图、流式答案与快捷复制、重试、追问。
- 支持模型思考强度、系统录屏 / Shizuku 选择及可调悬浮窗显示。
- 调整主页配色、模式顺序与默认值，修复悬浮窗生命周期和布局问题。
- 移除 Kirari Network 专属接入，增加旧配置兼容处理。
