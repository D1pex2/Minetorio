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
