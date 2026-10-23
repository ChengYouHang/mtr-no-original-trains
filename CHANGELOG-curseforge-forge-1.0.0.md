## No Original Trains 1.0.0 — Forge

**First public release.** Removes MTR's 378 built-in trains and only those, and fixes addon train gangways. Addon packs are never touched.

**File:** `mtr_no_original_trains-forge-1.0.0.jar`

### What it does

- Removes MTR's 378 built-in trains from the vehicle list.
- Those trains are never loaded, so their models and textures cost no memory.
- MTR's boats, airships and cable cars are kept — only trains are removed.
- Fixes addon train gangways (missing, black-and-purple, or oddly long connection pieces) without modifying your addon zip.
- Addon vehicles are detected at runtime and allowed through automatically. No whitelist editing, ever.

### Requirements

- Minecraft 1.20.1
- Forge 47.x
- Minecraft Transit Railway **4.0.5**

No other mods required. 1.0.0 has no mandatory dependencies beyond MTR itself.

### Notes

- **Client-side only.** Do not install it on a server; servers do not need it.
- This Forge build is pinned to MTR 4.0.5. It will refuse to load on any other MTR version.
- Config is generated at `config/mtr_no_original_trains.json` on first launch:
  - `enabled` — master switch
  - `fixGangway` — set to `false` to leave addon gangways alone
  - `keepIds` — built-in train IDs you want back
  - `extraRemoveIds` — extra IDs you want removed
- Search your log for `[MTR_NoOriginalTrains]` to confirm it is running.

### Known limitations

- On a server where other players use MTR's stock trains, you will not see those trains — the filter runs on your client.
- **This build only works with MTR 4.0.5.** It will not load on 4.0.0–4.0.4, 4.1.x or any other version. Wider MTR support is coming in 1.1.0.
- **NeoForge is not supported for 1.20.1.** MTR has no NeoForge build for Minecraft 1.20.1, so there is nothing to attach to.
- **Do not install the Fabric build alongside this one.** Both share a mod ID and will conflict — pick the jar that matches your loader.
