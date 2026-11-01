# Real Mineflayer acceptance bot

This is a runnable **Minecraft 1.20.1** bot for visually checking Curio Observer. It joins your server, exports the same bot's real inventory/window data through the bundled adapter, and performs deliberately paced interactions. It does not generate fake HUD events. Node **22+** is required; the exact dependency tree is committed in `package-lock.json`.

The Chinese operating guide follows below. The essential commands are:

```sh
cd integrations/mineflayer-test
npm ci
node main.js --config config.example.json
```

After it prints `ready`, connect your mod to `http://127.0.0.1:3000/api/observer/stream` and spectate `Alex`. Use terminal commands `list`, `next`, `next <scenario>`, `run`, `pause`, `stop`, `status`, and `quit`.

Actual inventory clicks wait 220 ms by default, independently of the 2-second scene dwell. `--action-interval-ms 0` disables this optional control layer. A partial toss is assembled into one real stack drop; the `toss` scene verifies the server's dropped entity count is exactly 3 and recovers the same items.

`--run` explicitly starts the whole selected sequence. Without it the bot waits for terminal commands. `--prepare --origin x,y,z` explicitly prepares an operator-authorized arena; without that flag, the bot uses existing nearby facilities and items. Preparation is never enabled by a JSON/environment setting. Read the arena section before using it.

## 连接和观察

```powershell
cd E:\LudensAgent\Curio-Observer\integrations\mineflayer-test
npm.cmd ci
node main.js --config config.example.json
```

示例配置使用本地服务器 `127.0.0.1:25565`、离线账号 `Alex`，默认展示停留 2 秒、单场景最长 90 秒。运行后先等待，不自动操作世界。可以复制为已被 Git 忽略的 `config.local.json` 再修改。

真实背包点击默认每次等待 **220 毫秒**，与场景的 2 秒展示停留分别配置：`--action-interval-ms 220 --dwell-ms 2000`。这是让 bot 真正放慢动作，不是 HUD 事后重放旧数据；单个和整组放置分别保留真实数量。设 `--action-interval-ms 0` 可恢复原 Mineflayer 操作速度。普通桥接默认不控制动作，只有这个测试 runner 默认启用。

观察端安装构建出的 mod 后：

1. 加入同一服务器并进入旁观模式。
2. 在 mod 的 K 设置中启用 Mineflayer feed，URL 设为 `http://127.0.0.1:3000/api/observer/stream`。
3. 附身观察 bot `Alex`。需要命令权限时执行 `/spectate Alex`，或使用服务器提供的旁观功能。
4. 在 bot 所在终端输入 `list` 查看场景，输入 `next` 开始一个场景。

bot 和游戏客户端在不同机器上时，显式配置 `feedHost` 和可达的观察 URL；应通过已有认证代理或私有网络提供接口。Mineflayer feed 本身不提供认证。

## 手动与顺序测试

| 终端命令 | 行为 |
|---|---|
| `list` | 列出本次选择的场景 ID 和名称 |
| `next` | 运行下一个场景 |
| `next <id>` | 运行或重复指定场景 |
| `run` | 按顺序运行剩余场景；一轮结束后再次 run 从头开始 |
| `pause` | 当前场景正常完成后暂停后续场景 |
| `stop` | 取消当前场景，停止移动，关闭当前容器 |
| `status` | 当前步骤、下一个步骤、已记录的通过／跳过／失败结果 |
| `quit` | 断开 bot，关闭观察数据流 |

运行速度可以调慢，例如：

```sh
node main.js --config config.local.json --dwell-ms 4000
```

显式自动运行：

```sh
node main.js --config config.local.json --run
```

也可以通过 `--scenarios id1,id2` 选择部分场景，以 `list` 输出的 ID 为准。场景会实际移动、合成和交易物品；建议使用专门的测试物品和设施。寻路禁止挖掘、搭方块和跑酷。没有所需物品、附近设施或交易对象时，场景报告 `skipped` 及原因；不能把跳过当成 HUD 验证通过。

当前 22 个场景 ID 为 `inventory`、`crafting_table`、`chest`、`barrel`、`ender_chest`、`shulker_box`、`hopper`、`dispenser`、`grindstone`、`anvil`、`smithing`、`enchanting`、`furnace`、`blast_furnace`、`smoker`、`brewing`、`villager`、`stonecutter`、`loom`、`cartography`、`beacon`、`toss`。例如先检查砂轮和村民：

```sh
node main.js --config config.local.json --scenarios grindstone,villager
```

单场景超时或真实操作失败会停止自动序列并记录原因。若 Mineflayer 内部操作在取消后仍未结束，程序会拒绝启动重叠操作；等它结束，或 `quit` 后重新连接。暂停不会冻结服务器时间，熔炉和其他玩家仍会运行。

