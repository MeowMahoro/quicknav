# Quick Nav

[English](README.md) | [中文](README.zh-CN.md)

![Build](https://github.com/MeowMahoro/quicknav/actions/workflows/build.yml/badge.svg)

> **AI 说明**：本独立移植版由 AI 辅助制作与适配，源码来自 [Skyblocker](https://github.com/SkyblockerMod/Skyblocker)。

独立的 **快速导航（Quick Navigation）** Fabric 模组 —— 从 [Skyblocker](https://github.com/SkyblockerMod/Skyblocker) 拆分而来，可单独装载于 Minecraft **26.2** 的 Fabric 环境，不依赖 Skyblocker 本体。

在 Hypixel SkyBlock 的容器菜单（如快捷栏、背包等）顶部/底部渲染 14 个可自定义的导航标签按钮，点击即可执行命令（`/skills`、`/ah`、`/hub` 等）。

## 功能

- 与 Skyblocker 原版 Quick Navigation 完全一致的行为：
  - 14 个可开关、可双击确认的导航按钮
  - 每个按钮可自定义：物品图标（`Item`/`Count`/`Components`）、正则 UI 标题匹配、点击命令、Tooltip（支持文本 JSON 格式）
  - "选择 SkyBlock 物品" 弹出窗（内置常用 SkyBlock 图标；任意其他物品可通过 Item 与 Item Components 选项手动指定）
  - 选中标签置顶渲染、淡入动画、确认工具提示等原版细节
- 独立的配置系统（`config/quicknav.json`），通过 `/quicknav config` 或 Mod Menu 打开配置界面
- 独立命名空间（`quicknav`）、独立混入与 access widener，可与 Skyblocker 同时安装

## 构建

需要 JDK 25+。

```powershell
.\gradlew.bat build
```

产物位于 `build/libs/quicknav-<version>.jar`。

也可以使用 GitHub Actions 自动构建：每次推送到 `master` 分支都会触发构建，构建产物可在 [Actions](https://github.com/MeowMahoro/quicknav/actions) 页面的 Artifacts 中下载。

## 安装

将以下内容放入 `.minecraft/mods/`：

1. **quicknav.jar**（本模组，已内置 Dandelion 配置框架）
2. **fabric-api**（[Modrinth](https://modrinth.com/mod/fabric-api)）
3. **yet-another-config-lib**（YACL，Dandelion 的配置界面后端，[Modrinth](https://modrinth.com/mod/yacl)）
4. **fabric-language-kotlin**（Dandelion 的运行时依赖，[Modrinth](https://modrinth.com/mod/fabric-language-kotlin)）

> Dandelion 只发布在 skyblocker 的私有 Maven 仓库（`maven.azureaaron.net`），公开渠道无法下载，因此通过 jar-in-jar 内置在本模组中，无需单独安装。
> 与 Skyblocker 同时安装时也不会冲突：Fabric Loader 对版本相同的重复嵌套 jar 只会取其一。
> 注意：不要同时安装手动下载的 dandelion.jar，避免重复。

## 使用

- 在 SkyBlock 服务器上打开任意容器菜单即可看到导航标签。
- 配置：聊天栏输入 `/quicknav config`（或 `/quicknav options`），或通过 Mod Menu 的配置按钮进入。

## 与 Skyblocker 同时安装

本模组使用独立的命名空间、混入与配置，可与 Skyblocker 共存。注意两者都会渲染 Quick Navigation 按钮，
如不希望按钮重复显示，请在其中一个模组中关闭该功能（Skyblocker：`/skyblocker config` → Quick Navigation → Enable；
本模组：`/quicknav config` → Quick Navigation → Enable Quick Navigation）。

## 与 Skyblocker 的差异

- 仅包含 Quick Navigation 相关功能，不包含 Skyblocker 的其他模块。
- "选择 SkyBlock 物品" 弹窗使用内置的常用图标列表，而非 Skyblocker 的完整 NEU 物品仓库；如需任意物品，可直接在 Item 选项中输入物品 ID、在 Item Components 中填入组件字符串。
- 命名空间、翻译键、配置文件名均为 `quicknav`，与 Skyblocker 互不干扰。

## 协议

[LGPL-3.0-or-later](./LICENSE)，与 Skyblocker 相同。
