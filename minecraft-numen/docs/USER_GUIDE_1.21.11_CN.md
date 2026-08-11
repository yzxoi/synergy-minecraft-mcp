# Numen 1.21.11 使用手册

> 适用版本：Minecraft 1.21.11、Numen 0.1.3、Java 21  
> 适用加载器：Fabric 0.18.1+ / NeoForge 21.11.42+  
> 本文以当前 `synergy/merge-1.21.1` 集成分支源码为准。旧 README 中的 `G` 键、旧工具名等内容不适用于本版。

## 1. 先选清楚使用模式

新版有三种容易混淆的能力。前两种是二选一的“大脑模式”，第三种是给内置大脑增加工具。

| 模式 | 谁负责思考 | 是否需要 Numen 内填写模型 Key | 适合场景 |
| --- | --- | --- | --- |
| 内置大脑 | Numen 客户端里的 Agent Loop | 需要 | 直接在游戏里和同伴聊天 |
| 外接大脑 | Synergy 等外部 MCP 客户端 | 不需要 | 让 Synergy 精细感知、派动作、轮询结果 |
| 工具扩展 | 仍是 Numen 内置大脑，但可调用别的 MCP Server | 需要 | 给同伴增加模组、数据库或其他外部工具 |

“外接大脑”和“工具扩展”方向相反：

```text
外接大脑：Synergy ──MCP──> Numen ──> Minecraft 同伴
工具扩展：Numen 内置大脑 ──MCP──> 其他 MCP Server
```

同一时间建议只让一个大脑控制一具身体。开启“外接大脑”后，Numen 会暂停内置大脑，游戏聊天输入也会锁定；关闭后才恢复内置聊天。

## 2. 安装

### 2.1 当前构建产物

Fabric：

```text
fabric/build/libs/numen-fabric-1.21.11-0.1.3.jar
SHA-256: 2ee0ae4d170806e0aa4b810e9b146bb8d5a0c8bbddae3ceaf13b3a6ffc6c762c
```

NeoForge：

```text
neoforge/build/libs/numen-neoforge-1.21.11-0.1.3.jar
SHA-256: 06df499661ec11014beb8860d8d8b7dfaf8111da1e5eb364fc3cba0a8e6dfd9f
```

这两个 Numen jar 已内嵌对应加载器的 `numen-api`，不要再单独复制一个 `numen-api` jar。

### 2.2 Fabric

需要：

- Minecraft 1.21.11
- Java 21
- Fabric Loader 0.18.1 或更高
- 与 1.21.11 匹配的 Fabric API
- `numen-fabric-1.21.11-0.1.3.jar`

把 Fabric API 和 Numen jar 放入当前游戏实例的 `mods/`。先删掉旧的 `numen-fabric-*.jar` 和单独安装的 `numen-api-*.jar`，避免同一 mod 重复加载。

### 2.3 NeoForge

需要：

- Minecraft 1.21.11
- Java 21
- NeoForge 21.11.42 或更高
- `numen-neoforge-1.21.11-0.1.3.jar`

把 Numen jar 放入当前游戏实例的 `mods/`，并删除旧版 Numen jar。NeoForge 不需要 Fabric API。

### 2.4 单人和多人

- 单人游戏：安装到启动游戏的客户端实例即可；集成服务器会一同加载服务端部分。
- 独立服务器：服务端和需要使用 Numen 的客户端都应安装相同版本。Fabric 服务端也需要 Fabric API。
- LLM Key、对话记录、外接 MCP Server 都在主人的客户端侧；服务端不替玩家保管 LLM Key。
- 同伴是服务端假玩家，会真实消耗物品、受伤、死亡、掉落和修改世界。第一次使用先备份存档，并在测试世界验证。

## 3. 第一次启动

### 3.1 打开面板

进入世界后按 `N` 打开同伴面板。`N` 是 1.21.11 的真实默认键位；因为原版已占用 `G`，旧版文档中的 `G` 不再适用。

可以在“选项 → 控制”中搜索 `Numen` 或“打开同伴名册”重新绑定。

面板包含：

