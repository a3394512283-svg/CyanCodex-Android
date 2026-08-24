# CyanCodex-Android

沧蓝工坊 (CyanCodex) —— 我的世界（基岩版/网易版）指令助手 Android 端。

## 功能
- 指令库：用户上传 + 平台资源，分类浏览/搜索/一键复制/效果图
- 指令编辑器：**语法高亮 + 参数自动补全**（参考 CHelper）+ 整段粘贴自动分行
- AI 助手：内置 NX树指令手册 + System Shield 安全知识库，按量计费
- 聊天室 / 好友私聊 / 插件库（恶意代码安全扫描 + 审核）
- T显生成器 / 音乐生成器 / 穷举 / 滚动字幕 / T显JSON
- 悬浮窗（游戏内快速复制）/ 输入法模式
- 积分系统：签到 / 转账（手续费20%）/ AI 按量计费
- 客户端检测：签名校验 + 封禁第三方客户端/篡改

## 模块
| 模块 | 说明 |
|---|---|
| `app` | 主应用 |
| `reviewer` | 指令/插件审核工具（邀请码登录） |

## 技术栈
- Kotlin + Jetpack Compose (Material3) + Navigation Compose
- Retrofit / OkHttp / Gson
- minSdk 26 / targetSdk 36
- R8 混淆加固（含资源混淆）

## 构建
```bash
./gradlew :app:assembleRelease
```
需自行配置 `keystore.properties` 与 `local.properties`（未包含在本仓库），并准备 JDK 17+（AGP 9 要求）。

## 说明
- 服务器地址在 App 内以 XOR+Base64 混淆存储，不包含明文 IP；本仓库已脱敏为占位符 `cdn.example.com`，如需连接自己的服务端，请修改 `app/src/main/java/com/canglan/cyancodex/data/Session.kt` 中的 `SERVER_ENC`
- 网络请求携带 `X-App-Signature`（APK 签名证书 SHA-256），服务端白名单校验
- 内置插件（assets/plugins）为演示样例，正式插件经安全扫描后分发

> 我的世界为 Mojang/Microsoft 商标，本应用为第三方工具，与官方无关联。
> © 2026 世纪集团 · 沧蓝工坊工作室
