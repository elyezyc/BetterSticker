# BetterSticker

一个跑在 LSPosed 里的 QQ 表情/语音收藏工具。

长按聊天输入框旁边的表情或语音图标，打开收藏面板；单击表情直接发送，长按可以预览、收藏、移动分组或删除。消息里的图片、表情和语音也可以通过 QQ 原生长按菜单收进来。

> 灵感来自 **FunBox**。BetterSticker 是一个独立实现，没有复制 FunBox 的图片、布局、索引格式或闭源代码。目标不是“复刻 FunBox”，而是把我自己常用的那部分做得更顺手一点。

当前版本：**1.0.0**（versionCode 100）  
目标 QQ：**9.3.75.42350**  
安装包名：**com.elyezyc.bettersticker**

---

## 现在能做什么

### 表情面板

- 长按聊天输入区的表情图标打开表情面板；
- 长按语音图标打开语音面板；
- 从 QQ 切到后台再回来，会重新挂载长按监听；
- 面板里有分组、收藏、导入、导出和发送。

### 发送 / 预览 / 收藏

- **单击表情**：直接发送到当前 QQ 聊天；
- **长按表情**：
  - 预览；
  - 收藏到“收藏”分组；
  - 移动到其他分组；
  - 删除。
- **长按分组**：
  - 收藏整组；
  - 按组导出 ZIP；
  - 重命名；
  - 删除分组。

### 分组

- 分组条：`全部`、`默认`、`收藏`、自定义分组；
- `＋` 新建分组；
- 删除分组时，组内表情移动到“默认”，不会删文件；
- 从 FunBox 迁移时，按 `.fun/Sticker/Storage/<表情包分组>` 的结构还原分组；
- 重新导入同一批文件时，会把旧条目更新到正确分组。

### 导入

面板右上角“导入”：

- **从 FunBox ZIP 导入**：
  - 选择 FunBox 导出的 ZIP；
  - 按 `Sticker/Storage/<分组>` 或 ZIP 内目录分组；
  - 识别 PNG / GIF / WebP / JPEG / SILK。
- **从 FunBox 迁移（原版素材目录）**：
  - 默认预填 `/sdcard/Android/media/com.tencent.mobileqq/.fun`；
  - 可以直接输入绝对路径；
  - 也可以点“选择目录”走系统目录选择器；
  - 不需要改 `trigger.json`。
- **从本地图片导入**：
  - 多选图片，归入“本地导入”分组。

### 收藏

- 系统“收藏”分组是一个特殊分组；
- 长按单个表情可以加入/移出收藏；
- 长按分组可以收藏整组；
- 收藏状态保存在 `index.json` 的 `favorite` 字段里。

### 导出

- 导出全部：`Download/BetterSticker/`；
- 按组导出：生成 `BetterSticker_<分组>_<时间>.zip`，内含 `manifest.json` 和原始图片/语音；
- 图片按原格式导出；
- SILK 语音只有在配置了解码器时才转 WAV，否则保留原始 SILK。

### QQ 原生菜单

- 长按消息里的图片/表情/语音，QQ 菜单中会出现“收藏到 BetterSticker”；
- 原生菜单插入失败时，会在屏幕顶部显示一个浮动按钮兜底；
- 不会下载聊天文件，只读取 QQ 已经落盘的本地文件。

### App 状态页

- 桌面打开 **BetterSticker**；
- 显示版本、注入状态、当前 Activity、Hook 计数、分组数量、收藏数量、数据目录；
- 提供“刷新状态”“打开收藏面板”“打开 QQ”“导出到 Download”“复制状态”；
- QQ 设置页里没有任何 BetterSticker 入口，也不会有浮卡。

---

## 安装

### 要求

- Android 10 及以上；
- LSPosed；
- QQ **9.3.75.42350**（其他版本可能可以跑，但类名/混淆名没有保证）；
- 只勾选 QQ 作用域。

### 安装步骤

```bash
adb install app/build/outputs/apk/release/app-release.apk
```

如果之前装过旧包名的 BetterSticker（`com.bettersticker`），需要先卸载旧版，因为包名和签名都变了：

```bash
adb uninstall com.bettersticker
adb install app/build/outputs/apk/release/app-release.apk
```

然后在 LSPosed 里：

1. 启用 BetterSticker；
2. 作用域勾选 QQ；
3. 强制停止 QQ 再打开。

> 卸载旧模块 App 不会删除 QQ 数据目录里的收藏；收藏在 QQ 自己的 `Android/media` 目录下。

---

## 使用

### 打开面板

进入任意聊天，长按输入框左侧的麦克风或右侧的表情图标。

### 发送

面板里单击任意表情，会调用 QQ 的 AIO 表情发送接口，发送到当前聊天。

如果提示“还没捕获到当前聊天”，先在当前聊天里正常发一条消息或切换一次聊天，再试。

### 收藏消息里的素材

长按聊天里的图片/表情/语音 → QQ 菜单 → “收藏到 BetterSticker”。

### 从 FunBox 迁移

面板 → 导入 → 从 FunBox 迁移：

```text
默认：/sdcard/Android/media/com.tencent.mobileqq/.fun
结构：.fun/Sticker/Storage/<表情包分组>/...
```

可以直接点“开始迁移”，也可以改成别的绝对路径，或者用“选择目录”。

---

## 数据放在哪