- 对话：聊天记录、工具调用、当前计划和停止按钮。
- 状态：同伴模型、生命、饥饿、护甲、副手、2×2 合成区和只读背包。
- 设置：模型配置、代理、MCP 工具扩展、外接大脑、技能、人设、声线、皮肤、语音输入和主题。
- 左侧名册：切换同伴；`+` 召唤；删除按钮永久注销同伴。

### 3.2 内置大脑：建立模型配置

如果准备直接在游戏里聊天，先进入“设置 → 模型配置”，点“新建”，填写：

- 名称：这条配置在游戏里的显示名，例如 `OpenAI-main`。
- 提供商：选择接口方；它决定协议适配和默认 Base URL。
- 模型：从列表选择或填写自定义模型 ID。
- API Key：必填，只保存在本机配置文件。
- Base URL：通常留空；代理站、自建兼容服务或特殊区域地址才填写。
- 深度思考：`自动 / 低 / 中 / 高`，仅对支持该参数的模型有意义。

新安装的模型配置库为空。召唤 UI 要求先创建至少一条模型配置，并在召唤时为同伴选择它。每个同伴可以绑定不同模型，修改配置后下一次请求即时生效。

配置保存在：

```text
<游戏目录>/config/numen/providers.json
```

可选提供商和模型目录保存在：

```text
<游戏目录>/config/numen/models.json
```

`models.json` 可加入自建 OpenAI 兼容站点或新模型。API Key 应放在 `providers.json` 或游戏设置中，不要写进要提交到 Git 的文件。

### 3.3 召唤同伴

在面板左栏点 `+`，填写或选择：

- 名字：3–16 位英文字母、数字或下划线。
- 人设：可选。
- 模型配置：内置大脑模式必选。
- 声线：可选。
- 皮肤：可选；默认会尝试使用同名正版玩家皮肤，失败则使用默认皮肤。

也可以使用命令：

```mcfunction
/numen player summon Alice
/numen player despawn Alice
/numen settings
/numen reset
```

说明：

- 重复 summon 自己已有的同名同伴是幂等唤醒，不会故意创建第二个。
- `despawn` 是永久注销，背包物品会掉在原地，无法通过再次登录恢复。
- `/numen reset` 清空当前客户端的对话循环，不会回滚同伴已做过的世界操作。
- 用命令召唤的新同伴没有经过模型配置选择；准备使用内置大脑时，应在面板设置里给它绑定模型配置。

## 4. 与同伴交互

### 4.1 面板聊天

按 `N`，选择同伴头像，在“对话”页输入任务。消息会进入该同伴自己的队列；同伴正在完成一轮工具调用时，新消息会等到协议安全点再处理。

对话会按同伴 UUID 追加保存在：

```text
<游戏目录>/config/numen/conversations/<同伴 UUID>.jsonl
```

上下文快满时会自动压缩，原始记录仍保留在磁盘。面板顶部的 `context` 是当前上下文占比，`tokens` 是累计处理量。

### 4.2 原版聊天框直发

可以在 Minecraft 聊天框输入：

```text
@Alice 帮我砍 16 个橡木原木，然后回来
```

输入 `@Al` 后按 `Tab` 可补全自己的同伴名。只有名字匹配到自己的同伴时，消息才会被私下路由；名字未匹配时，消息会照常发到公共聊天，注意不要把敏感内容误发公屏。

### 4.3 如何下达更稳定的任务

Numen 不是把一句话直接翻译成一个巨大动作，而是“感知 → 动作 → 结果 → 再感知”的循环。任务越长，越应该给清楚目标和边界。

推荐写法：

```text
先检查你的工具、食物和背包空间。采集 32 个橡木原木；不要砍我脚下这片人工树林，
向东找天然林。完成后回到我身边。如果缺斧头，先告诉我需要什么。
```

建造时建议说清：

- 建筑用途和大致尺寸。
- 锚点或相对主人位置。
- 材料范围和是否允许替换现有方块。
- 是否必须先报价、等待确认。
- 完成条件和遇到缺料时的处理方式。

例如：

```text
先在我东侧 8 格检查一块 9×7 的空地，设计单层木屋。先给我材料清单和布局，
不要开工；我确认后再一次性建完整的地基、墙、门窗和屋顶。
```

