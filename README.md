# 天刀助手 Cookie 读取器 (TiDaoCookieReader)

从已登录天刀助手的 WebView 中提取登录 Cookie 和角色信息，无需抓包。

📖 **[使用教程文档](https://docs.qq.com/aio/DRXhkU3NNY3VLSHdE?p=FxuxTS2JkoguXFfXY8ppSQ)**（腾讯文档，详细图文说明）

---

## 功能

- 自动读取天刀助手登录 Cookie（`access_token`、`openid`、`uin`、`appid` 等）
- 自动读取游戏角色信息（支持多角色账号选择）
- 检测每日福利绑定状态
- 一键复制全部信息到剪贴板

## 使用前提

- 已 Root 的 Android 模拟器（推荐 MuMu 模拟器并开启 Root 权限）
- 已在模拟器上安装并扫码登录[天刀助手](https://sj.qq.com/appdetail/com.tencent.gamehelper.wuxia)

## 快速开始

### 1. 下载安装

前往 [GitHub Releases](https://github.com/1150260034/TiDaoCookieReader/releases/latest) 下载最新版 APK，安装到模拟器中。

### 2. 使用步骤

1. 打开天刀助手，扫码登录
2. 在天刀助手中点击「周周载愿」（使 WebView 加载完成）
3. 打开本 App → 点「读取 Cookie」→ 选择角色 → 点「复制全部」

详细图文教程请查看：[使用教程文档](https://docs.qq.com/aio/DRXhkU3NNY3VLSHdE?p=FxuxTS2JkoguXFfXY8ppSQ)

---

> 开发者文档请参阅 [CLAUDE.md](./CLAUDE.md)

## License

MIT License
