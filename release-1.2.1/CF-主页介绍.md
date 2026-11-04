# No Original Trains

**Removes MTR's 378 built-in trains — and none of your addon packs.**

`Client-side only` · `Minecraft 1.20.1` · `Fabric + Forge` · `Pick what to keep in-game`

> **New in 1.2.1** — the in-game picker now covers addon pack trains as well as MTR's own. See **Addon Trains** below.

---

## At a glance

| Item | Detail |
| :--- | :--- |
| **What it removes** | 378 of the 423 vehicle definitions MTR 4.0.5 ships |
| **What it leaves alone** | Every addon pack vehicle, by default |
| **Where it runs** | Your client only — servers don't need it |
| **Loaders** | Fabric 0.14.21+ · Forge 47.x |
| **Hard requirements** | MTR 4.0.0+ · FerriteCore · ModernFix |

---

## Why this is an optimization mod

MTR 4.0.5 ships 423 vehicle definitions of its own, 378 of which are built-in trains. Every one of them is registered and loaded at startup — models, textures and all — whether or not you ever place a single one.

If your world runs on an addon pack, that is pure dead weight. This mod removes exactly those 378 definitions, so they are never registered, never loaded, and never take up memory or texture space.

Your FPS does not change. What changes is how much the game has to carry around before you have even left the main menu.

## What it does

- **Gone from the vehicle list** — no more scrolling past trains you will never place.
- **Never loaded** — models and textures are not registered, so they cost no memory.
- **Addon packs are safe** — any vehicle declared by another resource pack is allowed through by default.
- **Trains only** — MTR's boats, airships and cable cars are kept.

---

## Pick what to keep, in-game

You do not have to give up all 378. The main menu has a **MTR Train Selection** button, which opens a hub with three choices: **Built-in Trains**, **Addon Trains**, **Cancel**.

### Built-in Trains

- Tick the **families** you want to keep — families, not individual cars, so keeping `SP1900` is one click instead of eight.
- **Select All / Select None / Reset**, plus a collapsible list once it gets long.
- A live count of what you have kept, e.g. *Keeping 4 / 32 families*.
- Saving asks whether to restart now or later — the change applies after a restart.

> The button anchors itself above the Options/Quit row. If there is not enough room it pushes the vanilla buttons down; if that is still not enough it shrinks itself. It never covers them and never drops below Quit, at any GUI scale — including the Forge layout where Realms and Mods share a single row.

Prefer the config file? Nothing changed: `keepIds` and `extraRemoveIds` work exactly as before.

### Addon Trains · new in 1.2.1

Lists every pack in your resource pack folder that actually declares vehicles.

| Behaviour | Detail |
| :--- | :--- |
| **Only real train packs** | A pack appears only if it ships an `mtr:mtr_custom_resources.json` containing vehicles. Texture, shader and sound packs never show up, however many you have installed. |
| **Old and new formats** | MTR 3 packs (`custom_trains`) and MTR 4 packs (`vehicles[]`) are read the same way. |
| **Three levels deep** | Line → model → individual car. The Shanghai pack's 248 cars open as **18 lines**; a line with several models opens into those models. |
| **Tick a line** | Blocks every car on that line in one click. Drill down to block a single model. |
| **Real names** | Read from the pack file, so a car still shows its name after you have blocked it — no raw IDs. |
| **Nothing blocked by default** | Every addon car passes unless you tick it. A built-in train on your keep list stays no matter what you do here. |

---

## Which file do I need

| Build | File | Needs |
| :--- | :--- | :--- |
| **Fabric** | `mtr_no_original_trains-fabric-1.2.1.jar` | Minecraft 1.20.1 · Fabric Loader 0.14.21+ · FerriteCore (Fabric) · ModernFix (Fabric) |
| **Forge** | `mtr_no_original_trains-forge-1.2.1.jar` | Minecraft 1.20.1 · Forge 47.x · FerriteCore (Forge) · ModernFix (Forge) |

> ⚠️ Both builds share the same mod ID. Install one of them, never both.

## Required dependencies

Both builds need **Minecraft Transit Railway 4.0.0 or newer**, plus **FerriteCore** and **ModernFix** as hard requirements. If any of the three is missing, the game stops at the loading screen and names the one that is absent.

**Why FerriteCore and ModernFix are hard requirements:** MTR addon packs routinely ship very large textures. Combined with decorative mods, the block atlas can grow past the point where Minecraft can build its mipmaps, which causes the game to hang on startup or on world load. These two mods address that directly; without them this mod's own purpose — running high-quality addon trains on modest hardware — is not achievable.

## Works with any MTR 4.x

There is no hard-coded list of trains. At startup the mod reads the train definitions straight out of whatever MTR build you have installed, works out which of them are MTR's own built-ins, and filters those.

- Every **MTR 4.0.x and 4.1.x** release works, including betas and pre-releases. The only exceptions are 4.0.0-beta-8 through beta-11, which lack an internal method this mod hooks into.
- **Future MTR 4.x releases** work too — when MTR adds, removes or renames built-in trains, this mod follows automatically. No update needed on my side.
- If the scan cannot identify MTR's own definitions for any reason, it falls back to a bundled list rather than risking your addon packs.

## It also fixes addon gangways

Some addons — especially ones using the older resource format — end up with missing, black-and-purple, or oddly long connection pieces between carriages. This mod corrects the gangway texture path and makes a gangway render at every coupling point, without modifying your addon zip.

---

## Before you download

- **Client-side only.** Drop it in `mods/` and that is it. Servers do not need it.
- **Multiplayer caveat.** Because the filter runs on your client, built-in trains become invisible to you. On a server where other players use MTR's stock trains, you will not see those trains. The mod is aimed at single-player worlds and servers that use addon trains exclusively.
- **Upgrading from an older version?** Delete the previous jar before installing — the file name now includes the loader, and both builds share the same mod ID.
- **It adds no blocks or items**, and never modifies your world or your addon files. Removing the mod at any time restores the default behaviour.
- **Config (optional).** `config/mtr_no_original_trains.json` is generated on first launch. Everything there can also be set from the in-game picker: `enabled`, `fixGangway`, `keepIds`, `extraRemoveIds`, `blockedAddonIds`.
- **Verify it is running.** Search your log for `[MTR_NoOriginalTrains]`. You should see one line reporting how many built-in trains were removed, and one reporting which resource pack supplied the built-in list.
- **Not supported.** NeoForge on 1.20.1 (MTR has no 1.20.1 NeoForge build), and any Minecraft version other than 1.20.1.

---

## Source and license

MIT licensed · source on GitHub: [ChengYouHang/mtr-no-original-trains](https://github.com/ChengYouHang/mtr-no-original-trains)

Issues and pull requests are welcome.

---

*The project icon is derived from MTR's official texture (`assets/mtr/textures/item/train.png`). MTR is released under the MIT license, copyright (c) 2021 LogisticsCraft. Thanks to the MTR team for building and open-sourcing it — this addon would not exist without it.*