不推荐只说“给我盖个大城堡”后完全不限定位置、尺寸、材料和停止条件。模型可能做出合理但不是你想要的假设。

### 4.4 停止和恢复

- 内置大脑：在对话页点停止按钮，会取消当前模型回合和正在执行的身体任务。
- 外接大脑：调用 `task_stop`，最好带上要停止的 `task_id`。
- 停止是取消后续工作，不是事务回滚；已挖掉、放下或消耗的内容不会自动恢复。
- 同伴死亡后遵循原版死亡和掉落规则，约 30 秒后在主人附近寻找安全位置复活。死亡会中止当时任务。

## 5. 异步任务：新版避免“卡住”的关键

长动作受理后会立即返回类似结果：

```json
{
  "success": true,
  "message": "已受理,后台执行中……",
  "data": {
    "task_id": "t42",
    "task": "mine",
    "async": true
  }
}
```

一具身体同时只执行一个身体任务。不要在 `t42` 运行时反复提交同一个 `mine`、`goto` 或 `build`，否则只会收到“身体正忙”。

### 5.1 内置大脑模式

内置大脑会自动收到 `task_finished` 事件，正常情况下不需要轮询。玩家问进度时，它可以调用 `task_status` 查看。

### 5.2 Synergy / 外接 MCP 模式

外接驱动不会收到 `task_finished` 推送，必须保存受理返回的 `task_id`，然后调用：

```json
{
  "companion": "Alice",
  "task_id": "t42"
}
```

`task_status` 支持阻塞等待：传 `wait_seconds`（0–60）后，服务器会在 HTTP 线程上等到任务终态或超时，省去高频轮询。等待超时返回的快照带 `timed_out_waiting: true`，用同一 `task_id` 再调一次即可继续等：

```json
{
  "companion": "Alice",
  "task_id": "t42",
  "wait_seconds": 10
}
```

`task_status` 的主要字段：

| 字段 | 含义 |
| --- | --- |
| `state` | `pending`、`running`、`success`、`failed`、`timeout` 或 `cancelled` |
| `terminal` | 是否已到终态 |
| `elapsed_s` | 已运行秒数 |
| `budget_left_s` | 剩余任务预算 |
| `progress.phase` | 当前阶段 |
| `progress.metrics` | 数量、距离、已完成格数等任务专用指标 |
| `progress.seconds_since_progress` | 距离上次实质进展的秒数 |
| `progress.stalled` | 是否超过该任务的停滞预警阈值 |
| `result` | 终态结果；必须检查 `success`、`timed_out`、`interrupted` 和 `data` |
| `result.code` | 失败时的机器可读错误域：`validation`/`not_found`/`busy`/`world_state`/`network`/`timeout`/`unsupported`/`cancelled`/`internal` |
| `result.retryable` | 失败时是否允许原样重试同一调用 |
| `result.next_steps` | 失败时给模型的下一步建议（可执行） |
| `result.situation` | 任务结束瞬间的身体处境快照（见下方字段） |
| `situation` | 快照顶层也镜像一份处境快照，无需进 `result` 就能看到 |

`situation` 字段：`in_water`、`eye_underwater`、`air`、`air_pct`、`on_ground`、
`hp`、`max_hp`、`hunger`、`in_lava`、`dimension`、`x/y/z`、`locomotion`（on_ground /
in_water / swimming / in_lava / elytra_flying / airborne）、`active_reflex`（当前
调度链名，如 breath / unstuck，无则 `none`）、`nearby_hostiles`（16 格球形范围内敌对
生物数）、`targeting_hostiles`（其中正在以该同伴为目标的数量）和 `under_attack`
（正被敌对生物锁定、当前受伤，或最近 100 tick 内受过攻击）。模型应在每次身体绑定的
MCP 响应里顺便读取它，避免「怪物已经贴脸，大脑还在继续 goto」。

### 5.2.1 事件通道 `get_events`

外接大脑可用 `get_events` 拉取同伴的增量事件流（有界环形缓冲，200 条，不持久）：

