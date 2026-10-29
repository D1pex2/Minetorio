# Minetorio implementation log

## 2026-10-29 — stages 2 and 3

- Continued from the existing NeoForge 21.1.264 / Minecraft 1.21.1 / Java 21 source project. Pure core and Minecraft wrappers are retained; no loader installation or external game profile changes.
- Recovered integration at `90d09c8` before this implementation. Existing working copies, including unfinished world generation, are preserved.
- Author confirmed exact Factorio 1.1 mechanics, recipes and research. The short-lived 2.0 request was withdrawn; a later Space Age effort will handle 2.0. The already approved fluid transport design remains the 2.0 segment model.
- Stage 2 fixes: blueprint drag, selection height overflow, belt replacement direction, construction support and oil remote menus. Expanded research UI supports horizontal navigation and explicit locked future prerequisites.
- Stage 3: oil fields and extraction, mixed item/fluid recipes, refinery/chemistry, steel smelting/chest, blue science, corrected late-science gates and temporary recipes, fluid assembler 2, barrels, field map ledger, original procedural art and working demo chain.
- Sources: pinned `wube/factorio-data` tag `1.1.110`; Factorio Wiki entity images are reference-only under ignored `.superpowers/reference/factorio`.
- Verification runs are serialized by root after overlapping agent runs were detected. The final evidence must come from fresh serialized runs, not earlier agent summaries.
- Independent review found support-removal construction and unsupported assembler downgrade. Regression tests reproduced both before fixes. Shared fake-player creative mode caused an unrelated flaky ore-drop test; fixtures now set survival explicitly.
- Current next steps: finish regression checks, rebuild demo, inspect actual client rendering/UI, then audit every design requirement before marking complete. No push has been performed.

- Verified checkpoint: 532 unit tests, 296 GameTests, isolated build successful after lighting fix. The author owns playtesting; no further computer input or client restart. User then expanded work to completing the Factorio world, character/machine UI and engineer skin.


## 2026-10-29 — Nauvis, engineer and factory interfaces

- Completed approved world/interface block: default Nauvis preset, original terrain/object/fish art, safe starter area and nearby water, finite surface resources, crash-site supplies.
- Persistent 80-slot inventory, locked engineer tool and recoverable logistics trash, remembered ten-column shortcut rows, live craft/logistics/statistics windows and small-window scaling.
- Machine slot windows share original industrial panels; remote view remains read only for inventory transfer.
- Exact Factorio 1.1 materials and saved 100/200-item stacks; ordinary chests 16/32/48, logistics chests48, steel-axe research. Latest exact1.1 instruction supersedes old invented diamond-tool research.
- Saved production/consumption histories use actual crafting, mining, science, fuel and fluid events; added research/queue/nearby-alert HUD.
- Original classic64x64 engineer skin and icons: art/gen_engineer.py. Own OGG tracks can be supplied by resource pack; vanilla music is disabled. No Factorio audio/art shipped.
- Fresh dedicated-server Nauvis demo export; player saves and the author's running client untouched.
- Independent review fixed 36-slot drag preview counting and pickup stopping at99 in extended slots; regression covers a200-item cable stack.
- Manual visual/gameplay verification remains with the author.

- Delivery verification: 561 unit tests and 327 required GameTests pass; full build and real Nauvis demo export succeed. JAR SHA256: 3A3EA8C15284CB14EF92618C74076ED938BEADCF6B414A89F40279EB02B12C10.
