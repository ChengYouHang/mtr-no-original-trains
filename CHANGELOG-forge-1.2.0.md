## 1.2.0 — built-in train picker on the title screen

This release adds the feature most people asked for: **you no longer have to edit a config file to keep specific built-in trains.** A button on the title screen opens a picker where you tick the train families you want to keep, hit Save, and the game restarts to apply it.

Everything that 1.1.0 did still works exactly the same way. This is purely additive.

### New: train picker on the title screen

- **A new button on the main menu**, labelled **"MTR train selection"**. Click it and the mod opens its own picker screen. No config editing, no restarting to find out you typed an ID wrong.
- **Trains are grouped by family** — pick "SP1900" instead of hunting for the individual car IDs. Each family shows how many cars it contains, and the screen keeps a running count (`kept 4 / 32 families`).
- **Select all / select none / reset to default**, plus a search-free collapsible list so the screen stays usable no matter how many families your addon packs declare.
- **Save prompts you to restart.** After saving, a confirmation dialog offers to restart the game right away, restart later, or never show the dialog again. Nothing takes effect until the game restarts, and the dialog says so plainly instead of leaving you guessing.
- **The button places itself.** It anchors directly above the Options/Quit button row, and if there isn't room it pushes the vanilla buttons below it down — or shrinks itself — rather than sitting on top of them or dropping below Quit. This works on Fabric and Forge, at any GUI scale, including the odd Forge layouts where Realms and Mods share a row.

### New: family catalogue at runtime

- **The picker builds its list from the MTR build you actually have installed**, so it always matches your MTR version — including trains added or renamed in a 4.x update.
- **Config keys are unchanged.** `keepIds` and `extraRemoveIds` still work exactly as before, so an existing config keeps behaving the same. The picker simply writes the same keys for you.

### Fixed

- **Keeping several families used to shrink the picker catalogue and leave it inconsistent.** If you kept a large number of families, the picker could end up showing far fewer families than you had selected (the symptom was a count like `selected 32 / 1 families`), and the family mapping stopped resolving for the ones it dropped. The cause was that train definitions were only captured when a train was actually *blocked* — so the more you kept, the less the mod knew about, and the catalogue was rebuilt from an ever-shrinking set. Two changes fix it:
  - Every built-in train is now registered into the catalogue on first sight, whether or not it ends up being blocked.
  - The static map from MTR's shipped manifest and the runtime map are kept separate, and lookup falls back to the static one — so a family always resolves even if the runtime list is incomplete.

  Verified across all five paths: block everything, keep 32 families, keep 2 individual car IDs, extra-remove with a conflict against keep, and fully disabled. The catalogue stays at 32 families / 378 cars in every case.

### Note on file names

The jars are now named with the loader in the file name — `mtr_no_original_trains-fabric-1.2.0.jar` and `mtr_no_original_trains-forge-1.2.0.jar` — because two identically named jars on the desktop is a good way to install the wrong one. If you are updating, delete the old jar first.

### Requirements

- Minecraft 1.20.1
- Fabric Loader 0.14.21+ / Forge 47.x (pick the matching jar)
- Minecraft Transit Railway **4.0.0 or newer** (any 4.x)
- **FerriteCore** and **ModernFix** (both are hard requirements since 1.1.0)

Install one jar only. Both builds share the same mod ID and will conflict if installed together.
