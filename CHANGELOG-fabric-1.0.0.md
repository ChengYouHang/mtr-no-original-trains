## No Original Trains 1.0.0 — Fabric

**First public release on Modrinth.** Earlier internal builds were never published under this project.

### What it does

Removes MTR's 378 built-in trains and only those, and fixes addon train gangways. Addon packs are never touched.

### Requirements

- Minecraft 1.20.1
- Fabric Loader 0.14.21 or newer
- Minecraft Transit Railway **4.0.5 or newer** (any 4.x)

### Notes

- **Client-side only.** Do not install it on a server; servers do not need it.
- MTR's boats, airships and cable cars are kept — only trains are removed.
- Addon vehicles are detected at runtime and whitelisted automatically. No whitelist editing, ever.
- Config is generated at `config/mtr_no_original_trains.json` on first launch:
  - `enabled` — master switch
  - `fixGangway` — set to `false` to leave addon gangways alone
  - `keepIds` — built-in train IDs you want back
  - `extraRemoveIds` — extra IDs you want removed
- Search your log for `[MTR_NoOriginalTrains]` to confirm it is running.

### Known limitations

- On a server where other players use MTR's stock trains, you will not see those trains — the filter runs on your client.
- **NeoForge is not supported for 1.20.1.** MTR has no NeoForge build for Minecraft 1.20.1 (its NeoForge support starts at 4.1.0-beta on 1.21.x), so there is nothing to attach to. Use the Forge build if you are on Forge.
