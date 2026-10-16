## 1.1.0 — works with any MTR 4.x

This release makes the mod **independent of a specific MTR version**. It is no longer built against a frozen list of train IDs taken from MTR 4.0.5 — it now reads MTR's own vehicle manifest at runtime and works out which trains are MTR's built-ins by itself.

### What changed

- **No more hard-coded train list.** At startup the mod scans every installed resource pack and finds the manifest that MTR itself ships (the one that contains all of MTR's own vehicle IDs). Whatever trains that manifest declares are the ones that get removed.
- **MTR 4.x updates are picked up automatically.** When MTR adds, renames, or removes a built-in train in a future 4.x release, the mod follows along — no update to this mod required, no config edit, no reinstall.
- **Addon packs remain 100% safe.** Identification uses two independent checks (ID-set match plus pack-name match). A pack that only *looks* like MTR is never mistaken for it, and any vehicle claimed by another pack is still never blocked.
- **Fallback kept.** If MTR's own manifest cannot be identified for any reason, the mod falls back to the original 4.0.5 blacklist rather than risking your addons. You will see which path was taken in the log.
- **Clearer log line.** The startup report now says how many IDs are in effect this run and where that list came from.

### ⚠️ BREAKING CHANGE — new required dependencies

Starting with 1.1.0, this mod **requires two additional mods** and **will refuse to load without them**:

- **FerriteCore** — compresses the block atlas in memory
- **ModernFix** — dynamic atlas optimisation and faster startup

**Why.** MTR addon packs routinely ship very large textures. Combined with decorative mods, the block atlas (`textures/atlas/blocks.png`) can grow past the point where Minecraft can build its mipmaps, which causes the game to hang on startup or on world load. FerriteCore and ModernFix are the two mods that directly address this; without them this mod's own purpose (running high-quality addon trains on modest hardware) is not achievable.

**What you will see if they are missing.** The game will stop at the loading screen with a "missing mandatory dependencies" error naming `ferritecore` and `modernfix`. It will not reach the main menu. Install both and it loads normally.

**Upgrading from 1.0.0.** Install FerriteCore and ModernFix first, then replace the jar. Your config file is not touched and no setting needs changing.

### Requirements

- Minecraft 1.20.1
- Fabric Loader 0.14.21 or newer
- Minecraft Transit Railway **4.0.5 or newer** (any 4.x)
- **FerriteCore** and **ModernFix** (both are hard requirements from this version on)
