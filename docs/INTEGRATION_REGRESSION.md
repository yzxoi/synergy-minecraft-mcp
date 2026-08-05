# Numen / Synergy 集成回归清单

这份清单把“构建成功”和“Minecraft 世界确实发生了变化”分开验证。它适用于 Fabric，也可作为 NeoForge 的同等验收模板。每次替换 jar、切换 MCP 配置或更换 API/core 版本时，都应保存一份结果。

## 0. 静态部署检查

先在不启动游戏的情况下确认运行时没有混用旧 artifact：

```powershell
pwsh -File .\scripts\Invoke-NumenIntegrationCheck.ps1 `
  -CoreJar .\minecraft-numen\fabric\build\libs\numen-fabric-1.21.11-0.1.3.jar `
  -ApiJar .\numen-api\fabric\build\libs\numen_api-fabric-1.21.11-0.0.8-SNAPSHOT.jar `
  -InstanceMods 'D:\下载\Minecraft\instances\<实例>\mods'
```

脚本只读检查：

- core jar 内必须恰好包含一个嵌入 API（Fabric 为 `META-INF/jars/numen-api*.jar`，NeoForge 为 `META-INF/jarjar/numen-api*.jar`）；
- 外部 API jar 与 core 内嵌 API jar 的 SHA-256 必须完全相同；
- 实例 `mods` 中默认只允许一个 Numen loader runtime jar；
- `sources`、`javadoc` 和单独的 `*-api.jar` 不得作为运行时 jar 部署。

如果 SHA 不一致，不要继续做世界内测试；先重新发布 API、清理 Loom remapped/processed 缓存，再重建 core。

## 1. 启动与身份不变量

在新测试世界中记录：

1. 服务端日志出现任务调度器 heartbeat；
2. 只加载一个匹配 loader 的 Numen jar；
3. MCP `tools/list` 中的 schema 与当前源码一致，尤其是 `build` 使用 `ops`；
4. 召唤 `sama`，记录 companion UUID、维度、坐标、gameTime、MCP server 地址。

若感知工具与行动工具报告不同 UUID、维度或明显不可能的坐标，立即停止后续动作并保留日志。

## 2. 最小外接 MCP 闭环

每一步只派一个任务，并保存 `task_id`。调用成功只代表“已受理”，不代表世界目标完成。

| 阶段 | 调用 | 必须验证 |
| --- | --- | --- |
| 发现 | `list_companions` | 返回 `sama` 且 UUID 稳定 |
| 基线 | `get_self_status` | 位置/生命/饥饿/装备/背包可读 |
| 移动 | `goto`，目标仅 2–5 格 | `task_status(task_id)` 进入 `running` 后到 `succeeded`；再读状态，位置 delta 与目标一致 |
| 停止 | 长一点的 `goto` 后 `task_stop` | 同一 ID 读到 `cancelled`，身体回到 idle |
| 背包 | `equip_item` | 再次 `get_self_status` 的 mainhand 与任务结果一致 |
| 感知 | `scan_blocks` / `inspect_block` | 目标方块坐标与行动阶段使用的坐标一致 |
| 建造 | 单格 `build(ops=[...])` | 任务成功后重新扫描，目标方块状态改变 |

推荐的最小建造是相邻、可放置的单格方块；不要一开始就测试整栋房子。`build` 返回错误时，记录完整 MCP result（包括 `isError` 和文本），不要用旧的 `blocks` 参数重试来掩盖 schema/runtime 不一致。

## 3. 运行时诊断字段

每个失败任务至少保存以下字段：

- 请求时间、MCP tool 名、tool call id、task id；
- companion UUID、`identityHashCode`、level/dimension、x/y/z、gameTime；
- task 状态序列（pending/running/succeeded/failed/cancelled）；
- 当前 chain 名称与 priority；
- tick counter、最近位置 delta、输入/导航状态；
- 失败任务的 `result.code` / `result.retryable` / `result.next_steps`（机器可读错误域与下一步建议）；
- 终态快照的 `situation` 块（in_water / eye_underwater / air / on_ground / hp / hunger / in_lava / locomotion / active_reflex）；
- API/core SHA-256 与启动日志中的 revision。

如果实体 tick 抛异常，日志必须包含一次限频的异常摘要和实体身份；不能只留下“身体空闲”。

## 3.1 可观测性新契约验收

升级后的外接 MCP 契约，至少验证以下各点：

1. `task_status(wait_seconds=N)` 阻塞等待：任务在 N 秒内终态则直接返回终态；超时返回快照并带 `timed_out_waiting: true`，同一 `task_id` 可续等；
2. `task_status` 快照顶层有 `situation`（终态还有 `result.situation`），水中/低氧等处境无需额外调 `get_self_status` 就能看到；
3. 失败结果带 `code` + `retryable` + `next_steps`（例如 `goto` 不可达 → `world_state` + 可执行建议），网络/超时错误与游戏世界错误分域；
4. `get_events(companion, since_id)` 返回增量事件流：`entered_water` / `air_low` / `damaged` / `fell` / `respawned` / `task_finished` / `body_log` / `dimension_change`，`next_id` 可续读；
5. 工具结果带 `structuredContent`（机器可读 JSON）与 `content[0].text`（英文可读文本）；查询类工具带 `readOnlyHint`/`idempotentHint`，`delete_companion` 带 `destructiveHint`；
6. 落水场景端到端：把同伴放进水里，`get_events` 出现 `entered_water`，`task_status` 快照 `situation.in_water=true`，模型一个调用周期内可反应。

## 4. 生存行为抢占回归

在安全测试世界生成一个不会立即致死的威胁，然后分别验证：

1. 没有外部任务时，`MobDefenseChain` 可以接管身体；
2. 显式 `goto`/`build` 运行期间，非致命 survival chain 不得静默冻结外部任务；
3. `task_status` 能显示当前链、优先级和外部任务是否仍在运行；
4. 任务终态后，生存链可以恢复。

## 5. 通过标准

只有以下条件全部满足，才进入挖掘、战斗、蓝图和海景小屋等长流程：

- 静态 API/core parity 通过；
- 4 个最小闭环（短移动、停止、装备、单格建造）都能从任务终态和世界再感知双重确认；
- 没有未解释的 tick 异常、旧 jar 或重复 runtime jar；
- 失败任务具有可操作错误信息，并能按 task id 查询终态；
- 生存链抢占行为可解释且不会吞掉显式任务。

## 6. 失败报告模板

```text
日期/版本：
loader：Fabric / NeoForge
世界：全新测试世界 / 既有世界
MCP endpoint：
core SHA：
API SHA：
companion UUID：
tool/task id：
请求参数：
task_status 序列：
请求前坐标 → 请求后坐标：
目标方块前状态 → 后状态：
当前 chain/priority：
日志中的异常摘要：
结论：成功 / 可重试失败 / 阻塞性缺陷
```
