# Synergy Minecraft MCP

这是一个面向 Synergy 外接大脑工作流的非官方 Minecraft MCP monorepo，当前目标版本为 Minecraft 1.21.11。

本仓库记录了我们基于 Dwinovo 的 Numen 项目进行的 MCP、任务可观测性、寻路、建造与蓝图迁移工作：

- `numen-api/`：游戏内 MCP Server、同伴生命周期、工具注册和任务协议。
- `minecraft-numen/`：Minecraft 感知、寻路、挖掘、交互、建造和蓝图执行器。

两个目录保留各自的 Gradle 工程结构。`minecraft-numen` 在构建时通过同级的 `numen-api` 复合构建/本地 Maven 产物取得依赖。

## 上游与项目身份

本项目是衍生开发记录，不是 Dwinovo 官方发行版，也未获其认可或背书。

- 上游 API：[Dwinovo/numen-api](https://github.com/Dwinovo/numen-api)
- 上游核心：[Dwinovo/minecraft-numen](https://github.com/Dwinovo/minecraft-numen)
- 精确基线、导入提交和同步注意事项见 [UPSTREAMS.md](UPSTREAMS.md)。

“Numen”和“言出法随”及其品牌归原权利人所有。本仓库名称不表示官方关系。

## 许可证与素材排除

本仓库没有一份覆盖所有文件的统一许可证：

- 两个上游子项目的非公共 API 源码及其衍生修改继续遵循 LGPL-3.0。
- `com.dwinovo.numen.api` 公共集成 API 仅在上游 `LICENSE-API` 指定范围内遵循 MIT。
- 本仓库新增的根目录维护文件按 MIT 提供。
- 上游 GUI 纹理、Logo、皮肤、声音和其他受 `LICENSE-ASSETS` 约束的素材没有导入本仓库。

完整映射见 [LICENSES.md](LICENSES.md)，排除清单见 [ASSET_EXCLUSIONS.md](ASSET_EXCLUSIONS.md)，上游署名见 [NOTICE.md](NOTICE.md)。每个子项目内原有的 `LICENSE`、`LICENSE-API` 和 `LICENSE-ASSETS` 均原样保留。

由于受保护素材被排除，此仓库是可审计的源码与改动记录，不应被误认为包含官方美术资源的完整发行包。发布衍生 Jar 前必须提供自行创作且许可兼容的替代资源，并遵守 LGPL 对相应源码的要求。

## 当前状态

- Minecraft：1.21.11
- Java：21
- Numen Core 快照：`bfba8bfa`（原始本地历史）
- Numen API 快照：`114770a3`（原始本地历史）

1.21.11 的安装、Synergy MCP 接入、异步任务、蓝图和排障说明位于 `minecraft-numen/docs/USER_GUIDE_1.21.11_CN.md`。

## 构建顺序

先构建并发布 API 到仓库内约定的本地 Maven 目录，再构建核心：

```bash
cd numen-api
./gradlew publish

cd ../minecraft-numen
./gradlew build
```

具体 Gradle 任务和平台产物以两个子项目自己的文档及配置为准。
