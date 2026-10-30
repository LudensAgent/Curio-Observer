# Curio Observer

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

[English](README.md) | 简体中文

纯客户端旁观 HUD 模组，适用于 Minecraft 1.20.1（Fabric）。在旁观模式下附身任意玩家时，
绘制该玩家的生命 / 护甲 / 手持 / 背包等信息，并提供「暗处增亮」与「自动视角」两个画面辅助功能。

> 本模组**不向服务器发送任何数据包**，无需在服务端安装任何模组。

## 功能

- **旁观 HUD**：`vitals`（生命/护甲/饥饿/氧气/经验）、`inventory`（装备/背包/副手/主手）、`view`（画面辅助状态）、`hands`（第一人称手持渲染）。
- **暗处增亮**：替换光图 gamma，在黑暗/洞穴中看得更清楚。
- **自动视角**：旁观时自适应第三人称/第一人称的广播相机。
- **可选 RCON 通道**：服务端开启 `enable-rcon=true` 后，可拉取被观察玩家的完整背包与饥饿/经验数据（否则这些值按默认显示）。

## 按键（默认）

| 按键 | 功能 |
|------|------|
| `O` | 切换旁观 HUD |
| `B` | 切换「暗处增亮」 |
| `V` | 切换「自动视角」 |
| `K` | 打开观察者设置 |

所有按键可在「选项 → 控制 → 按键绑定」中修改，开关状态持久化到 `config/curio-observer.json`。

## 依赖

- Minecraft **1.20.1**
- [Fabric Loader](https://fabricmc.net/use/) `>= 0.18.5`
- [Fabric API](https://modrinth.com/mod/fabric-api)
- Java **17+**

## 安装

1. 安装 Fabric Loader（针对 1.20.1）与 Fabric API。
2. 从 [Releases](../../releases)（或 GitHub Actions 构建产物）下载 `curio-observer-<version>.jar`。
3. 将 jar 放入 `.minecraft/mods/` 目录，启动游戏。

## 构建

要求 Java 17。在仓库根目录执行：

```bash
./gradlew build          # Linux / macOS
gradlew.bat build        # Windows
```

产物位于 `build/libs/curio-observer-0.3.8.jar`。运行单元测试与 release 兼容性测试：

```bash
./gradlew test
```

## 配置

首次运行后会在 `config/curio-observer.json` 生成配置文件。RCON 相关项默认关闭：

```jsonc
{
  "rconEnabled": false,
  "rconHost": "127.0.0.1",
  "rconPort": 25575,
  "rconPassword": "",
  "rconPollSeconds": 1
}
```

> RCON 需要在服务端 `server.properties` 中设置 `enable-rcon=true` 与 `rcon.password`，
> 并在本模组配置里填写对应密码后才生效。

## 已知限制

旁观者只能看到服务器同步给它的数据，因此：

- 生命、护甲、主手/副手、氧气（气泡）可正常显示。
- 饥饿/经验值对被观察的远端玩家通常显示默认值（服务端不会同步给旁观者），除非开启 RCON。
- 完整背包（27 格）与打开的工作台/熔炉/附魔台等容器界面无法被旁观者看到；「背包」面板只展示客户端实际能拿到的装备与手持物品。

## 许可证

本项目使用 [MIT 协议](LICENSE)。
