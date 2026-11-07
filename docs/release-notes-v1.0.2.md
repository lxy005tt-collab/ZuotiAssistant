做题小助手 **1.0.2**，增加主页隐匿模式。基于 [HanakoAI](https://github.com/zyf2007/HanakoAI) 改良，遵循 AGPL-3.0。

## Changelog

- 主页「截屏方案」下方增加「隐匿模式」开关，设置持久化、即时生效；重置悬浮窗外观不会改变这个开关。
- 开启后尝试在系统截图、录屏和投屏中透明排除本应用悬浮图层；手机上小球、菜单、答案、追问及区域选择仍照常显示。
- 窗口图层重建后重新请求隐藏；接口失败时主页提示「不支持透明隐匿」，运行中也弹出提示。
- 仅使用透明排除，不加黑块、不主动禁止整屏截图。关闭隐匿模式后，本应用搜题截图仍会单独避开浮窗。

## 使用与兼容性

默认开启，沿用之前版本尝试隐藏悬浮图层的行为。主页可随时关闭。需要 Android 12 或更新系统；低版本显示不支持，其他功能照常使用。

**「已请求透明隐藏」表示系统接受了调用，不代表已验证实际截图或录屏。** 部分系统可能限制或忽略该隐藏接口。请分别使用系统截图和系统录屏确认；不支持时悬浮窗仍可能出现在捕获结果中。系统键盘、状态栏、通知和其它应用窗口不在本模式的隐藏范围内。

本次验证包含构建、自动测试和安装包检查；未进行真机截图 / 录屏验证。技术依据见 [Android SurfaceControl 图层排除接口](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/core/java/android/view/SurfaceControl.java)。

## 下载说明

应用本身要求 Android 7.0 及以上。新用户通常选择 `ZuotiAssistant-1.0.2-full-arm64-v8a.apk`。

| 安装包 | 用途 |
| --- | --- |
| `full-arm64-v8a` | 推荐，内置本地中文 OCR，适合大多数现代手机 |
| `full-armeabi-v7a` | 内置本地 OCR，适合支持 32 位应用的设备 |
| `lite-arm64-v8a` | 不含本地 OCR，使用多模态或云端 OCR |
| `lite-armeabi-v7a` | Lite 的 32 位版本 |
| `full-dev-upgrade-arm64-v8a` | 旧测试版和已有兼容包的覆盖升级通道 |

已有旧测试版或 `dev-upgrade` 包的用户，继续选择兼容升级包，**不要先卸载**，以保留配置和历史记录。两个通道均沿用此前签名、启用正式版优化，显示名称均为「做题小助手」；普通包与兼容包的数据各自独立。

## 源码与许可

安装包对应 `v1.0.2` 标签。项目来源与版权见 [NOTICE](https://github.com/lxy005tt-collab/ZuotiAssistant/blob/v1.0.2/NOTICE.md)，协议见 [AGPL-3.0](https://github.com/lxy005tt-collab/ZuotiAssistant/blob/v1.0.2/LICENSE)。
