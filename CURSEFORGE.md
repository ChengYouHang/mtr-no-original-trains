# No Original Trains

**Removes MTR's 378 built-in trains — without touching a single one of your addon packs.** Available for **Fabric** and **Forge** on Minecraft 1.20.1, working with **any MTR 4.x**.

## Why this is an optimization mod

MTR 4.0.5 ships with **423 vehicle definitions of its own, 378 of which are built-in trains**. Every one of them is registered and loaded at startup — models, textures and all — whether or not you ever place a single one.

If your world runs on an addon pack, that is pure dead weight. This mod removes exactly those 378 definitions, so they are never registered, never loaded, and never take up memory or texture space. Your FPS does not change; what changes is how much the game has to carry around before you have even left the main menu.

## What it does

- **Gone from the vehicle list** — no more scrolling past trains you will never place.
- **Never loaded** — their models and textures are not registered, so they cost no memory.
- **Addon packs are safe** — any vehicle declared by another resource pack or addon is automatically allowed through.
- **Trains only** — MTR's boats, airships and cable cars are kept.

## Works with any MTR 4.x

Version 1.1.0 no longer has a hard-coded list of trains. At startup the mod reads the train definitions straight out of whatever MTR build you have installed, works out which of them are MTR's own built-ins, and filters those. That means:

- **Every MTR 4.0.x and 4.1.x release works**, including betas and pre-releases.
- **Future MTR 4.x releases work too** — when MTR adds, removes or renames built-in trains, this mod follows automatically. No update needed on my side.
- If the scan cannot identify MTR's own definitions for any reason, it falls back to a bundled list rather than risking your addon packs.

## Two builds, one behaviour

This mod ships as **two separate jars** — a **Fabric** build and a **Forge** build. Both do exactly the same thing; you only need to pick the one that matches your mod loader.

| | Fabric build | Forge build |
|---|---|---|
| Loader | Fabric Loader 0.14.21+ | Forge 47.x |
| Minecraft | 1.20.1 | 1.20.1 |
| MTR version | **4.0.0 or newer** (any 4.x) | **4.0.0 or newer** (any 4.x) |
| Environment | Client-side only | Client-side only |
| File | `mtr_no_original_trains-1.1.0.jar` | `mtr_no_original_trains-forge-1.1.0.jar` |

Install **one** of them — never both in the same `mods/` folder. They share a mod ID, so putting both in will conflict.

## Required dependencies — 1.1.0 and newer

Starting with 1.1.0, this mod **will not load without** the following two mods. They are declared as mandatory, so if either is missing the game stops at the loading screen and tells you which one is absent. This is intentional: both are themselves optimization mods, they are on every serious 1.20.1 client anyway, and running without them would undercut the whole point of this mod.