```text
/sdcard/Android/media/com.tencent.mobileqq/.bs/
├── index.json          # 收藏索引，schema 2
├── trigger.json        # 触发配置
├── stickers/           # 图片原文件
├── voices/             # SILK 原文件
└── probe*.log
```

- 旧版本内部目录会在首次启动时复制迁移；
- 外部 media 目录不可用时才回退到 QQ 内部 `files/bettersticker`；
- 导出的文件在 `Download/BetterSticker/`；
- 模块本身不申请 INTERNET、存储或无障碍权限。

---

## 目前还不够好的地方

这一版是 1.0.0，但不是“完美成品”。

1. **只针对 QQ 9.3.75.42350 做过反编译适配**  
   菜单、输入框、AIO 上下文都依赖混淆类名和方法名。QQ 一升级，可能需要重新核对。

2. **单击发送依赖 QQ 内部 AIO 接口**  
   `AIOEmoApiImpl.sendRandomPicEmotionMsgFromAIO` 和 AIOContext 捕获都是反射/注入实现。某些聊天类型或 QQ 小版本上可能失败；失败时请用长按预览。

3. **FunBox 分组规则是“按目录猜”**  
   现在按 `.fun/Sticker/Storage/<分组>` 取分组，不解析 FunBox 的内部数据库/索引。如果实际导出把分组信息放在 JSON 里，分组可能不准。

4. **FunBox ZIP 的格式没有官方文档**  
   当前支持“目录结构 + manifest 名称”的通用读法，不保证兼容所有 FunBox 版本导出的 ZIP。

5. **SILK 解码器没有内置**  
   语音可以收藏原始 SILK；没有配置解码器时，不会生成假 WAV。

6. **面板的 Material 组件是“受限 MD3”**  
   QQ 进程里直接使用 Material 组件会要求 `Theme.AppCompat`，所以面板采用 MD3 色板/圆角/胶囊形状的自绘实现；App 自己的页面使用真正的 Material 3。

7. **没有真机自动化测试**  
   当前仓库里的测试只覆盖 WAV 头、图片签名识别等纯 JVM 逻辑。QQ 注入、发送、文件选择器都需要手动验证。

8. **按组导出的 ZIP 是 BetterSticker 格式**  
   里面的 `manifest.json` 用于自己回读，不是 FunBox 官方导出格式。

9. **收藏/分组数据没有云同步**  
   只在本机；清除 QQ 数据会一起清掉。

10. **GPLv3 的“or later”需要保留声明**  
    本项目按 GPL-3.0-or-later 发布；再分发时请保留 `LICENSE`、`NOTICE` 和版权声明。

---

## 1.0.0 更新日志

第一个正式版本。

### 面板

- 长按 QQ 输入区表情/语音图标打开面板；
- 从后台回到 QQ 后重新挂载长按监听；
- 面板分组条、收藏、批量操作；
- App 自己的 Material 3 状态页。

### 发送与预览

- 单击表情发送到当前聊天；
- 长按菜单第一项为“预览”；
- 发送失败时给出提示，不吞掉原操作。

### 分组

- 默认 / 收藏 / 自定义分组；
- 新建、重命名、删除、移动；
- 按 FunBox 的 `Sticker/Storage/<分组>` 目录结构还原分组；
- 重复导入时更新旧条目的分组。

### 导入

- 从 FunBox ZIP 导入；
- 从 FunBox 目录迁移（默认 `.fun`、手输绝对路径、系统目录选择器）；
- 从本地图片导入；
- 图片格式签名校验：PNG / GIF / WebP / JPEG / SILK。

### 导出

- 导出全部到 `Download/BetterSticker/`；
- 按分组导出 ZIP；
- `manifest.json` 记录分组和条目；
- SILK 可转 WAV（需要解码器）。

### 收藏消息

- QQ 消息长按原生菜单“收藏到 BetterSticker”；
- 图片/表情/语音读取 QQ 本地文件；
- 原生菜单失败时顶部浮动按钮兜底。

### 数据与设置

- `index.json` schema 2：`groups`、`groupId`、`favorite`；
- 旧 schema 1 自动迁移；
- 收藏目录在 `/sdcard/Android/media/com.tencent.mobileqq/.bs`；
- 设置项全部在 BetterSticker 自己的 Activity 中，QQ 设置页无注入；
- 正式签名：`key/key.jks`；
- 包名：`com.elyezyc.bettersticker`。

---

## 构建

```bash
./gradlew assembleRelease
```

签名配置在 `key/keystore.properties`，不会提交到仓库。

---

## 许可证

BetterSticker 以 **GPL-3.0-or-later** 发布。

```text
Copyright (C) 2026 elyezyc
SPDX-License-Identifier: GPL-3.0-or-later
```

完整文本见 [LICENSE](LICENSE)；第三方组件（Xposed API、Material Components 等）仍遵循各自许可证。

## 参考与致谢

- **FunBox**：灵感来源，尤其是“在 QQ 里直接管理表情包”的产品思路。没有复制其代码、资源和闭源二进制。
- **QTool**：输入区图标按 `contentDescription` / 父布局识别的思路参考。
- **TCQT**：AIO 菜单注入和 `ui.e` 菜单项的实现思路参考。
- **Material Components for Android**：App 状态页的 Material 3 组件。
- 以及所有在酷安、GitHub、LSPosed 社区分享 QQ 结构信息的人。

---

## 免责声明

本项目仅用于个人学习和本地数据管理。请遵守 QQ 用户协议和当地法律；不要用于绕过风控、批量抓取、传播他人内容或任何商业用途。
