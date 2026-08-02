# Upstream provenance

本文件记录 monorepo 中两个导入目录的来源，便于审计修改和继续跟踪上游。

## numen-api

| 字段 | 值 |
| --- | --- |
| 目录 | `numen-api/` |
| 上游仓库 | `https://github.com/Dwinovo/numen-api.git` |
| 上游分支 | `1.21.11` |
| 上游基线提交 | `626fa1aebab64b3bbc4f9c9246e81875cb3188a2` |
| 导入前本地分支 | `synergy/merge-1.21.1` |
| 导入前快照提交 | `114770a30a502d748d6a5589432da2872539a956` |

## minecraft-numen

| 字段 | 值 |
| --- | --- |
| 目录 | `minecraft-numen/` |
| 上游仓库 | `https://github.com/Dwinovo/minecraft-numen.git` |
| 上游分支 | `1.21.11` |
| 上游基线提交 | `9b9e60eeba5320153323918bee13b68aa14da7c6` |
| 导入前本地分支 | `synergy/merge-1.21.1` |
| 导入前快照提交 | `bfba8bf5120eca07f771de56940d7d32d12e8e2e` |

## 历史导入方式

monorepo 保留两个项目的源码提交历史，但导入时重写了提交对象，专门从全部历史中删除上游 `LICENSE-ASSETS` 禁止在衍生项目中复制的视觉、音频、品牌和人物设定素材。因此：

- 表中的“导入前快照提交”是原始本地仓库中的审计锚点。
- monorepo 内对应提交会具有不同 SHA，这是合规过滤造成的预期结果。
- 不应直接把未过滤的上游分支做普通 `git subtree pull`，否则会重新引入受限制素材。
- 同步上游时应先在临时克隆中应用 [ASSET_EXCLUSIONS.md](ASSET_EXCLUSIONS.md) 的过滤规则，再合并到对应前缀。

上游代码及其修改仍受各自目录中的许可证约束。保留上游链接和提交信息并不改变许可证，也不表示上游认可本项目。