```json
{
  "companion": "Alice",
  "since_id": 0,
  "limit": 50
}
```

返回 `{events: [{seq, ts, kind, data}], next_id, truncated}`；下次调用把 `next_id`
传给 `since_id` 即可无空洞续读。事件类型：`entered_water`、`left_water`、`air_low`
（≤90 tick）、`damaged`（HP 降 ≥1 心）、`fell`（≥3 格）、`respawned`、
`task_finished`、`body_log`、`dimension_change`。同一类型 2 秒内最多发一次，避免刷屏。

推荐控制循环：

1. 先感知环境。
2. 只派一个身体动作，保存 `task_id`。
3. 用 `task_status(wait_seconds=N)` 阻塞等待；超时再续等，不要高频空转。
4. 到 `terminal: true` 后读取 `result`（含 `code`/`retryable`/`next_steps`/`situation`），不能仅凭“身体空闲”推断成功。
5. 再调用感知工具确认真实世界状态；或用 `get_events` 看等待期间发生了哪些处境事件。
6. 若连续多次 `stalled: true`，根据 phase/message 判断是等待、补物资，还是 `task_stop` 后重新规划。

停止示例：

```json
{
  "companion": "Alice",
  "task_id": "t42"
}
```

把它传给 `task_stop` 后，再以同一 ID 调 `task_status`，可以读到保留的 `cancelled` 终态。

## 6. 用 Synergy 作为外接大脑

这是本项目最推荐的 Synergy 接法：Synergy 负责推理，Numen 只提供可观察、可中断、粒度适中的 Minecraft 身体工具。

### 6.1 启用 Numen MCP Server

1. 进入世界并按 `N`。
2. 打开“设置 → 外接大脑”。
3. 开启“外接大脑模式”。
4. 默认端点是 `http://127.0.0.1:8765/mcp`。

首次启动会生成：

```text
<游戏目录>/config/numen/mcp_server.json
```

默认内容等价于：

```json
{
  "enabled": false,
  "host": "127.0.0.1",
  "port": 8765,
  "token": "",
  "call_timeout_seconds": 300,
  "hidden_tools": ["todowrite", "load_skill"],
  "allowed_origins": []
}
```

面板只能即时切换启用状态；修改 host、port、token、超时、隐藏工具或允许来源后，应重启游戏客户端。

`allowed_origins` 只用于额外放行带 `Origin` 请求头的浏览器来源，按完整 origin
（协议、主机和端口）精确匹配，例如 `https://agent.example.com`。原生 MCP 客户端通常不发送
`Origin`，无需配置；当前 MCP 端点自身的 origin 和等价本机回环地址也会自动允许。不要使用通配符。

只在本机使用时保留 `127.0.0.1`。若改为局域网地址或 `0.0.0.0`，必须设置高强度随机 token、限制防火墙来源，并明确理解外部调用者可以挖掘、建造、攻击和永久删除同伴。

### 6.2 配置 Synergy

Synergy 的全局 MCP 配置文件通常是：

```text
~/.synergy/config/synergy.d/40-mcp.jsonc
```

不使用 token 的本机配置：

```jsonc
{
  "mcp": {
    "numen": {
      "type": "remote",
      "url": "http://127.0.0.1:8765/mcp",
      "oauth": false,
      "enabled": true,
      "connectTimeout": 30000,
      "listTimeout": 30000,
      "callTimeout": 330000
    }
  }
}
```

设置了 token 时增加请求头：

```jsonc
{
  "mcp": {
    "numen": {
      "type": "remote",
      "url": "http://127.0.0.1:8765/mcp",
      "oauth": false,
      "headers": {
        "Authorization": "Bearer 在这里填写与 mcp_server.json 相同的 token"
      },
      "enabled": true,
      "callTimeout": 330000
    }
  }
}
```

注意 Synergy 的格式是：

- 根键为 `mcp`，不是 Claude Desktop 的 `mcpServers`。
- HTTP 服务类型为 `remote`，不是 `http`。
- Numen 不使用 OAuth，所以设置 `oauth: false`。
- Synergy 默认工具调用超时是 30 秒；这里设为 330 秒，使它略高于 Numen 的 300 秒服务端上限。

