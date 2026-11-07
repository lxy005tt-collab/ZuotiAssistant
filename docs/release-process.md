# 做题小助手发布流程

本项目独立发布至 [lxy005tt-collab/ZuotiAssistant](https://github.com/lxy005tt-collab/ZuotiAssistant)，应用更新检查也只读取该仓库。`docs/release-notes-v0.0.*` 是保留的上游历史记录，不代表本项目已经发布的版本。

## 发布准备

1. 更新 `app/build.gradle.kts` 中的 `versionName` 和递增的 `versionCode`。
2. 更新 README 的当前版本及 `## Changelog`，保留 HanakoAI 来源、NOTICE 与 LICENSE。
3. 使用固定的发布签名证书；签名变化会影响覆盖安装，不能依赖清除用户数据解决。
4. 根目录 `keystore.properties` 可配置 `storeFile`、`storePassword`、`keyAlias` 和 `keyPassword`。证书与密码仅保存在本机或发布环境，不提交到 Git。

也可用 `-PhanakoKeystoreProperties=<本机配置文件绝对路径>` 从源码目录之外读取签名配置，便于在本机独立保管证书与密码。

## 检查与构建

```bash
./gradlew :app:testFullDebugUnitTest :kirari-llm-core:testDebugUnitTest
./gradlew :app:assembleFullRelease :app:assembleLiteRelease
```

Windows 使用 `gradlew.bat`，建议把源码放在纯英文路径。

产物位于 `app/build/outputs/apk/<full|lite>/release/`，区分：

- Full：包含本地 ML Kit 中文 OCR。
- Lite：不包含本地 OCR，需使用云端 OCR 或多模态模型。
- arm64-v8a：适用于大多数现代 Android 手机。
- armeabi-v7a：用于支持 32 位应用的设备。

为已有 `.dev` 测试安装提供兼容升级包时，另行执行 `:app:assembleFullCompat`。
该构建继承 Release 的 R8 和资源压缩，关闭可调试标志，保留 `.dev` 包名和原 debug 签名。显示名称与正式版统一为「做题小助手」；覆盖升级和数据保留不依赖应用名称。
必须在持有原测试证书的同一环境构建；换一份 debug 证书就无法覆盖。发布时文件名含 `dev-upgrade`，并明确说明这是旧测试版兼容通道，不能与普通正式包混淆。

发布前检查包名、版本、安装与升级行为，并在设备上确认悬浮窗、截图授权和模型调用。不要把调试包标注成签名正式版。

## GitHub Release

- 标签与发布标题使用 `v<versionName>`，例如 `v1.0.0`。
- 从已经提交的源码创建标签，并上传同一版本、同一提交构建出的 APK。
- 发布正文的 `## Changelog` 写入本次变化，`## 下载说明` 说明 Full / Lite 与架构差异。
- 发布后才在 README 添加对应的真实下载链接，源码与 APK 应当可以相互对应。
- 应用优先查询 GitHub Latest Release；该 API 不返回预发布版本。只有作为普通 Release 发布的版本才通过此通道提示更新。
- README 回退解析识别 `[[Download <version>](<apk-url>)]` 和 `[[View Release Notes](<release-url>)]` 格式；未发布安装包时不要添加占位下载链接。

每次分发均应保留项目来源说明与 AGPL-3.0 许可证，并提供对应版本的源代码。
