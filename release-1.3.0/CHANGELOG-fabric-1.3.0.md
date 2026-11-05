## 1.3.0 — search, conflict warnings, vanilla checkboxes

A quality-of-life release for the in-game picker. The selection screens did their job but were getting hard to live in: the Shanghai pack alone is 248 cars across 18 lines, and scrolling that by eye was the only way to find anything. This version adds search to every picker screen, warns you when a tick will not do what it looks like it will do, and redraws the checkboxes the way vanilla does.

Nothing about the saving rules changed. Everything you configured in 1.2.1 behaves exactly the same after updating.

### New: search on every screen

- **The Built-in Trains picker, the Addon Trains pack list and each pack's detail page** all get a search box with a **Search** button on its right. Type, press Search (or Enter), and the list narrows.
- **Typing alone does not re-filter** the list, so nothing jumps around while you type. Escape clears the search first and leaves the screen on the second press.
- **Pack-list search covers every train.** Searching from the pack list does not just match pack names — it searches all trains of all packs at once and lists the matching models grouped under each pack header, with the hit count on the pack row. Click a pack to fine-tune inside it, with the search carried over.
- **Space means AND.** `上海 16` matches trains whose name or ID contains both terms.
- **Numbers stay whole.** Searching `2` does not match `12号线`; searching `16` does not match the digits inside `AC16`. Number-boundary rules keep line numbers from bleeding into model numbers — this took the matcher from 90.9% precision to 100% on a 357-car benchmark of two real metro packs, with recall also at 100%.
- **Prefixes ignored.** `mtr_custom_train_` and `mtr:` are stripped before matching, so pasting a raw ID from the log still finds the train.
- **Best matches first.** Results rank by relevance: exact name, then starts-with, then contains, then ID-only. Inside a pack, matched models sort to the top of their line and the strongest lines sort to the top of the page.

### New: conflict warnings

- A **"!" mark** appears on any car that is also on your built-in keep list — keeping wins there, so blocking it has no effect, and the tooltip explains that.
- A pack or model that **shares cars with another addon pack** shows a conflict count, because blocking a shared car removes it from both packs. The tooltip says so.

### New: vanilla-style checkboxes

Checkboxes now draw the same diagonal check as vanilla Minecraft: solid when everything under the row is selected, translucent when only part of it is.

### Renamed, and localized in 42 languages

- The main menu button and the panel title now read **"No Original Trains Panel"**.
- Every string in all four picker screens is translated in **42 languages** — the picker follows whatever language your game runs in.

### Fixed

- The "Restart the game to apply." hint no longer overlaps the bottom edge of the list on short screens.

### Coming from 1.2.0? (Modrinth skipped 1.2.1)

Modrinth went straight from 1.2.0 to 1.3.0, so this version also includes everything 1.2.1 added on CurseForge:

- **Addon pack trains can be filtered too.** The main menu button opens a hub — **Built-in Trains**, **Addon Trains**, **Cancel**. Addon Trains lists every pack that ships an `mtr:mtr_custom_resources.json` declaring vehicles (texture, shader and sound packs never show up), reads both MTR 3 (`custom_trains`) and MTR 4 (`vehicles[]`) formats, and groups trains three levels deep: **line → model → individual car**. The Shanghai pack's 248 cars open as 18 lines.
- **Tick a line** to block it in one click, drill down to block a single model or a single car. Select All / Select None work on the current screen.
- **Real names come from the pack file**, so a blocked car keeps its name instead of degrading to a raw ID.
- **Nothing is blocked unless you tick it**, and built-in keep rules always win over addon blocking.

### Behaviour

- No config migration, no changed defaults. `keepIds`, `extraRemoveIds` and `blockedAddonIds` all keep working, from the picker or from the config file.
- Saving still goes through the same restart dialog: restart now, restart later, or don't ask again. Nothing takes effect until the game restarts.

### Requirements

- Minecraft 1.20.1
- Fabric Loader 0.14.21+ / Forge 47.x (pick the matching jar)
- Minecraft Transit Railway **4.0.0 or newer** (any 4.x; beta-8 to beta-11 not supported)
- **FerriteCore** and **ModernFix** (hard requirements since 1.1.0)

Install one jar only — `mtr_no_original_trains-fabric-1.3.0.jar` on Fabric, `mtr_no_original_trains-forge-1.3.0.jar` on Forge. Delete the old jar first; both builds share the same mod ID and will conflict if you keep two.