`toss` 使用真实 `bot.toss(...,3)`（未启用 pacing 时使用先整理再 `tossStack` 的路径），检查服务器出现一个数量为 3 的掉落物，再全部捡回。需要石头、一个空背包槽和 `origin` 附近的清空通道；附近已有掉落物时会跳过，防止误捡或合并。中途 stop 不会继续走动，可能留下这 3 个物品供手工捡回。一次批量掉落的 HUD 显示实际 count=3，不拆成三个单件动画。

## 显式准备测试场地

准备模式要求 bot 具有执行相关服务器命令的权限，并且操作者已经授权这个区域用于测试。示例原点 `(1000,200,1000)` 是场地中心，位于主世界。

```sh
node main.js --config config.example.json --prepare --origin 1000,200,1000
```

本次准备范围是水平 **24×24**：`x=988..1011`、`z=988..1011`，地板 `y=200`，设施位于上方。准备器只在空气处填充地板、以 `keep` 放置设施，不清空或覆盖已有建筑。它会为这个测试放置工作站、提供 bot 测试物品，并创建带专用标签的交易村民；具体命令和所有坐标集中在 [prepare.js](prepare.js)。临时准备状态在结束后恢复，场地和测试物品保留供重复检查。程序不会在退出时删除场地。

`--prepare` 完成后仍等待终端命令；只有同时指定 `--run` 才立即开始场景。仅在 JSON 写入 `origin` 不会修改世界；必须明确传入 `--prepare`。

准备需要 bot 至少 **23 个空背包槽位**，会添加测试物品和 30 级经验，不清空原有物品。部分场景会消耗原料，重复一轮前可能需要补充；再次准备也会再次提供物品。取消或退出时，runner 会先给准备器的恢复动作最多 3 秒完成，再断开连接。

没有 OP 权限时，预先由管理员布置设施和物品，然后不加 `--prepare` 运行。其他坐标通过 `--origin x,y,z` 或配置文件显式指定；不要使用未授权区域。

## 配置

优先级：CLI 参数 > 环境变量 > JSON 配置 > 默认值。主机必须显式提供，直接执行 `node main.js` 不会默认连接某个服务器。Minecraft 版本固定为 1.20.1。

| JSON 字段 | CLI | 环境变量 | 默认 |
|---|---|---|---|
| `host` | `--host` | `MC_HOST` | 必须提供 |
| `port` | `--port` | `MC_PORT` | 25565 |
| `username` | `--username` | `MC_USERNAME` | CurioTest |
| `auth` | `--auth` | `MC_AUTH` | offline |
| `feedHost` | `--feed-host` | `OBSERVER_HOST` | 127.0.0.1 |
| `feedPort` | `--feed-port` | `OBSERVER_PORT` | 3000 |
| `origin` | `--origin x,y,z` | `MC_ORIGIN` | 无 |
| `dwellMs` | `--dwell-ms` | `MC_DWELL_MS` | 2000 |
| `actionIntervalMs` | `--action-interval-ms` | `MC_ACTION_INTERVAL_MS` | 220，0 关闭 |
| `timeoutMs` | `--timeout-ms` | `MC_TIMEOUT_MS` | 90000 |
| `connectTimeoutMs` | `--connect-timeout-ms` | `MC_CONNECT_TIMEOUT_MS` | 30000 |
| `scenarios` | `--scenarios a,b` | `MC_SCENARIOS` | 全部 |
| `logFile` | `--log name.jsonl` | `MC_LOG_FILE` | 不写文件 |

`auth` 支持 `offline` 与 `microsoft`。正版认证使用 Mineflayer 的 Microsoft 设备授权流程；不提供或记录密码。认证缓存放在已忽略的 `.auth/` 中。使用自己的合法账号，并确保 bot 用户名与正在使用的游戏账号不会相互顶号。

日志文件只能写入此目录下已忽略的 `logs/`。示例配置启用了 `logs/acceptance.jsonl`，记录每个场景的结果、耗时与原因；不会记录认证凭据。`Ctrl+C` 和 `quit` 都会清理 feed、终端和连接。

## 验证范围

这个 runner 的 `passed` 表示对应真实 Mineflayer 操作完成，不代表已经用肉眼确认所有 HUD 像素。观察时应核对窗口类型、槽位、输入输出、cursor、进度和交易报价；参考 [数据契约](../../docs/mineflayer-protocol.zh-CN.md) 了解未实现的专用选项／预览。

```sh
node --test test/*.test.js
```

自动化测试验证配置授权边界、串行步骤、暂停／取消、超时、场景前置条件和准备范围。测试不会自动连接配置中的真实服务器，也不会准备世界。

依赖接口参考：[Mineflayer API](https://github.com/PrismarineJS/mineflayer/blob/master/docs/api.md)、[mineflayer-pathfinder](https://github.com/PrismarineJS/mineflayer-pathfinder)。
