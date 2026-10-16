## No Original Trains 1.0.0 — Forge

**First Forge release.** Same behaviour as the Fabric build, now on Forge 47.x for Minecraft 1.20.1.

### What it does

Removes MTR's 378 built-in trains and only those, and fixes addon train gangways. Addon packs are never touched.

### Requirements

- Minecraft 1.20.1
- Forge 47.4.26 (any 47.x works)
- Minecraft Transit Railway **4.0.5** — this build is pinned to 4.0.5 and will refuse to load with 4.0.6+ or 4.1.x

### Notes

- **Client-side only.** Do not install it on a server; the mod will abort with an error if it detects a dedicated server.
- If you use both Fabric and Forge, install only the build that matches your loader. Do not put both in the same `mods/` folder.
- Built-in train list and gangway fix behave identically to the Fabric version — 378 IDs removed, addon vehicles detected at runtime and whitelisted automatically.

### Known limitations

- **NeoForge is not supported for 1.20.1.** MTR has no NeoForge build for Minecraft 1.20.1 (its NeoForge support starts at 4.1.0-beta on 1.21.x), so there is nothing to attach to.
- On a server where other players use MTR's stock trains, you will not see those trains — the filter runs on your client.
