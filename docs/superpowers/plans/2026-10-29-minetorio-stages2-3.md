# Minetorio stages 2 and 3 completion plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Finish the approved blueprint/building stage and oil production stage, with working progression, art and multiplayer-safe server validation.

**Architecture:** Keep the pure Java core and the existing Minecraft wrappers. Extend the existing fluid network, multiblock, power and copyable-settings interfaces. Work in the current checkout with exclusive file ownership; root owns shared registrations and integration. Serialize Gradle runs.

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1.264, JUnit 5 and NeoForge GameTests.

**Spec:** `docs/superpowers/specs/2026-10-27-minetorio-blueprints-design.md`, `docs/superpowers/specs/2026-10-28-minetorio-oil-design.md`. Later approved world design supersedes vanilla-progression assumptions; completing that separate world/UI project is not a substitute for finishing stages 2–3.

## Global constraints

- Core has no Minecraft/NeoForge imports. Factorio 1.1 normal recipes and rates, converted to 20 ticks/s; fluids follow the approved segment design.
- Both languages, dedicated-server safety, persistence, rotated fluid ports, ghosts and copyable settings are required for new machines.
- Machine art follows real Factorio references kept outside distributable assets; use existing procedural pixel/model pipeline and moving machinery.
- No worktree churn, concurrent edits to the same file, shared Gradle runs, or deletion of previous unfinished work. No push without a separate need.

## Review focus

1. Full outputs, missing power and recipe changes must not lose/duplicate items or fluids.
2. Fluid filters and rotated machine ports must prevent mixing and retain contents across saves/unloads.
3. Research must gate both recipes and blueprint paste; mixed-fluid crafting must not run by hand.
4. Construction/remote actions must be server validated and must not force ticking remote chunks.
5. Render the actual new machine models, check animation, bounding boxes and ghosts; green logic tests alone do not prove art works.

## Task ownership and order

### Task 1 — Stage 2 completeness (`stage2`)

- [x] Audit every design requirement against source/tests.
- [x] Reproduce missing blueprint drag and oversized automatic selection with failing tests.
- [x] Implement in `core/blueprint`, `content/blueprint`, `client/blueprint`; audit remaining camera/planner requirements.
- [x] Run targeted tests, report explicit remaining requirements and integration requests.

### Task 2 — Oil core, data and progression (`oil_core`)

- [x] Add tests for mixed input/output conservation, starvation/backpressure, yield decay and normal recipe values.
- [x] Implement `core/oil` with an item/fluid recipe and processing state; integrate item recipes/tech in `Phase1Data`.
- [x] Replace temporary stage 3 recipes, add steel, chemistry, science, express belts and barrels.
- [x] Verify core tests; expose exact stable interfaces to the machine implementer and root.

### Task 3 — World extraction and machines (`oil_machines`)

- [x] Add oil-source and recipe machine GameTests for generation, placement, rotation, automation and persistence.
- [x] Implement `content/oil`, `client/oil`, own `OilRegistry` with pumpjack, refinery, chemical plant and steel furnace.
- [x] Use the existing network/power/multiblock APIs; expose central hooks and asset IDs to root.
- [x] Verify powered production, all port rotations and breaking machines without loss.

### Task 4 — Integration and assets (root, then available agent)

- [x] Wire OilRegistry/client hooks, mining/creative/ghost interfaces, map oil yields and translations.
- [x] Add fluid crafting to assembler 2 (barrels/electric engines/express belts) and required lab/research/UI support.
- [x] Build procedural models/textures/icons from actual reference images; validate model limits/z-fighting and view renders.
- [x] Keep shared-file edits with root; delegate independent asset files when a slot opens.

### Task 5 — Verification and completion audit

- [x] Run full unit suite, `build`, dedicated GameTests and meaningful end-to-end oil-chain checks.
- [x] Perform independent review of stages 2–3 against both design documents and fix important findings with regression tests.
- [x] Verify offline rendered artifacts and update playtest/roadmap; author explicitly owns manual playtesting. Source-only lighting fix passed six regression tests; no claim of visual confirmation.
- [x] Mark the goal complete only after every requirement has direct evidence.

## Progress and rulings

- Baseline: integration recovered at `90d09c8`; 502 unit tests, 262 GameTests, successful build (2026-10-29).
- Ruling: oil depletion floor follows Factorio 1.1: max(20% absolute, 20% initial yield), resolving the design's shorthand '20%' against its explicit Factorio fidelity requirement. Cost if wrong: rich field endgame yields differ from the shorthand reading.
- Active: stage2 audits/fixes; oil_core designs pure processing and data; oil_machines implements new content. Root handles shared files and integration.

## Verified checkpoint

- 2026-10-29: isolated final build successful; 532 unit tests, 296 required GameTests pass. Independent review fixes have regression coverage; all model/composite z-fight checks pass. Updated demo includes working oil-to-plastic/battery production.
- Running client was not stopped or changed after the author reserved playtesting. New lighting code requires a later restart to take effect.
- The overall chat continues: author requested completing world/biomes, full Factorio character and machine UI, and engineer skin. This is tracked in the next plan; do not repeat these completed stages.
