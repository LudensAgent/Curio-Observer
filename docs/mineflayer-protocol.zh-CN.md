# Mineflayer 观察协议 v1

[English](mineflayer-protocol.md)

Curio Observer 是面向 Minecraft 1.20.1 Fabric 的客户端附身观察模组。视角来自游戏的旁观机制；背包、合成、容器和村民交易等私有信息来自被观察 bot 的数据流。本文件规定第三方 Mineflayer 项目需要提供的最低接口。[随附适配器](../integrations/mineflayer/README.md) 可直接接入，不需要依赖参考仓库。

## 最小接入

采集器必须接入**实际被观察的 Mineflayer bot 实例**。另外创建一个旁观 bot，拿不到其他玩家的私有槽位。由 bot 提供数据时，不需要安装 Minecraft 服务端模组，也不需要 RCON。RCON 可以作为人物背包／数值的可选补充，但不能替代容器数据流。

基本实现必须做到：

1. 提供 UTF-8 HTTP GET SSE 接口，响应头为 `Content-Type: text/event-stream`。
2. 每次连接先发送 `hello`、`bot_status` 和完整 `state`；若窗口已打开，再发送完整 `window_open` 和 `cursor`。
3. 发送窗口打开、关闭、槽位变化和鼠标携带物品变化。槽位必须保留原始索引和空槽 `null`，不能过滤空槽。
4. 至少每 15 秒发送一次注释心跳。客户端在连接静默 45 秒后判定过期并重连。
5. 重连后重新发送当前完整快照。v1 不要求保存历史事件或处理 `Last-Event-ID`。
6. bot 离线／死亡时发送 `offline`／`dead` 状态并清理窗口；恢复在线后提供新状态。

一个数据流对应一个 bot。`hello.bot` 必须与被附身观察的 Minecraft 用户名完全一致，客户端不会将另一只 bot 的背包叠加到当前目标。协议版本是 `1`，Minecraft 版本是 `1.20.1`。

默认地址为 `http://127.0.0.1:3000/api/observer/stream`。在模组的 K 设置中启用 Mineflayer feed 并填写 URL；配置文件对应 `feedEnabled`、`feedUrl`。

```text
event: hello
data: {"protocol":1,"bot":"Curio","mcVersion":"1.20.1","agentVersion":"my-bot/1.0"}

event: bot_status
data: {"status":"online"}

: heartbeat

```

每个事件用空行结束，`data` 是 JSON 对象。未知事件名会被忽略，因此已有数据流可以保留其他 Agent 信息。限制为：单个事件 JSON 最多 262,144 字符、最多 256 槽、单个物品可选 SNBT 最多 65,536 字符。较大的自定义背包或书籍快照必须遵守限制，不能直接截断 JSON。

## Mineflayer 最少需要提供什么

| 能力 | 对象、字段或事件 |
|---|---|
| 身份 | `bot.username`、`bot.version` |
| 玩家背包 | `bot.inventory.slots`、`bot.quickBarSlot`；完整生命 HUD 还需生命、饥饿、经验 |
| 当前窗口 | `bot.currentWindow` 及其 `id/type/title/slots/inventoryStart/selectedItem` |
| 生命周期 | `windowOpen/windowClose`、背包和窗口的 `updateSlot`、`spawn/death/end/kicked` |
| cursor 和原地修改 | `_client` 的 `set_slot/window_items`，或等价的实际鼠标携带物品更新；`acceptClick` 完成后的槽位状态 |
| 工作站属性 | `craft_progress_bar`，部分适配器命名为 `window_property` |
| 村民交易 | 接收 `trade_list`，观察发出的 `select_trade`，通过 `bot.registry.items` 解析物品数值 ID |
| 交互选项 | 观察发出的 `enchant_item/name_item`；其他工作站按需增加适配 |
| 2×2 合成 | 在 `bot.craft()` 或自定义背包操作前后提供明确的开始／结束信号 |

这是数据契约，不是远程控制接口。mod 不需要 bot 的账号凭据、寻路器、规划器或控制函数；其他语言的实现也可以直接输出同样的数据。

## 使用内置适配器

