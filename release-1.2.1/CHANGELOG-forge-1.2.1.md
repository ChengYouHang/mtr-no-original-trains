## 1.2.1 — addon pack trains can now be filtered too

1.2.0 let you pick which **built-in** MTR trains to keep. This update extends the same picker to **trains added by addon packs**, so a 100-car Chinese metro pack no longer floods your train menu.

Nothing about the 1.2.0 flow changed. If you never open the addon tab, the mod behaves exactly as it did before.

### New: addon pack train filter

- **The main menu button now opens a small hub** with three choices: **Built-in trains**, **Addon pack trains**, and **Cancel**. Built-in trains leads to the exact screen you already know.
- **Addon packs are listed by name, with a car count next to each one.** Click a pack to drill into it. Click **Back** to return to the pack list, **Done** to save.
- **Only real train packs show up.** A pack appears in the list only if it actually ships a `mtr:mtr_custom_resources.json` declaring vehicles. Pure texture packs, shader packs, sound packs, and anything else that merely sits in your resource pack folder are never scanned in — the list stays clean.
- **Both pack formats are supported.** Modern packs (`vehicles[]`) and old-style packs (`custom_trains`) are read the same way, so a pack that has not been updated for MTR 4 still lists its trains properly.
- **Trains are grouped two levels deep: line, then model.** Addon packs carry no `family:` tag — almost none of them do — so both levels are derived from the names and IDs the pack declares itself. The Shanghai pack's 248 cars open as **18 lines**; a line with several models (Line 16 has seven) opens into those models, and a model opens into its individual cars. Tick a line to block the whole line at once, tick a model to block just that model.
- **Nested rows drop the repeated line prefix**, so a model nested under `Shanghai Metro Line 16` shows as `16A01(AC19)` rather than repeating the whole line name on every row.
- **Names come from the pack manifest, not from the running game.** That means a train shows its real name even *after* you have blocked it — the row does not degrade into a raw ID like `mtr_custom_train_ac01_cab_1` the next time you open the screen.
- **Counters tell you where you stand.** A pack's page header reads *Blocking 12 / 109 trains in this pack*, and the pack list header shows how many packs and how many trains the scan found in total.

### Behaviour you should know about

- **Addon trains are never touched unless you explicitly tick them.** By default every addon pack car is allowed through, identical to 1.2.0.
- **Built-in keep rules always win.** If a built-in train is on your keep list, no addon setting can remove it. The two lists do not fight.
- **Saving goes through the same restart dialog** as the built-in picker: restart now, restart later, or never ask again. Nothing takes effect until the game restarts.
- **Trains only, never boats or aircraft.** The picker counts and filters vehicles whose transport mode is `TRAIN`; pack-declared boats, airships, and cable cars are left alone.

### Fixed

- **The main menu button showed raw translation keys on Forge** (`gui.mtr_no_original_trains.tab_builtin` and friends) instead of text, because Forge keeps its own copy of the language files and the new keys had only been added to the Fabric side. Both sides are now generated from the same source.

### Note on file names

Jars keep the loader in the file name — `mtr_no_original_trains-fabric-1.2.1.jar` and `mtr_no_original_trains-forge-1.2.1.jar`. Delete the old jar before installing; both builds share the same mod ID and will conflict if you keep two.

### Requirements

- Minecraft 1.20.1
- Fabric Loader 0.14.21+ / Forge 47.x (pick the matching jar)
- Minecraft Transit Railway **4.0.0 or newer** (any 4.x)
- **FerriteCore** and **ModernFix** (both are hard requirements since 1.1.0)

Install one jar only.
