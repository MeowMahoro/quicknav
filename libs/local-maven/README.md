# Dandelion 26.3 local build

QuickNav 需要 Dandelion 作为配置框架。上游 `AzureAaron/Dandelion` 的最新发布版是
`1.0.0-alpha.22+26.2`，其内嵌的 MoulConfig 仍然调用 26.2 的
`InputConstants.isKeyDown(Window, int)`，在 Minecraft 26.3 上打开配置界面会直接崩溃。

官方 MoulConfig 已发布 `modern-26.3:4.8.0`，因此这里基于上游 alpha.22 源码重新构建了一个
Dandelion 26.3 兼容版本，并以内嵌本地 Maven 仓库的方式供构建使用。

## 来源与改动

- 上游：`AzureAaron/Dandelion`，提交 `f3473b82`（Alpha 22），许可证 LGPL-3.0-or-later
- 参考移植：`Froggy-Lord/Dandelion` 分支 `port/minecraft-26.3`，以及 PR
  [`AzureAaron/Dandelion#15`](https://github.com/AzureAaron/Dandelion/pull/15)
- MoulConfig：官方 `org.notenoughupdates.moulconfig:modern-26.3:4.8.0`
- 版本号改为 `1.0.0-alpha.22.1+26.3`：Fabric Loader 比较版本时会忽略 `+build` 元数据，
  如果继续使用 `alpha.22`，与其他模组内嵌的 `alpha.22+26.2` 优先级相同，无法保证选中本版本。

具体源码改动见同目录的 `dandelion-26.3.patch`，主要包括：

- 26.3 依赖矩阵（Loader 0.20.0、FAPI 0.163.0、YACL 3.9.7、FLK 1.14.2）
- `InputConstants.Type.KEYSYM` → `KEYBOARD`
- `GLFW.GLFW_MOUSE_BUTTON_LEFT` → `0`
- `Util.getPlatform().openUri(String)` → `Blaze3D.openUri(URI)`
- 更新 `dandelion.classtweaker` 到 `accessWidener v2`，并改用 `renderpearl` 的渲染管线/纹理类路径

## 构建方式

```bat
git clone https://github.com/AzureAaron/Dandelion.git
cd Dandelion
git checkout f3473b82
git apply <此目录>\dandelion-26.3.patch

gradlew.bat clean shadowJar -Pmod_version=1.0.0-alpha.22.1 --no-daemon ^
  -x spotlessJavaCheck -x spotlessCheck
```

> Spotless 在 Windows/CRLF 下会误报全仓库格式问题，因此本地构建跳过格式检查。

产物去重（上游 shadowJar 会重复写入 `fabric.mod.json`、`LICENSE`、`dandelion.classtweaker`）
后放入本目录，供 QuickNav 的 Gradle 构建直接解析。