将 `integrations/mineflayer` 安装为本地包，例如 `npm install /path/to/Curio-Observer/integrations/mineflayer`，或复制该目录到 bot 工程。要求 Node 22+，适配器本身没有第三方运行依赖。

```js
import { installMineflayerObserver } from '@curio-observer/mineflayer';

// bot 是你的应用已创建的 Mineflayer 实例。
let observer;
bot.once('spawn', async () => {
  observer = installMineflayerObserver(bot);
  await observer.listen({ port: 3000 });
});

// 替换 bot 或退出应用前：
// await observer?.dispose();
```

如果 bot 已完成首次出生，直接调用安装函数。bot 对象被重新创建时，需要销毁旧适配器并接入新对象；同一个对象跨维度或重生时不必重装。

可用 API：

- `observer.handler(request, response)`：接到现有 HTTP 服务；处理观察接口时返回 `true`，其他路径返回 `false`。
- `observer.listen({host, port})`：创建独立 HTTP 服务，默认只监听 `127.0.0.1:3000`。
- `observer.openInventory()` / `closeInventory()`：为绕过 `bot.craft()` 的自定义 2×2 操作增加开关信号。
- `await observer.dispose()`：恢复被包装的方法、移除监听、结束 SSE 连接并关闭它自己创建的 HTTP 服务。

默认模式下，适配器观察 `bot.craft()`、`bot.trade()`、活动窗口 `acceptClick()` 和 `_client.write()`，保留原有参数与返回值，不插入展示等待时间，不主动发送游戏操作包。同一个 bot 的库存操作串行执行仍由原控制程序负责。需要可见的实际点击节奏时，可以显式启用下文的 `actionPacing`。包内提供 TypeScript 声明，直接列出最低 `ObserverBotSource`、`ObserverWindowSource` 和 `ObserverItemSource` 接口。

远程部署可以显式设置 `host`，并把 mod 的 URL 指向可达地址。这个 HTTP 接口自身没有认证；需要对外提供时，应接到应用已有的认证代理或私有连接中。它只输出遥测，不接受控制命令。

## 物品和人物状态

空物品为 `null`。非空物品格式：

```json
{
  "id": "minecraft:enchanted_book",
  "count": 1,
  "damage": 0,
  "maxDamage": 0,
  "enchanted": true,
  "name": null,
  "nbt": "{StoredEnchantments:[{id:\"minecraft:mending\",lvl:1s}]}"
}
```

`nbt` 是可选的**物品 tag compound 的 SNBT 字符串**，不是整个 `{id,Count,tag}` ItemStack。保留数字类型后缀、列表、数组、自定义名称、药水与实际附魔。仅有 `enchanted:true` 无法表达附魔书内容。旧格式没有 NBT 仍能显示基础物品；适配器对超过长度上限的 tag 会省略 NBT，保留基础字段。整个快照的 JSON 超过事件上限时，会为该帧省略所有可选 NBT；仍过大时再省略名称，保留每个槽位、物品 ID 和数量。

`state` 至少包含：

| 字段 | 含义 |
|---|---|
| `health/absorption`，可选 `maxHealth` | 生命、吸收；最大生命默认 20 |
| `food/saturation/oxygen` | 饥饿、饱和、0–20 HUD 氧气刻度 |
| `xp:{level,progress}` | 经验等级、0–1 进度 |
| `armor` | 恰好 4 格，头／胸／腿／脚 |
| `main` | 恰好 27 格主背包 |
| `hotbar` | 恰好 9 格快捷栏 |
| `offhand` | 副手物品或 null |
| `selected` | 选中快捷栏，0–8 |

打开容器后，`currentWindow` 后面的 36 格玩家库存可能比 `bot.inventory` 更新，应通过 `inventoryStart` 读取。Minecraft 1.20 锻造台有 **4 个工作站槽位**，部分旧版 prismarine-windows 却声明为 3，需要纠正偏移。

## 窗口数据

| 事件 | 内容 |
|---|---|
| `window_open` | `windowId/kind/title/slots`；可附完整 `furnace/enchanting/anvil/merchant/brewing` |
| `slot` | `windowId/slot/item` |
| `cursor` | `item`，鼠标携带物品或 null |
| `window_close` | `windowId` |
| `bot_status` | `status` 为 `online/offline/dead` |

