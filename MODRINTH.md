# No Original Trains

**Removes Minecraft Transit Railway's 378 built-in trains — without touching a single one of your addon packs.**

Available for **Fabric** and **Forge** on Minecraft 1.20.1.

## What it does

MTR 4.0.5 ships with 423 vehicle definitions of its own. 378 of them are built-in trains. This mod removes **exactly those 378, and nothing else**:

- **Gone from the vehicle list** — no more scrolling past trains you will never place.
- **Never loaded** — their models and textures are not registered, so they cost no memory.
- **Addon packs are safe** — any vehicle declared by another resource pack or addon is automatically allowed through.
- **Trains only** — MTR's boats, airships and cable cars are kept.

## Why you should download it

- **You run addon trains, not vanilla ones.** If your world is built around a custom pack, MTR's stock trains are just clutter in the spawn list. This clears them out in one step.
- **No configuration, ever.** Install a new addon pack and it keeps working. You never edit a whitelist, never reinstall the mod, never restart with a different setting.
- **It also fixes addon gangways.** Some addons — especially ones using the older resource format — end up with missing, black-and-purple, or oddly long connection pieces between carriages. This mod corrects the gangway texture path and makes a gangway render at every coupling point, **without modifying your addon zip**.

## How the addon-safety works

At startup the mod scans every installed resource pack and reads their MTR vehicle manifests. The manifest that contains all of MTR's own built-in IDs is MTR itself; every other manifest belongs to someone else's pack. Any vehicle ID claimed by one of those other packs is **never blocked**, even if it happens to share a name with a built-in train. If the scan cannot identify MTR's own manifest, the mod falls back to plain blacklist behaviour rather than risking your addons.

## Which build do you need?

| | Fabric build | Forge build |
|---|---|---|
| Loader | Fabric Loader 0.14.21+ | Forge 47.x |
| Minecraft | 1.20.1 | 1.20.1 |
| MTR version | **4.0.5 or newer** (any 4.x) | **4.0.5 exactly** |
| Environment | Client-side only | Client-side only |
| File | `mtr_no_original_trains-1.1.0.jar` | `mtr_no_original_trains-forge-1.0.0.jar` |

Install **one** of them — never both in the same `mods/` folder. They share a mod ID, so putting both in will conflict.

## Before you download — please read

- **Client-side only.** Install it in `mods/` and that is it. Servers do not need it.
- **Multiplayer caveat.** Because it filters your client's vehicle registry, built-in trains become invisible **to you**. On a server where other players use MTR's stock trains, you will not see those trains. The mod is aimed at single-player worlds and servers that use addon trains exclusively.
- **It adds no blocks or items, and never modifies your world or your addon files.** Removing the mod at any time restores the default behaviour.
- **Not supported:** NeoForge on 1.20.1 — MTR has no 1.20.1 NeoForge build to attach to. Other Minecraft versions are not supported yet.
- **Config (optional):** `config/mtr_no_original_trains.json` is generated on first launch.
  - `enabled` — master switch
  - `fixGangway` — set to `false` to leave addon gangways alone
  - `keepIds` — built-in train IDs you want back
  - `extraRemoveIds` — extra IDs you want removed
- **Verify it is running:** search your log for `[MTR_NoOriginalTrains]`. You should see one line reporting how many built-in trains were removed, and one reporting which resource pack supplied the built-in list.

## Source and license

MIT licensed, source available on GitHub. Issues and pull requests are welcome.

The project icon is derived from MTR's official texture (`assets/mtr/textures/item/train.png`).
MTR is released under the MIT license, copyright (c) 2021 LogisticsCraft. Thanks to the MTR team
for building and open-sourcing it — this addon would not exist without it.