也可以运行交互式命令：

```bash
synergy mcp add
synergy mcp list
```

选择 `Remote`，URL 填 `http://127.0.0.1:8765/mcp`，OAuth 选择否。修改配置后执行 MCP runtime reload，或重启 Synergy。当前 Synergy 源码中的运行时重载调用是 `runtime_reload(target: "mcp")`。

### 6.3 验证连接

必须保持 Minecraft 已进入世界，且“外接大脑”已开启。然后让 Synergy：

1. 调 `list_companions`。
2. 若为空，调 `create_companion`，名字使用 3–16 位字母、数字或下划线。
3. 再调 `list_companions` 确认。
4. 调 `get_self_status`，并传 `companion` 名字或 UUID。

Synergy 界面里工具名可能显示为 `mcp__numen__list_companions` 一类带命名空间的名称；MCP Server 内的逻辑名仍是 `list_companions`。

如果要绕过 Synergy，直接检查 MCP 端点，可用：

```bash
curl -sS http://127.0.0.1:8765/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"manual-check","version":"1"}}}'

curl -sS http://127.0.0.1:8765/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"list_companions","arguments":{}}}'
```

有 token 时为每条命令增加：

```bash
-H 'Authorization: Bearer 你的token'
```

### 6.4 Synergy 的推荐首轮提示

```text
连接 Numen。先列出同伴并选择 Alice。你是她的外接大脑：每次行动前先感知，
长动作保存 task_id 并轮询 task_status 到终态，完成后再感知确认；一具身体同一时间
只派一个任务。不要把“调用成功”当作“世界目标完成”，不要在任务运行中重复提交。

现在先报告 Alice 的位置、生命、饥饿、装备、背包摘要和附近威胁，不要执行破坏性动作。
```

这比“自由玩 Minecraft”更稳定，因为它先建立状态基线和执行纪律，再逐步给自治目标。

### 6.5 外接模式的管理工具

| 工具 | 用途 |
| --- | --- |
| `list_companions` | 列出当前主人的在线同伴和 UUID |
| `create_companion` | 召唤同伴，不需要先在 Numen 配模型 Key |
| `delete_companion` | 永久注销同伴并掉落背包，属于破坏性操作 |

所有 Minecraft 引擎工具都会被额外注入必填参数 `companion`。默认隐藏 `todowrite` 和 `load_skill`；修改 `hidden_tools` 可以调整，但不建议把内置大脑专用记账工具直接交给外部驱动。

## 7. Numen 调用其他 MCP：工具扩展模式

如果不是让 Synergy 接管身体，而是想让 Numen 内置大脑借用另一个 MCP Server 的工具，进入“设置 → 工具扩展”添加服务。

配置文件：

```text
<游戏目录>/config/numen/mcp_clients.json
```

HTTP 示例：

```json
{
  "enabled": true,
  "servers": [
    {
      "name": "create_helper",
      "type": "http",
      "url": "http://127.0.0.1:9000/mcp",
      "headers": {},
      "enabled": true,
      "connect_timeout_seconds": 20,
      "call_timeout_seconds": 120
    }
  ]
}
```

stdio 示例：

```json
{
  "enabled": true,
  "servers": [
    {
      "name": "helper",
      "type": "stdio",
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-everything"],
      "env": {},
      "enabled": true,
      "connect_timeout_seconds": 20,
      "call_timeout_seconds": 120
    }
  ]
}
```

借来的工具会注册为 `<server>__<tool>`，例如 `create_helper__inspect_machine`，避免和 Numen 原生工具重名。通过面板新增、编辑、启停可以实时连接；直接手改 JSON 后重启游戏最稳妥。

不要把这一配置写进 Synergy 的 `40-mcp.jsonc`：两边 JSON 的字段名和方向都不同。

## 8. 蓝图和建造

### 8.1 两套建造入口

- `build`：模型用 `set`、`box`、`walls`、`line`、`cylinder`、`sphere`、`roof`、`set_door`、`scatter` 等原语生成自由结构；单次展开上限 16384 格。
- `blueprint`：读取已有结构文件；单图上限 32768 个记录格。