| Mod | Why it is required | Download |
|---|---|---|
| **FerriteCore** | Deduplicates block and item model data in memory — the largest single memory saving available on 1.20.1 | [Modrinth](https://modrinth.com/mod/ferrite-core) · [CurseForge](https://www.curseforge.com/minecraft/mc-mods/ferritecore) |
| **ModernFix** | Cuts startup time and memory use across the board, plus a long list of known-bug fixes | [Modrinth](https://modrinth.com/mod/modernfix) · [CurseForge](https://www.curseforge.com/minecraft/mc-mods/modernfix) |

**Minecraft Transit Railway (MTR) is also required** — obviously, since this is an addon for it. Supported: MTR 4.0.0 through 4.1.x on Minecraft 1.20.1.

## Why you should download it

- **You run addon trains, not vanilla ones.** If your world is built around a custom pack, MTR's stock trains are just clutter in the spawn list. This clears them out in one step.
- **No configuration, ever.** Install a new addon pack and it keeps working. You never edit a whitelist, never reinstall the mod, never restart with a different setting. Since 1.1.0 you do not even need to update when MTR itself updates.
- **It also fixes addon gangways.** Some addons — especially ones using the older resource format — end up with missing, black-and-purple, or oddly long connection pieces between carriages. This mod corrects the gangway texture path and makes a gangway render at every coupling point, **without modifying your addon zip**.

## How the addon-safety works

At startup the mod scans every installed resource pack and reads their MTR vehicle manifests. The manifest that contains all of MTR's own built-in IDs is MTR itself; every other manifest belongs to someone else's pack. Any vehicle ID claimed by one of those other packs is **never blocked**, even if it happens to share a name with a built-in train. If the scan cannot identify MTR's own manifest, the mod falls back to plain blacklist behaviour rather than risking your addons.

## Before you download — please read

- **Client-side only.** Install it in `mods/` and that is it. Servers do not need it.
- **Multiplayer caveat.** Because it filters your client's vehicle registry, built-in trains become invisible **to you**. On a server where other players use MTR's stock trains, you will not see those trains. The mod is aimed at single-player worlds and servers that use addon trains exclusively.
- **Upgrading from 1.0.0?** 1.1.0 adds FerriteCore and ModernFix as required dependencies. Install them **before** you replace the jar, or the game will refuse to start.
- **It adds no blocks or items, and never modifies your world or your addon files.** Removing the mod at any time restores the default behaviour.
- **Not supported:** NeoForge on 1.20.1 — MTR has no 1.20.1 NeoForge build to attach to. Other Minecraft versions are not supported yet.
- **Config (optional):** `config/mtr_no_original_trains.json` is generated on first launch.
  - `enabled` — master switch
  - `fixGangway` — set to `false` to leave addon gangways alone
  - `keepIds` — built-in train IDs you want back
  - `extraRemoveIds` — extra IDs you want removed
- **Verify it is running:** search your log for `[MTR_NoOriginalTrains]`. You should see one line reporting how many built-in trains were removed, and one reporting which resource pack supplied the built-in list.
- **Old MTR 4.0.0 betas are not supported.** MTR 4.0.0-beta-8 through beta-11 lack an internal method this mod hooks into. All MTR 4.0.0-beta.12 and later work fine.

## Release notes in Chinese

### 1.1.0 — 支持任意 MTR 4.x

本版本让模组**不再绑定某个特定的 MTR 版本**。它不再依赖从 MTR 4.0.5 里抄下来的一份固定列车 ID 列表，而是运行时读取 MTR 自己的车辆清单，自行判断哪些是 MTR 的内置列车。

- **不再有硬编码列车列表。** 启动时扫描所有已安装的资源包，找出 MTR 自己附带的那份清单（包含 MTR 全部内置车辆 ID 的那份），清单里声明了什么就删什么。
- **MTR 4.x 更新自动跟进。** 未来 MTR 新增、改名或删除内置列车时，本模组会自动跟随，无需等待更新、无需改配置、无需重装。
- **追加包依然 100% 安全。** 识别采用双重校验（ID 集合匹配 + 包名匹配），只是「看起来像 MTR」的包不会被误认，被其他包声明过的车辆永远不会被屏蔽。
- **保留兜底方案。** 万一识别不出 MTR 自己的清单，会退回 4.0.5 的原始黑名单，绝不拿你的追加包冒险。
- **Forge 版不再锁死 4.0.5。** 1.0.0 的 Forge 版在 MTR 4.0.6+ 或 4.1.x 下会拒绝加载，本版本接受任意 MTR 4.x。

### ⚠️ 破坏性变更 —— 新增必需前置

从 1.1.0 起，本模组**缺少以下两个模组就不会加载**：

- **FerriteCore** —— 在内存中去重方块 / 物品模型数据
- **ModernFix** —— 动态图集优化与更快的启动

**原因。** MTR 追加包经常携带超大贴图，叠加装饰类模组后，方块图集（`textures/atlas/blocks.png`）会膨胀到 Minecraft 无法生成 mipmap 的程度，导致游戏在启动或加载存档时卡死。FerriteCore 与 ModernFix 正是直接解决这个问题的两个模组；没有它们，本模组「在普通配置上跑高画质追加包列车」的目标就无从谈起。

**缺失时的表现。** 游戏会停在加载界面，并报出点名 `ferritecore` 与 `modernfix` 的「缺少必需依赖」错误，进不到主菜单。装齐后即可正常加载。

**从 1.0.0 升级。** 请先安装这一侧对应的 FerriteCore 与 ModernFix，再替换 jar。配置文件不会被改动，也不需要改任何设置。

### 需求

- Minecraft 1.20.1
- Fabric Loader 0.14.21+ 或 Forge 47.x
- Minecraft Transit Railway **4.0.0 或更高**（任意 4.x）
- **FerriteCore** 与 **ModernFix**（本版本起为硬前置）

### 文件说明

- **Fabric：** `mtr_no_original_trains-1.1.0.jar`
- **Forge：** `mtr_no_original_trains-forge-1.1.0.jar`

两者只能装一个，不能同时放进 `mods/`。

---

## Source and license

MIT licensed, source available on GitHub. Issues and pull requests are welcome.

The project icon is derived from MTR's official texture (`assets/mtr/textures/item/train.png`).
MTR is released under the MIT license, copyright (c) 2021 LogisticsCraft. Thanks to the MTR team
for building and open-sourcing it — this addon would not exist without it.
