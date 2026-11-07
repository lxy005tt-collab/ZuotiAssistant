做题小助手 **1.0.1**，图标与名称修正版。基于 [HanakoAI](https://github.com/zyf2007/HanakoAI) 改良并独立维护，遵循 AGPL-3.0。

## Changelog

- 换用圆脸、大眼睛、微笑的黑白低多边形猫，桌面图标与首页 / 设置顶部栏使用同一份 SVG。
- 同步更新自适应、主题和不同密度图标。
- 修复兼容升级包在桌面和最近任务页仍显示「做题小助手 测试版」的问题，统一为「做题小助手」。包名与签名保持原升级通道，保留配置和历史记录。

## 下载说明

Android 7.0 及以上。**新用户通常选择 `ZuotiAssistant-1.0.1-full-arm64-v8a.apk`。**

| 安装包 | 用途 |
| --- | --- |
| `full-arm64-v8a` | 推荐，内置本地中文 OCR，适合大多数现代手机 |
| `full-armeabi-v7a` | 内置本地 OCR，适合支持 32 位应用的设备 |
| `lite-arm64-v8a` | 不含本地 OCR，使用多模态或云端 OCR |
| `lite-armeabi-v7a` | Lite 的 32 位版本 |
| `full-dev-upgrade-arm64-v8a` | 原测试版或之前兼容包的覆盖升级通道 |

已安装旧测试版或之前 `dev-upgrade` 包的用户，**继续选择 `full-dev-upgrade-arm64-v8a`，无需卸载**。升级后显示名称变为「做题小助手」，原包名 `fun.kirari.hanako.dev` 和测试证书继续保留；不要改装普通包来替代覆盖升级。

普通包沿用 1.0.0 的正式签名，包名为 `fun.kirari.hanako`。两个通道均启用正式版优化、不可调试，数据各自独立。

## 验证范围

发布构建和内置发布检查通过；检查了安装包版本、应用 / 启动入口名称、图标资源与签名延续。现有单元测试 197 项通过、2 项跳过，无失败。本次未进行真机回归。

## 源码与许可

安装包对应 `v1.0.1` 标签。原项目来源与版权说明见 [NOTICE](https://github.com/lxy005tt-collab/ZuotiAssistant/blob/v1.0.1/NOTICE.md)，协议见 [AGPL-3.0](https://github.com/lxy005tt-collab/ZuotiAssistant/blob/v1.0.1/LICENSE)。
