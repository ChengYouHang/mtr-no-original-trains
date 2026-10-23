# No Original Trains

**MTR ships 378 built-in trains you will never place. This removes every one of them — without touching a single one of your addon packs.** Fabric and Forge, Minecraft 1.20.1.

## What it does

MTR 4.0.5 carries 423 of its own vehicle definitions. 378 of them are built-in trains, and every one is registered and loaded at startup — models, textures, the lot — whether or not you ever place a single one. This mod removes **exactly those 378, and nothing else**:

- **Gone from the vehicle list** — no more scrolling past trains you will never place.
- **Never loaded** — their models and textures are not registered, so they cost no memory and no texture space.
- **Addon packs are safe** — any vehicle declared by another resource pack or addon is automatically allowed through.
- **Trains only** — MTR's boats, airships and cable cars are kept.

## Works with any MTR 4.x

Version 1.1.0 has no hard-coded train list. At startup it reads the train definitions straight out of whatever MTR build you have installed, works out which of them are MTR's own built-ins, and filters those. That means:

- **Every MTR 4.0.x and 4.1.x release works**, including betas and pre-releases.
- **Future MTR 4.x releases work too** — when MTR adds, removes or renames a built-in train, this mod follows automatically. No update needed on my side, no config edit, no reinstall.
- If the scan cannot identify MTR's own definitions for any reason, it falls back to a bundled list rather than risking your addon packs.

## It also fixes addon gangways

Some addons — especially ones using the older resource format — end up with missing, black-and-purple, or oddly long connection pieces between carriages. This mod corrects the gangway texture path and makes a gangway render at every coupling point, **without modifying your addon zip**.

## Required dependencies — 1.1.0 and newer

This mod **will not load without** the following. All three are declared as required, so your launcher will prompt for them and the game will name whichever one is missing.

| Mod | Why |
|---|---|
| **Minecraft Transit Railway** | The mod this is an addon for. Supported: MTR 4.0.0 through 4.1.x on 1.20.1. |
| **FerriteCore** | Deduplicates block and item model data in memory — the largest single memory saving on 1.20.1. |
| **ModernFix** | Cuts startup time and memory use, plus a long list of known-bug fixes. |

**Why they are mandatory.** MTR addon packs routinely ship very large textures. Combined with decorative mods, the block atlas (`textures/atlas/blocks.png`) can grow past the point where Minecraft can build its mipmaps, which hangs the game on startup or on world load. FerriteCore and ModernFix directly address that; running without them would undercut the whole point of this mod.

## Two builds, one behaviour

Pick the jar that matches your mod loader. Install **one** — never both in the same `mods/` folder. They share a mod ID, so putting both in will conflict.

| | Fabric build | Forge build |
|---|---|---|
| Loader | Fabric Loader 0.14.21+ | Forge 47.x |
| Minecraft | 1.20.1 | 1.20.1 |
| MTR version | 4.0.0 or newer (any 4.x) | 4.0.0 or newer (any 4.x) |
| Environment | Client-side only | Client-side only |
| 1.1.0 file | `mtr_no_original_trains-1.1.0.jar` | `mtr_no_original_trains-forge-1.1.0.jar` |

## How the addon-safety works

At startup the mod scans every installed resource pack and reads their MTR vehicle manifests. The manifest that contains all of MTR's own built-in IDs is MTR itself; every other manifest belongs to someone else's pack. Any vehicle ID claimed by one of those other packs is **never blocked**, even if it happens to share a name with a built-in train. Identification uses two independent checks (ID-set match plus pack-name match), so a pack that merely *looks* like MTR is never mistaken for it. If the scan cannot identify MTR's own manifest, the mod falls back to plain blacklist behaviour rather than risking your addons.

## Before you download — please read

- **Client-side only.** Install it in `mods/` and that is it. Servers do not need it.
- **Multiplayer caveat.** Because it filters your client's vehicle registry, built-in trains become invisible **to you**. On a server where other players use MTR's stock trains, you will not see those trains. The mod is aimed at single-player worlds and servers that use addon trains exclusively.
- **Upgrading from 1.0.0?** 1.1.0 adds FerriteCore and ModernFix as required dependencies. Install them **before** you replace the jar, or the game will refuse to start.
- **It adds no blocks or items, and never modifies your world or your addon files.** Removing the mod at any time restores the default behaviour.
- **Not supported:** NeoForge on 1.20.1 — MTR has no 1.20.1 NeoForge build to attach to. Other Minecraft versions are not supported yet.
- **Old MTR 4.0.0 betas are not supported.** beta-8 through beta-11 lack an internal method this mod hooks into. beta.12 and later work fine.
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
