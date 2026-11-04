No Original Trains

Removes MTR's 378 built-in trains — without touching a single one of your addon packs.

Client-side only. Minecraft 1.20.1. Two builds, Fabric and Forge — install the one that matches your loader.


WHY THIS IS AN OPTIMIZATION MOD

MTR 4.0.5 ships with 423 vehicle definitions of its own, 378 of which are built-in trains. Every one of them is registered and loaded at startup — models, textures and all — whether or not you ever place a single one.

If your world runs on an addon pack, that is pure dead weight. This mod removes exactly those 378 definitions, so they are never registered, never loaded, and never take up memory or texture space. Your FPS does not change; what changes is how much the game has to carry around before you have even left the main menu.


WHAT IT DOES

- Gone from the vehicle list — no more scrolling past trains you will never place.
- Never loaded — their models and textures are not registered, so they cost no memory.
- Addon packs are safe — any vehicle declared by another resource pack is allowed through by default.
- Trains only — MTR's boats, airships and cable cars are kept.


PICK WHAT TO KEEP, IN-GAME

You do not have to give up all 378. The main menu has a "MTR Train Selection" button, which opens a small hub with three choices: Built-in Trains, Addon Trains, Cancel.

Built-in Trains opens the family picker:

- Tick the train families you want to keep. Families, not individual cars, so keeping "SP1900" is one click rather than eight.
- Select All / Select None / Reset, plus a collapsible list when it gets long.
- A live count of what you have kept, e.g. "Keeping 4 / 32 families".
- Saving asks whether to restart the game now or later — the change only applies after a restart.

The button anchors itself directly above the Options/Quit row. If there is not enough room it pushes the vanilla buttons down, and if that is still not enough it shrinks itself. It never covers them and never drops below Quit. Works at any GUI scale, including the Forge layout where Realms and Mods share a single row.

If you would rather stay in the config file, nothing changed: keepIds and extraRemoveIds work exactly as before.


FILTER ADDON PACK TRAINS TOO

Addon Trains lists every pack in your resource pack folder that actually declares vehicles.

- Only real train packs are listed. A pack appears only if it ships a mtr:mtr_custom_resources.json containing vehicles. Texture packs, shader packs and sound packs never show up, however many you have installed.
- Old and new pack formats both work. Packs written for MTR 3 (custom_trains) and packs written for MTR 4 (vehicles[]) are read the same way.
- Grouped by line, then by model. Addon packs almost never tag their families, so the mod derives them from the names and IDs the pack declares itself. The Shanghai pack's 248 cars open as 18 lines; press + on a line to see its models, press + on a model to see individual cars. Tick a line to block the whole line in one click, or drill down and block a single model.
- Nested rows drop the repeated line prefix, so a model under Shanghai Metro Line 16 shows as 16A01(AC19) instead of repeating the whole line name on every row.
- Names are read from the pack file, so a car still shows its real name after you have blocked it — no raw IDs.
- Nothing is blocked unless you tick it. Every addon pack car is allowed through by default, and a built-in train on your keep list stays no matter what you do here.


WHICH FILE DO I NEED

Fabric — mtr_no_original_trains-fabric-1.2.1.jar
Minecraft 1.20.1, Fabric Loader 0.14.21 or newer.
Requires FerriteCore (Fabric) and ModernFix (Fabric).

Forge — mtr_no_original_trains-forge-1.2.1.jar
Minecraft 1.20.1, Forge 47.x.
Requires FerriteCore (Forge) and ModernFix (Forge).

Both builds share the same mod ID and the same behaviour. Install one of them, never both.


REQUIRED DEPENDENCIES

Both builds need Minecraft Transit Railway 4.0.0 or newer, plus FerriteCore and ModernFix as hard requirements. If any of the three is missing, the game stops at the loading screen and names the one that is absent.

Why FerriteCore and ModernFix are hard requirements: MTR addon packs routinely ship very large textures. Combined with decorative mods, the block atlas can grow past the point where Minecraft can build its mipmaps, which causes the game to hang on startup or on world load. These two mods directly address that; without them this mod's own purpose — running high-quality addon trains on modest hardware — is not achievable.


WORKS WITH ANY MTR 4.x

There is no hard-coded list of trains. At startup the mod reads the train definitions straight out of whatever MTR build you have installed, works out which of them are MTR's own built-ins, and filters those.

- Every MTR 4.0.x and 4.1.x release works, including betas and pre-releases. The only exceptions are 4.0.0-beta-8 through beta-11, which lack an internal method this mod hooks into.
- Future MTR 4.x releases work too — when MTR adds, removes or renames built-in trains, this mod follows automatically. No update needed on my side.
- If the scan cannot identify MTR's own definitions for any reason, it falls back to a bundled list rather than risking your addon packs.


IT ALSO FIXES ADDON GANGWAYS

Some addons — especially ones using the older resource format — end up with missing, black-and-purple, or oddly long connection pieces between carriages. This mod corrects the gangway texture path and makes a gangway render at every coupling point, without modifying your addon zip.


BEFORE YOU DOWNLOAD

- Client-side only. Drop it in mods/ and that is it. Servers do not need it.
- Multiplayer caveat. Because it filters your client's vehicle registry, built-in trains become invisible to you. On a server where other players use MTR's stock trains, you will not see those trains. The mod is aimed at single-player worlds and servers that use addon trains exclusively.
- Upgrading from an older version? Delete the previous jar before installing. The file name includes the loader, and both builds share the same mod ID.
- It adds no blocks or items, and never modifies your world or your addon files. Removing the mod at any time restores the default behaviour.
- Config (optional): config/mtr_no_original_trains.json is generated on first launch. Everything there can also be set from the in-game picker — enabled, fixGangway, keepIds, extraRemoveIds, blockedAddonIds.
- Verify it is running: search your log for [MTR_NoOriginalTrains]. You should see one line reporting how many built-in trains were removed, and one reporting which resource pack supplied the built-in list.
- Not supported: NeoForge on 1.20.1 (MTR has no 1.20.1 NeoForge build), other Minecraft versions.


SOURCE AND LICENSE

MIT licensed, source available on GitHub: https://github.com/ChengYouHang/mtr-no-original-trains
Issues and pull requests are welcome.

The project icon is derived from MTR's official texture (assets/mtr/textures/item/train.png). MTR is released under the MIT license, copyright (c) 2021 LogisticsCraft. Thanks to the MTR team for building and open-sourcing it — this addon would not exist without it.
