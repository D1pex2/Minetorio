# Minetorio world, character and interface implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans. Continue from the verified stages 2–3 checkpoint; do not reimplement completed oil/blueprint work.

**Goal:** Complete the approved Factorio world and character design, with a consistent Factorio-style interface, real machine slots and an engineer appearance.

**Architecture:** Reuse existing item/fluid/power/research/network systems and the saved unfinished world-generation implementation. Work directly in the current checkout with exclusive file ownership. Root owns shared hooks, translations, integration, final Gradle execution and cross-system review.

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1.264; existing procedural pixel/model pipeline.

**Spec:** `docs/superpowers/specs/2026-10-28-minetorio-factorio-world-design.md`; user clarification 2026-10-29: Factorio 1.1 mechanics/recipes/research; comprehensive visual improvement, real machine windows instead of white action-bar text, Factorio engineer skin. Existing 2.0-style inventory arrangement and fluid transport were explicitly approved adaptations.

## Global constraints and ownership

- The author owns manual playtesting. Do not send input to, stop, restart or modify the running game. Verify source, tests, generated renders and isolated builds.
- Baseline checkpoint is the committed stages 2–3 implementation: 532 unit tests, 296 GameTests, successful isolated build. Preserve the unfinished `.claude/worktrees/agent-afbf093a2278a7ab9` copy.
- No concurrent shared-file edits. Root alone runs Gradle; use the isolated `.superpowers/final-build` and project cache while the author's client is open.
- Exact Factorio 1.1 progression remains authoritative; no temporary substitutes for recipes/research. No Space Age or enemies in this work.
- Original assets only in distribution. Real Factorio reference images remain in ignored scratch. All text has English/Russian translations.

## Work packages

### 1. Nauvis world (world agent)

- [x] Salvage and review the existing world-generator work rather than replace it.
- [x] Complete default Minetorio preset, thin world, gentle terrain, grass/dirt/desert/shallow/deep water, no caves/underground ores/vanilla structures.
- [x] Complete Factorio trees, rocks, cliffs, rare fissures; compatible ore/oil generation and safe start area.
- [x] Complete crash site and starter supply; multiplayer and dedicated-server preset behavior.
- [x] Own `content/world`, `client/world`, `ModWorld`, new world mixins, worldgen resources and world art. Shared hooks/langs/resource migration belong to root.

### 2. Character inventory and hotbar (character agent)

- [x] Implement a real persistent/synchronized 80-slot inventory and tool/trash slots; preserve item conservation, crafting, death, pickup and automation.
- [x] Replace E/R with the approved left inventory / right crafting arrangement, functional logistics and statistics tabs.
- [x] Implement configurable ten-slot shortcut rows, remembered depleted items and X row switching; shortcuts must not be duplicate storage.
- [x] Keep vanilla crafting progression out of normal play. Validate all server requests and multiplayer state.
- [x] Own `content/character`, `core/character`, `client/character`, character inventory mixins and corresponding tests; coordinate exact inventory API with root/machine UI agent before integration.

### 3. Machine interface system (machine UI agent)

- [x] Build reusable dark Factorio-style panels, inset slots, yellow accents, compact labels, progress/energy/fluid indicators and readable tooltips.
- [x] Restyle existing furnace, assembler, oil, roboport and inserter windows without changing their mechanics.
- [x] Add real windows for drills, labs, boiler/engine/pumps/tanks, radar, solar/accumulator and poles where action-bar text currently substitutes for an interface.
- [x] Slot windows expose the actual underlying inventory, not an independent copy; recipe changes remain server checked and remote mode remains read only except settings.
- [x] Own `client/gui`, existing machine screens/menus plus new `content/machineui`; root owns central hooks and avoids these files. Character UI consumes the shared theme API.

### 4. Integration, resources and appearance (root / next available agent)

- [x] Wire new registries/mixins/client hooks and both languages; migrate basic resources to mod items throughout recipes, ores, fuel, demo and tests.
- [x] Finish unbreakable tool tiers/research and starter equipment, own chests, Factorio stack sizes and death inventory integration.
- [x] Add original engineer skin/model, supplied by mod for all players, plus author music sound slots with vanilla music disabled.
- [x] Add real production/consumption statistics behind the statistics tab and the approved HUD (research, craft queue, actionable machine alerts).
- [x] Rebuild the demo on the new terrain without touching the running author's copy.

### 5. Review and deliver

- [x] Test item conservation across menus/hotbar/crafting/pickup/death/save/load, server validation, world generation and progression.
- [x] Run isolated full unit suite, build and dedicated GameTests; verify original generated assets and offline UI previews.
- [x] Independent review against the full approved design; fix important findings with regression coverage.
- [x] Deliver a build and clear author playtest checklist. Do not claim manual visual verification.

## Decisions

- Reuse the approved design instead of requesting another design interview; the user's new instructions explicitly authorize completing this work.
- Mechanics remain 1.1; the approved ten-column inventory arrangement is a UI adaptation, not a migration to Factorio 2.0 rules.
- Continue on the current checkout with exclusive ownership instead of creating many worktrees and later resolving overlapping merges.


## Delivery evidence

- `world-interface-delivery.log`: 561 unit tests, 327 required server GameTests, full build successful.
- Independent static review: corrected drag budget counting only 36 slots and extra-slot pickup stopping at99; 200-cable pickup regression passes.
- `nauvis-demo-build.log`: real dedicated Nauvis server created spawn/crash/start resources, built showcase, ran600ticks, saved and exported successfully. Final demo refresh tracked in `nauvis-demo-delivery.log`.
- Original engineer UV preview inspected offline; art/check_zfight.py passes world/machine composites.
- Manual client visual/gameplay review intentionally left to the author. Existing running client and player saves unchanged.
- Research decision: exact Factorio1.1 steel_axe upgrade replaces the older design's invented tool-tier research.