自由建造非小型结构前，应让内置大脑加载 `building_design` 技能。外接 Synergy 则应自己先做设计、材料和锚点检查，再调用底层工具。

### 8.2 蓝图目录和格式

把文件放到服务器工作目录下的：

```text
schematics/
```

支持：

- `.litematic`
- `.schem`（Sponge / WorldEdit v2、v3）
- `.nbt`（原版结构方块导出）
- `.snbt`

旧目录 `config/numen/blueprints/` 仍可兜底只读，但新文件统一放 `schematics/`。单人游戏中，“服务器工作目录”通常就是该游戏实例目录；独立服务器则是服务端根目录，不是玩家客户端目录。

### 8.3 正确工作流

1. `blueprint(action="list")`：列出文件名和尺寸。
2. `blueprint_read(file="名称")`：只读尺寸、格数、逐层分布和完整材料表。
3. 决定锚点。锚点是旋转后结构的最小 X/Y/Z 角，Y 是底层地面高度。
4. 可再用带锚点的 `blueprint_read` 检查现场已完成多少、还缺多少材料。
5. `blueprint(action="build", file=..., x=..., y=..., z=..., rotation=0|90|180|270)` 开工。
6. 轮询异步任务；终态后复查现场。

生存模式下：

- 自由 `build` 会在开工前检查整次调用所需材料；缺料时整单拒绝，避免只盖半栋。
- 大型 `blueprint` 允许分段施工；背包材料用完会报告余量。补货后以完全相同的 file、锚点和 rotation 再调用，会跳过已经完成的格子继续。
- 显式空气格会清理现场；液体格会跳过。
- 带特殊组件/NBT 的物品可能要求精确匹配，先看 `blueprint_read` 的详细材料字段。

创造模式按当前能力画像免材料施工。

## 9. Skills

内置 11 个技能：

```text
blaze_rods
building_design
combat_basics
containers
dragon_combat
end_game_overview
ender_pearls
nether_entry
stronghold_finding
tier_progression
world_atlas
```

用户技能目录：

```text
<游戏目录>/config/numen/skills/<技能名>/SKILL.md
```

最小模板：

```markdown
---
name: base_rules
description: 当同伴在我们的主基地工作时，先加载此技能；包含禁挖区、仓库规则和常用坐标。
---

# 主基地规则

- 不要破坏信标、分类仓和红石线路。
- 原木放入主仓入口箱。
- 大型建造必须先报价并等主人确认。
```

规则：

- `name` 必填；只支持简单的单行 YAML `key: value`。
- `description` 应写清“什么时候加载”，模型只会先看到名称和描述。
- 正文按需由 `load_skill` 加入对话，不要把所有知识都塞进系统提示词。
- 技能可以包含 `references/` 等附属文件，正文引用后模型可按相对路径继续读取。
- 用户技能与内置技能同名时，用户版本优先。
- 在设置的“技能”页可查看、启停并打开目录。新增文件后可触发资源重载；重启客户端是最稳妥的刷新方式。

Skills 只指导决策，不新增底层能力。如果工作流要求读取某个模组机器的内部网络，而当前工具做不到，仍需要相应 MCP 或原生工具扩展。

## 10. 当前原生工具清单

当前注册 36 个原生工具。外接 MCP 默认隐藏其中 `todowrite`、`load_skill`，但会额外增加 3 个同伴管理工具。

| 类别 | 工具 |
| --- | --- |
| 自身/世界感知 | `get_self_status`、`get_owner_status`、`get_world_info`、`look_around` |
| 方块/实体感知 | `scan_blocks`、`scan_nearby_entities`、`inspect_block`、`inspect_block_storage` |
| 导航/定位 | `goto`、`locate_structure`、`locate_biome` |
| 采集/生存 | `mine`、`collect_items`、`fish`、`eat_item`、`equip_item` |
| 战斗/交互 | `combat`、`combat_status`、`melee_attack`、`ranged_attack`、`interact_at`、`interact_entity` |
| 背包/容器 | `drop_items`、`take_items`、`transfer`、`inspect_gui`、`close_gui` |
| 配方/合成 | `lookup_recipe`、`craft` |
| 建造 | `build`、`blueprint`、`blueprint_read` |
| 任务控制 | `task_status`、`task_stop` |
| 内置 Agent | `todowrite`、`load_skill` |

