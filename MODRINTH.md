# No Original Trains

**Removes Minecraft Transit Railway's 378 built-in trains, without touching a single one of your addon packs.**

## What it does

MTR 4.0.5 ships with 423 vehicle definitions of its own. 378 of them are built-in trains. This mod removes **exactly those 378, and nothing else**:

- **Gone from the vehicle list** — no more scrolling past trains you will never place.
- **Never loaded** — their models and textures are not registered, so they cost no memory.
- **Addon packs are safe** — any vehicle declared by another resource pack or addon is automatically allowed through.
- **Trains only** — MTR's boats, airships and cable cars are kept.

## Why you should download it

- **You run addon trains, not vanilla ones.** If your world is built around a custom pack, MTR's stock trains are just clutter in the spawn list. This clears them out in one step.
- **No configuration, ever.** Install a new addon pack and it keeps working. You never edit a whitelist, never reinstall the mod, never restart with a different setting.
- **It also fixes addon gangways.** Some addons (especially ones using the older resource format) end up with missing, black-and-purple, or oddly long connection pieces between carriages. This mod corrects the gangway texture path and makes a gangway render at every coupling point — **without modifying your addon zip**.

## How the addon-safety works

At startup the mod scans every installed resource pack and reads their MTR vehicle manifests. The manifest that contains all of MTR's own built-in IDs is MTR itself; every other manifest is treated as "someone else's pack". Any vehicle ID claimed by one of those other packs is **never blocked**, even if it happens to share a name with a built-in train. If the scan cannot identify MTR's own manifest, the mod falls back to plain blacklist behaviour rather than risking your addons.

## Before you download — please read

- **Client-side only.** You install it in `mods/` and that is it. Servers do not need it.
- **Multiplayer caveat.** Because it works on your client's vehicle registry, built-in trains are invisible **to you**. On a server where other players use MTR's stock trains, you will not see those trains. It is aimed at single-player worlds and servers that use addon trains exclusively.
- **Requirements:** Minecraft 1.20.1, Fabric Loader, MTR **4.0.5 or newer**. Forge/NeoForge and other Minecraft versions are not supported.
- **It adds no blocks or items, and never modifies your world or your addon files.** Removing the mod at any time restores the default behaviour.
- **Config (optional):** `config/mtr_no_original_trains.json` is generated on first launch.
  - `enabled` — master switch
  - `fixGangway` — set to `false` to leave addon gangways alone
  - `keepIds` — built-in train IDs you want back
  - `extraRemoveIds` — extra IDs you want removed
- **Verify it is running:** check your log for these lines.
  ```
  [MTR_NoOriginalTrains] 已启用（纯黑名单模式）：内置黑名单 378 个
  [MTR_NoOriginalTrains] 黑名单模式：本体清单来自资源包「mod/mtr」
  ```

## Source and license

MIT licensed, source available on GitHub. Issues and pull requests are welcome.

The project icon is derived from MTR's official texture (`assets/mtr/textures/item/train.png`).
MTR is released under the MIT license, copyright (c) 2021 LogisticsCraft. Thanks to the MTR team
for building and open-sourcing it — this addon would not exist without it.

---

## 中文说明（原版列车移除）

移除 MTR 4.0.5 内置的 **378 个原版列车**，一个追加包都不碰。

- **只删原版车** —— 不进建造列表、不加载模型与贴图（省内存）；MTR 自带的船、飞艇、缆车保留。
- **追加包全自动放行** —— 启动时扫描所有资源包，凡是被其他包声明过的车辆 ID 一律不拦截。装新追加包**无需任何配置**。
- **顺带修连接件** —— 纠正追加包写错的 gangway 贴图路径，并让每处车厢接缝都渲染风挡；**不改动你的追加包 zip**。
- **仅客户端**，需 Minecraft 1.20.1 + Fabric + MTR 4.0.5 及以上。
- **注意**：因为是客户端过滤，原版列车对你**不可见**；如果服务器上别人在开原版车，你看不到它们。适合单人存档或只用追加包的服务器。
