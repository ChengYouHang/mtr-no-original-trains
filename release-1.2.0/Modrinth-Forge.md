# No Original Trains — Forge

**Removes MTR's 378 built-in trains — without touching a single one of your addon packs.**

Forge build for Minecraft 1.20.1. Works with any MTR 4.x. Since 1.2.0 you can pick which built-in trains to keep right from the title screen.

> This is the **Forge** version. A Fabric build is published as a separate version on this page — install only the one that matches your loader.

## Why install it

MTR 4.0.5 defines **423 vehicles of its own, 378 of which are built-in trains**. All of them get registered and loaded at startup — models, textures and all — whether or not you ever place one.

If your world runs on addon packs, those 378 are dead weight. This mod removes exactly those definitions, so they are never registered, never loaded, and never occupy memory or texture space.

- **Gone from the vehicle list** — no more scrolling past trains you will never spawn.
- **Never loaded** — models and textures are not registered, so they cost no memory.
- **Addon packs are safe** — any vehicle declared by another resource pack is automatically allowed through.
- **Trains only** — MTR's boats, airships and cable cars are kept.

FPS does not change. What changes is how much the game carries around before you even leave the main menu.

## Pick what to keep — new in 1.2.0

You do not have to remove all 378. A **MTR train selection** button on the main menu opens a picker:

- **Grouped by family.** Ticking "SP1900" keeps the whole family — no hunting for individual car IDs.
- **Select all / select none / reset to default**, with a collapsible list and a live count (`kept 4 / 32 families`).
- **Saving asks about restarting.** The dialog offers restart now, restart later, or never show again. Nothing applies until the game restarts, and it tells you so instead of leaving you guessing.
- **The button finds its own spot.** It anchors directly above the Options/Quit row and shifts the vanilla buttons down if it needs the room — including Forge layouts where Realms and Mods share a row — so it stays put at any GUI scale.

Prefer the config file? Nothing changed — `keepIds` and `extraRemoveIds` work exactly as before. The picker just writes them for you.

## Works with any MTR 4.x

There is no hard-coded train list. At startup the mod reads the vehicle definitions out of whatever MTR build you have installed, determines which of them are MTR's own built-ins, and filters those.

- **Every MTR 4.0.x and 4.1.x release works**, including betas and pre-releases. The exceptions are 4.0.0-beta-8 through beta-11, which lack an internal method this mod needs.
- **Future MTR 4.x releases work too.** When MTR adds, removes or renames a built-in train, this mod follows automatically — no update, no config edit, no reinstall.
- **Fallback kept.** If MTR's own definitions cannot be identified for any reason, the mod falls back to a bundled blacklist rather than risking your addons.

## Also fixes addon gangways

Some addon packs — especially ones using the older resource format — end up with missing, black-and-purple, or oddly stretched connection pieces between carriages. This mod corrects the gangway texture path and renders a gangway at every coupling point, **without modifying your addon zip**.

## Forge build details

| | |
|---|---|
| Loader | **Forge 47.x** |
| Minecraft | 1.20.1 |
| MTR | 4.0.0 or newer (any 4.x) |
| Environment | Client-side only |
| File | `mtr_no_original_trains-forge-1.2.0.jar` |

## Required dependencies

All three are mandatory. If any is missing the game stops at the loading screen and names the one that is absent.

| Mod | Why |
|---|---|
| **Minecraft Transit Railway** | The mod this is an addon for. MTR 4.0.0 – 4.1.x on 1.20.1. |
| **FerriteCore (Forge)** | Deduplicates block and item model data in memory — the single largest memory saving on 1.20.1. |
| **ModernFix (Forge)** | Shorter startup, lower memory use, and a long list of bug fixes. |

**Why hard requirements.** MTR addon packs routinely ship very large textures. Combined with decorative mods, the block atlas can grow past the point where Minecraft can build mipmaps, hanging the game on startup or world load. FerriteCore and ModernFix are the two mods that address this directly; without them this mod's goal — running high-quality addon trains on modest hardware — is not achievable. Get the **Forge** build of each.

## Before you download

- **Client-side only.** Drop it in `mods/` and that is it. Servers do not need it.
- **Multiplayer caveat.** It filters your client's vehicle registry, so built-in trains become invisible to you. On a server where other players use MTR's stock trains, you will not see those trains. Aimed at single-player and servers that use addon trains exclusively.
- **Upgrading from 1.1.0?** Replace the jar — the file name now includes the loader, so delete the old one first. Your config is untouched.
- **Upgrading from 1.0.0?** Install FerriteCore and ModernFix *before* replacing the jar, or the game will refuse to start.
- **No blocks, no items, no world changes.** Removing the mod restores stock behaviour completely.
- **Old MTR 4.0.0 betas are not supported.** beta-8 through beta-11 lack an internal method this mod needs. beta.12 and later are fine.
- **Not supported:** Fabric (use the Fabric version), NeoForge on 1.20.1, other Minecraft versions.
- **Verify it is running.** Search the log for `[MTR_NoOriginalTrains]` — you should see one line reporting how many built-in trains were removed and one naming the resource pack that supplied the built-in list.

## Source and license

MIT licensed. Source, issues and pull requests: [GitHub](https://github.com/ChengYouHang/mtr-no-original-trains).

The project icon is derived from MTR's official texture (`assets/mtr/textures/item/train.png`). MTR is MIT licensed, copyright (c) 2021 LogisticsCraft. Thanks to the MTR team for building and open-sourcing it.
