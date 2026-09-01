# Quick Nav

[English](README.md) | [中文](README.zh-CN.md)

![Build](https://github.com/MeowMahoro/quicknav/actions/workflows/build.yml/badge.svg)

> **AI notice**: This standalone port was assembled and adapted with AI assistance; the source code comes from [Skyblocker](https://github.com/SkyblockerMod/Skyblocker).

A standalone **Quick Navigation** Fabric mod — ported from [Skyblocker](https://github.com/SkyblockerMod/Skyblocker), installable on its own in a Minecraft **26.2** Fabric environment without Skyblocker.

Renders 14 customizable navigation tab buttons at the top/bottom of Hypixel SkyBlock container menus (e.g. hotbar, inventory). Clicking a button executes a command (`/skills`, `/ah`, `/warp hub`, etc.).

## Features

- Features include:
  - 14 toggleable navigation buttons with double-click confirmation
  - Each button is customizable: item icon (`Item`/`Count`/`Components`), regex UI title matching, click command, tooltip (text JSON format)
  - "Select SkyBlock Item" popup (powered by the full NEU item repository; any other item can also be specified manually via the Item and Item Components options)
  - Details such as selected-tab pinning, fade-in animation, and confirmation tooltips
- Independent configuration system (`config/quicknav.json`), opened via `/quicknav config` or Mod Menu
- Independent namespace (`quicknav`), mixins, and access widener — can be installed alongside Skyblocker

## Building

JDK 25+ is required.

```powershell
.\gradlew.bat build
```

The artifact is at `build/libs/quicknav-<version>.jar`.

You can also use GitHub Actions for automatic builds: every push to the `master` branch triggers a build, and the artifacts can be downloaded from the [Actions](https://github.com/MeowMahoro/quicknav/actions) page.

## Installation

Place the following files into the `.minecraft/mods` folder. All of them except quicknav itself are required dependencies:

1. **quicknav.jar** (this mod, with the Dandelion config framework bundled)
2. **fabric-api** ([Modrinth](https://modrinth.com/mod/fabric-api))
3. **yet-another-config-lib** (YACL, Dandelion's config screen backend, [Modrinth](https://modrinth.com/mod/yacl))
4. **fabric-language-kotlin** (Dandelion's runtime dependency, [Modrinth](https://modrinth.com/mod/fabric-language-kotlin))

## Usage

- Open any container menu on a SkyBlock server to see the navigation tabs.
- Configuration: type `/quicknav config` (or `/quicknav options`) in chat, or use the Mod Menu config button.

## Installing alongside Skyblocker

This mod uses an independent namespace, mixins, and config, so it can coexist with Skyblocker. Note that both will render Quick Navigation buttons — to avoid duplicate buttons, disable the feature in one of them (Skyblocker: `/skyblocker config` → Quick Navigation → Enable; this mod: `/quicknav config` → Quick Navigation → Enable Quick Navigation).

## Differences from Skyblocker

- Contains only the Quick Navigation functionality, not Skyblocker's other modules.
- The "Select SkyBlock Item" popup is powered by the full NEU item repository; for arbitrary items, you can also enter the item ID in the Item option and the component string in Item Components.
- The namespace, translation keys, and config file name are all `quicknav`, independent of Skyblocker.

## License

[LGPL-3.0-or-later](./LICENSE).

## 中文文档

[简体中文 README](README.zh-CN.md)