重要：当前真实工具名是 `goto` 和 `mine`，不是旧提示文字中偶尔出现的 `move_to` 和 `auto_mine`。外接客户端应始终以 MCP `tools/list` 返回的名称和 JSON Schema 为准，不要硬编码旧文档。

几个常用参数示例：

```jsonc
// 去某个水平位置，省略 y 让系统自动找地表
{"companion":"Alice","x":120,"z":-45}

// 走到最近的工作台旁，不破坏它
{"companion":"Alice","block":"minecraft:crafting_table"}

// 找附近的敌对实体
{"companion":"Alice","radius":32,"type_filter":"hostile"}

// 搜索方块；最多返回 32 个匹配
{"companion":"Alice","radius":64,"block_ids":["minecraft:iron_ore","minecraft:deepslate_iron_ore"]}

// 获取 16 个新增加的原木物品
{"companion":"Alice","block_ids":["minecraft:oak_log"],"count":16}

// 启动一个有边界、可观测的战术战斗任务；entity_ids 来自实体扫描
{"companion":"Alice","entity_ids":[42,57],"stance":"defensive","max_range":12,"flee_health":8}

// 读取当前自主或显式战斗控制器的战术、威胁黑板和安全过滤状态
{"companion":"Alice"}
```

`goto` 有四种互斥意图：

- `x + z`：去一处位置，自动求地表；探索时默认用法。
- `block`：去最近目标方块旁边；要交互工作台、箱子或机器时优先用。
- `x + y + z`：精确站到该脚部格，可能挖掉占据该格的方块。
- 仅 `y`：升降到某个高度。

不要把精确坐标指向想保留的箱子或机器，应该用 `block` 模式走到它旁边。

## 11. 配置和数据目录速查

| 路径 | 内容 |
| --- | --- |
| `config/numen/providers.json` | 命名模型配置和同伴绑定，含 API Key |
| `config/numen/models.json` | 提供商、Base URL、模型目录和上下文大小 |
| `config/numen/mcp_server.json` | Numen 作为 MCP Server，即外接大脑 |
| `config/numen/mcp_clients.json` | Numen 作为 MCP Client，即工具扩展 |
| `config/numen/skills/` | 用户 Skills |
| `config/numen/skills_state.json` | 技能启停状态 |
| `config/numen/conversations/` | 每个同伴的对话 JSONL 和统计 |
| `config/numen/persona/` | 用户人设 Markdown |
| `config/numen/voice.json` | TTS 声线库和绑定 |
| `config/numen/stt.json` | STT 提供商/模型目录 |
| `config/numen/skin.json`、`config/numen/skins/` | 皮肤库 |
| `config/numen/ui.json` | 主题和界面偏好 |
| `schematics/` | 蓝图主目录 |
| `logs/latest.log` | 当前游戏日志 |
| `crash-reports/` | Minecraft 崩溃报告 |

Fabric 还会生成兼容用的 `config/numen.json`，NeoForge 对应 `config/numen-common.toml`。新版面板的主要模型配置以 `config/numen/providers.json` 为准，不建议把旧全局配置当成新同伴的唯一配置入口。

## 12. 常见故障

### 12.1 进入世界立即崩溃

先确认只安装了 0.1.3：

```text
numen-fabric-1.21.11-0.1.3.jar
```

或：

```text
numen-neoforge-1.21.11-0.1.3.jar
```

- 0.1.1 的 1.21.11 ChatScreen Mixin 签名不兼容已在后续版修复。
- 0.1.2 的同伴区块 TicketType 在注册表冻结后注册问题已在 0.1.3 修复。
- 若报告仍出现 `ExceptionInInitializerError`、`Registry is already frozen` 或旧 ChatScreen 注入错误，通常是 `mods/` 中还残留旧 jar，或启动器实际使用的是另一个实例目录。

### 12.2 游戏能进，但同伴不回复

检查顺序：

