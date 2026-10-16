## 1.1.0 — works with any MTR 4.x

This release makes the mod **independent of a specific MTR version**. It is no longer built against a frozen list of train IDs taken from MTR 4.0.5 — it now reads MTR's own vehicle manifest at runtime and works out which trains are MTR's built-ins by itself.

### What changed

- **No more hard-coded train list.** At startup the mod scans every installed resource pack and finds the manifest that MTR itself ships (the one that contains all of MTR's own vehicle IDs). Whatever trains that manifest declares are the ones that get removed.
- **MTR 4.x updates are picked up automatically.** When MTR adds, renames, or removes a built-in train in a future 4.x release, the mod follows along — no update to this mod required, no config edit, no reinstall.
- **Addon packs remain 100% safe.** Identification uses two independent checks (ID-set match plus pack-name match). A pack that only *looks* like MTR is never mistaken for it, and any vehicle claimed by another pack is still never blocked.
- **Fallback kept.** If MTR's own manifest cannot be identified for any reason, the mod falls back to the original 4.0.5 blacklist rather than risking your addons. You will see which path was taken in the log.
- **Clearer log line.** The startup report now says how many IDs are in effect this run and where that list came from, e.g. `本次生效清单 378 条，来源：dynamic(mtr)`.

### Upgrading from 1.0.0

Just replace the jar. Your config file is not touched and no setting needs changing. If you are on MTR 4.0.5 nothing about the behaviour changes — the same 378 trains are removed.

### Requirements

- Minecraft 1.20.1
- Fabric Loader 0.14.21 or newer
- Minecraft Transit Railway **4.0.5 or newer** (any 4.x)

### Note

The Forge build is still on 1.0.0 and still pinned to MTR 4.0.5. It will receive the same version-independent behaviour in a later release.
