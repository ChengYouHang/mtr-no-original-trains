# No Original Trains

**Removes MTR's 378 built-in trains — without touching a single one of your addon packs.** Available for **Fabric** and **Forge** on Minecraft 1.20.1, working with **any MTR 4.x**.

Since **1.2.0** there is a button on the title screen that lets you pick which built-in trains to keep — no config editing required.

## Why this is an optimization mod

MTR 4.0.5 ships with **423 vehicle definitions of its own, 378 of which are built-in trains**. Every one of them is registered and loaded at startup — models, textures and all — whether or not you ever place a single one.

If your world runs on an addon pack, that is pure dead weight. This mod removes exactly those 378 definitions, so they are never registered, never loaded, and never take up memory or texture space. Your FPS does not change; what changes is how much the game has to carry around before you have even left the main menu.

## What it does

- **Gone from the vehicle list** — no more scrolling past trains you will never place.
- **Never loaded** — their models and textures are not registered, so they cost no memory.
- **Addon packs are safe** — any vehicle declared by another resource pack or addon is automatically allowed through.
- **Trains only** — MTR's boats, airships and cable cars are kept.

## Pick what to keep, in-game

You do not have to give up all 378. A button on the main menu — **"MTR train selection"** — opens a picker where you can:

- Tick the **train families** you want to keep (families, not individual cars, so "SP1900" is one click rather than eight).
- Use **select all / select none / reset to default**, and collapse the list when it gets long.
- See a live count of what you have kept (`kept 4 / 32 families`).
- Save, and get asked whether to **restart the game now** or later — because the change only applies after a restart.

The button finds its own spot above the Options/Quit row and shifts the vanilla buttons down if it needs the room, so it stays put at any GUI scale on both loaders.

If you would rather stay in the config file, nothing has changed: `keepIds` and `extraRemoveIds` still work exactly as before.

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
| File | `mtr_no_original_trains-fabric-1.2.0.jar` | `mtr_no_original_trains-forge-1.2.0.jar` |

Install **one** of them — never both in the same `mods/` folder. They share a mod ID, so putting both in will conflict. From 1.2.0 the loader is in the file name, so there is no longer any way to mix them up.

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
- **Upgrading from 1.1.0?** Just replace the jar — the file name changed to include the loader, so delete the old one first. Your config is untouched. From 1.0.0, install FerriteCore and ModernFix **before** replacing the jar, or the game will refuse to start.
- **It adds no blocks or items, and never modifies your world or your addon files.** Removing the mod at any time restores the default behaviour.
- **Not supported:** NeoForge on 1.20.1 — MTR has no 1.20.1 NeoForge build to attach to. Other Minecraft versions are not supported yet.
- **Config (optional):** `config/mtr_no_original_trains.json` is generated on first launch. Everything here can also be set from the in-game picker since 1.2.0.
  - `enabled` — master switch
  - `fixGangway` — set to `false` to leave addon gangways alone
  - `keepIds` — built-in train IDs you want back
  - `extraRemoveIds` — extra IDs you want removed
- **Verify it is running:** search your log for `[MTR_NoOriginalTrains]`. You should see one line reporting how many built-in trains were removed, and one reporting which resource pack supplied the built-in list.
- **Old MTR 4.0.0 betas are not supported.** MTR 4.0.0-beta-8 through beta-11 lack an internal method this mod hooks into. All MTR 4.0.0-beta.12 and later work fine.

## Release notes in Chinese

### 1.2.0 — 主菜单列车保留选择界面

本版本加入呼声最高的功能：**保留哪些内置列车，不必再改配置文件。** 主菜单上多了一个按钮，点开就是车系列表，勾选要保留的车系、保存、重启游戏生效。1.1.0 的原有功能完全不变，本版本只是新增。

**主菜单选择界面**

- **主菜单新增「MTR 列车保留选择」按钮**，点开即进入选择界面，不用手写车 ID，也不用改完配置才发现拼错。
- **按车系分组** —— 勾「SP1900」一次就行，不用一辆辆找车厢 ID。每个车系显示车辆数，界面右上角实时显示 `已保留 4 / 32 个车系`。
- **全选 / 全不选 / 恢复默认**，列表可折叠，追加包再多也不会挤成一团。
- **保存后提示重启** —— 弹窗给出「是，立即重启 / 否，稍后重启 / 不再显示此界面」，改完立即知道要不要重启才生效。
- **按钮自己找位置** —— 固定在「选项 / 退出」按钮行正上方，空间不够时把原版按钮整体下移，再不够就收缩自身高度，不会压住原版按钮，也不会掉到退出按钮下面。Fabric 与 Forge、任意 GUI 缩放比例均适用，包括 Forge 上 Realms 与「模组」并排的特殊布局。

**车系目录改为运行时构建**

- 列表直接来自你实际安装的 MTR 版本，MTR 4.x 更新新增或改名的列车会自动出现。
- 配置键未变，`keepIds` 与 `extraRemoveIds` 行为与之前一致，已有配置照常生效；界面只是替你写这两个键。

**修复**

- **保留车系后选择界面目录缩水、计数错乱。** 保留较多车系时，界面会显示成类似「已选择 32 / 1 个车系」的异常计数，车系列表也随之残缺，部分车系的映射失效。原因是车辆定义只在「真正被拦截」时才被记录 —— 保留得越多，模组知道的越少，目录便按残留部分重建，越缩越小。两处改动解决：
  - 内置列车首次出现即登记进车系目录，无论最终是否被拦截。
  - MTR 自带清单生成的静态映射与运行时映射分开保存，查找时静态映射兜底，运行时列表不全也能解析出车系。

  五条路径实测通过：全部拦截、保留 32 车系、保留 2 辆车、额外移除与保留冲突、总开关关闭。目录始终为 32 车系 / 378 辆。

**文件命名**

两个 jar 的文件名带上加载器：`mtr_no_original_trains-fabric-1.2.0.jar` 与 `mtr_no_original_trains-forge-1.2.0.jar`，避免桌面上两个同名 jar 装错。升级时请先删掉旧 jar。

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

- **Fabric：** `mtr_no_original_trains-fabric-1.2.0.jar`
- **Forge：** `mtr_no_original_trains-forge-1.2.0.jar`

两者只能装一个，不能同时放进 `mods/`。

---

## Source and license

MIT licensed, source available on GitHub. Issues and pull requests are welcome.

The project icon is derived from MTR's official texture (`assets/mtr/textures/item/train.png`).
MTR is released under the MIT license, copyright (c) 2021 LogisticsCraft. Thanks to the MTR team
for building and open-sourcing it — this addon would not exist without it.