1. “外接大脑”是否开启；开启时内置聊天故意暂停。
2. 同伴是否绑定了模型配置。
3. 对应配置是否有 API Key。
4. `logs/latest.log` 中是否有 HTTP 401、403、404、429 或模型不存在。
5. Base URL 是否填写成 API 根地址，而不是具体网页或错误的聊天路径。
6. 代理设置是否可达。

### 12.3 外接大脑连不上

检查：

```bash
lsof -nP -iTCP:8765 -sTCP:LISTEN
curl -i http://127.0.0.1:8765/mcp
synergy mcp list
```

`curl -i` 的 GET 返回 405 并不代表服务坏了，因为当前 Numen MCP 端点只处理 JSON-RPC POST。真正验证请使用 6.3 节的 POST 命令。

常见原因：

- 外接大脑开关未开启。
- 8765 被别的进程占用；改 `port` 后重启。
- Synergy 写成了 `type: "http"`；它要求 `type: "remote"`。
- token 已设置但 Synergy 没带 Bearer 请求头。
- Synergy 和 Minecraft 不在同一台机器，却仍使用 `127.0.0.1`。

若 MCP 能连接，但 `list_companions` 为空、`create_companion` 失败，确认 Minecraft 已经进入世界，而不是停在主菜单。

### 12.4 同伴任务看起来卡住

- 不要先重复派同一个动作；先查原 task ID。
- 看 `phase`、`message`、`seconds_since_progress` 和 `stalled`，区分长距离移动、等待路径、缺材料和真实停滞。
- 查询自身状态和附近环境，确认同伴是否死亡、掉线、被困或跨维度。
- 确定无法继续时 `task_stop`，再基于新的感知结果缩小任务。
- 独立服务器上主人离线后，同伴不会继续持有区块；主人上线后会恢复。

### 12.5 蓝图找不到

- 单人：确认放在当前启动器实例根目录的 `schematics/`。
- 多人：确认放在服务端根目录的 `schematics/`，不是客户端目录。
- 扩展名必须是 `.litematic`、`.schem`、`.nbt` 或 `.snbt`。
- 先调用 `blueprint action=list`，构建参数使用返回的无扩展名名称。

### 12.6 Fabric 日志出现声音 Mixin redirect 警告

若只是 Fabric Sound API 与 Numen 声音注入点的 redirect 警告、游戏仍能进入世界，通常不是致命错误。排查崩溃应以 crash report 最底部的 `Caused by` 链和 `latest.log` 对应时间段为准。

## 13. 从源码重新构建

当前集成目录包含两个仓库。先把 API 发布到本地 Maven，再构建核心：

```bash
mkdir -p /tmp/numen-maven-1.21.11

cd /Users/yzxoi/minecraft-mcp/merge-1.21.11/numen-api
bash gradlew publish \
  -Plocal_maven_url=file:///tmp/numen-maven-1.21.11

cd /Users/yzxoi/minecraft-mcp/merge-1.21.11/minecraft-numen
bash gradlew clean build --refresh-dependencies \
  -Plocal_maven_url=file:///tmp/numen-maven-1.21.11
```

构建结果在：

```text
fabric/build/libs/
neoforge/build/libs/
```

正常分发只复制对应加载器的 Numen 0.1.3 jar；不要复制 dev、sources、javadoc jar，也不要再附加单独的 numen-api jar。

## 14. 建议的验收顺序

每次换 jar 或改 MCP 配置，按以下最小闭环验收：

1. 备份存档，清除旧版 Numen jar。
2. 新建测试世界，确认能进入且日志出现任务调度器 heartbeat。
3. 召唤一个测试同伴，打开状态页。
4. 内置模式：让它报告状态，再走 5 格并回来。
5. 外接模式：`list_companions` → `get_self_status` → 一个短 `goto` → 按 ID 轮询 → 再感知。
6. 测试 `task_stop`，确认身体回到 idle。
7. 最后才在正式存档测试挖掘、建造、战斗和蓝图。

这个顺序能把“加载问题、MCP 连接问题、感知问题、异步调度问题、世界修改问题”逐层分开，不会把所有故障都误判成模型卡住。