`slots` 必须使用**窗口槽位编号**，包含附加的玩家库存；不能使用玩家 NBT `Inventory` 的编号。先发送打开事件，再发送该窗口的增量。关闭后不再发送旧窗口更新，cursor 同时清空。快速“打开→操作→关闭”也必须保留事件，单纯定时取最新状态会漏掉整个过程。

支持的 `kind`：`inventory`、`crafting_table`、`chest`、`large_chest`、`generic`、`generic_3x3`、`shulker_box`、`hopper`、`furnace`、`blast_furnace`、`smoker`、`brewing_stand`、`enchanting_table`、`anvil`、`smithing`、`grindstone`、`stonecutter`、`loom`、`cartography_table`、`beacon`、`merchant`。未知类型显示通用格子。木桶／末影箱使用箱子布局，投掷器／发射器使用 `generic_3x3`。

| 窗口 | 主要槽位 |
|---|---|
| 玩家背包 | 输出 0；2×2 输入 1–4；盔甲 5–8；主背包 9–35；快捷栏 36–44；副手 45 |
| 工作台 | 输出 0；3×3 输入 1–9；主背包 10–36；快捷栏 37–45 |
| 砂轮／铁砧 | 输入 0–1；输出 2；玩家库存从 3 开始 |
| 1.20.1 锻造台 | 模板 0；装备 1；材料 2；输出 3；玩家库存从 4 开始 |
| 村民交易 | 输入 0–1；输出 2；玩家库存从 3 开始 |
| 箱子 | 容器 0–26 或 0–53，后面接玩家库存 |

2×2 合成不会产生普通 Mineflayer `windowOpen`。内置适配器会包装 `bot.craft()` 自动发送背包开关；自定义流程需调用 `openInventory()/closeInventory()`。普通玩家只按 E 查看背包而没有操作槽位的行为，不能靠这个协议推断。

## 工作站和村民交易

以下字段可放在 `window_open` 的对应子对象中；后续更新事件在顶层添加 `windowId`。例如 `furnace` 数据为 `{"windowId":2,"burnTime":80,"fuelTime":1600,"cookTime":40,"cookTimeTotal":200}`。每次发送已知属性的完整状态，未知值用 `null`。

| 事件 | 属性 | 原始来源 |
|---|---|---|
| `furnace` | `burnTime/fuelTime/cookTime/cookTimeTotal`，单位 tick | 属性 0/1/2/3 |
| `brewing` | 剩余 `brewTime`、剩余燃料次数 `fuel` | 属性 0/1 |
| `anvil` | 等级费用 `cost`、待命名文本 `name` | 属性 0、发出的 `name_item` |
| `enchanting` | 3 个 `{cost,enchant,level}` 选项、`chosen` 0–2 或 null | 属性 0–2、4–6、7–9；发出的 `enchant_item` |
| `merchant` | `offers/selected/level/experience/regular/canRestock` | `trade_list`、发出的 `select_trade` |

每个交易报价是 `{input1,input2,output,disabled,uses,maxUses}`。物品使用完整格式，保留附魔书等 NBT。`input1.count` 是需求与特殊折扣调整后的**实际价格**。`selected` 为交易索引，未观察到时为 null；HUD 会自动让选中项可见，不表示真实鼠标滚轮位置。

有些属性包早于 Mineflayer `windowOpen` 到达，需按当前窗口对象缓存，等窗口打开时一并发出。不要根据背包变化猜测附魔选项或制造熔炉进度。

Mineflayer 在交易完成后还会原地更新 `trades[].nbTradeUses/tradeDisabled`，未必再收到 `trade_list`。适配器在 `bot.trade()` 完成后同步这些数值；如果接入时村民窗口已经打开，也会读取现有 `trades` 用于重放。

## 可选的操作展示事件

mod 支持可选展示事件 `pointer`（`windowId/slot/durationMs/item`）和 `inventory_transfer`（`windowId/slot/kind/item`，kind 为 `take/put/drop`，兼容旧 `craft/quick_move`）。这两个事件允许 `slot=-999` 表示窗口外丢弃。`pointer.item` 必须是实际 cursor 持有的物品，第一次拿起之前为 null；`inventory_transfer.item.count` 是这一次操作真正移动的数量。例如一次丢出 3 个组成的物品堆，应发送一个 count=3 的 drop，不能编造三个 count=1 的动画。槽位与 cursor 仍立即同步真实状态。

先发送 pointer，再执行操作，完成后发送 transfer。例如从鼠标携带的 64 个物品中右键丢出 1 个：pointer 携带 64，transfer 数量为 1，剩余 cursor 为 63。pointer 不能填入尚未拿起的槽内物品。

内置适配器仅在显式配置 `installMineflayerObserver(bot, { actionPacing: { intervalMs: 220 } })` 时发送这些展示事件。传 `true` 同样使用 220 毫秒；省略或 false 保持原有速度。它在**真实点击前等待**，不延迟网络读写、不事后排队播放过期背包。测试 bot 默认启用，`--action-interval-ms 0` 可关闭；普通只读桥接默认不启用。

该可选控制层面向 Mineflayer 4.39.0 / Minecraft 1.20.1，将 `clickWindow/transfer/putAway/moveSlotItem/putSelectedItemRange` 以及已打开容器的 `deposit/withdraw` 路由到有节奏的真实点击。合成、交易、熔炉、附魔与铁砧等调用这些 API 时也得到节奏控制。整组左键放置不会被拆成逐个动画；右键放置仍为真实单个。`bot.toss(type,metadata,3)` 会先在空背包槽中组成 3 个的一堆，再真正左键丢出一次；需要整理但没有空位时，操作在丢弃前报错。超过堆叠上限的数量按实际物品堆分批丢出。`tossStack` 保留整堆语义，背包空间不足时不偷偷丢掉 cursor 中的物品。

控制器仍须串行 await 高层操作。停止时调用 `observer.cancelPendingActions()` 取消尚未发出的点击；已发到服务器的操作不能撤销。也可独立使用 `installMineflayerActionPacing(bot,{intervalMs,onPointer?,onTransfer?})`，返回 `{intervalMs,cancelPending(),dispose()}`。不要与 observer 的同名选项重复安装。自行保存旧函数引用或直接发送底层包的插件不在覆盖范围内。完整接口见 [适配器文档](../integrations/mineflayer/README.md#optional-real-action-pacing)。

HUD 立即应用实际槽位／cursor 快照，最多保留最近 1.2 秒内的 8 次 transfer；每次实际丢弃显示一个带准确数量的物品堆，动画最多持续 450ms，关窗淡出持续 200ms。打开新窗口或断线会清理旧展示状态。这些短动画不会排队重播历史背包；未启用实际操作间隔时，快速操作的中间槽位仍可能在下一帧渲染前已经消失。

## 当前覆盖边界

| 功能 | 实现范围 |
|---|---|
| 存储、合成、砂轮、锻造台 | 开关、实际槽位、物品 NBT、cursor、结果物品 |
| 熔炉及变种、酿造台 | 槽位、实际进度和燃料 |
| 附魔台、铁砧 | 选项、费用、选中项、命名 |
| 村民／流浪商人 | 报价、调整价格、选择、缺货、等级经验 |
| 切石机、织布机、制图台、信标 | 已有容器槽位；专用选择列表和预览尚未同步 |
| 马匹／讲台、书本／告示牌／命令方块编辑器 | 暂无专用渲染与完整适配契约，通用格子不代表完整界面 |
| 真实鼠标坐标、悬停、滚动、未提交编辑文本 | 原版 bot 窗口数据不提供 |

所有 HUD 都是只读展示，绘制出的格子和按钮不会触发点击、合成或交易。有布局或贴图不等于完整复现原版界面；第三方导出器应明确声明自身提供的数据。

## 测试

适配器测试：`node --test integrations/mineflayer/test/*.test.js`。客户端测试和构建：`./gradlew build`。覆盖实际 HTTP SSE 连接／重连、窗口生命周期、cursor、开窗前属性、2×2 合成边界、交易价格和完整 NBT。

参考 CurioObserver v1 数据流的窗口事件仍兼容，无关的思想、任务和 dashboard 事件会被忽略。当前项目不在运行时读取参考仓库。具体 Mineflayer／prismarine 版本仍应进行真实 bot、服务器和图形客户端联调；自动化测试不替代游戏内验证。
