# Task 4 report: belt core

Implemented dev.minetorio.core.belt: BeltTier, LaneItem, LaneSink, BeltLane (incl. asSink), BeltShape, SplitterLogic, UndergroundLink. Tests: BeltLaneTest, BeltMiscTest.

TDD: tests and implementation were written together, so there was no separate RED run. First GREEN run had one real failure (throughputYellowLane, see deviations); final `./gradlew test --offline` -> BUILD SUCCESSFUL (48 tests).

Deviations / concerns:
1. throughputYellowLane: literal "insertAt(item, 0) every tick" yields 64 (insert needs 3 ticks of travel, 0.28 per item => 66/200 ticks), not 75, because insertion at 0 is quantized to whole ticks. The test instead feeds like a saturated upstream (sub-tick entry position credit-SPACING) starting from a steady-state lane; result is within 75+-1. The lane logic itself reaches the 7.5/s rate. Real integration should pass overflow into the next lane (asSink) rather than insert at 0, otherwise a belt from an inserter-style feeder caps at ~6.7/s.
2. blockedLaneCompresses: brief says max 4 items; with SPACING 0.25 and length 1.0 positions 1,.75,.5,.25,0 are all legal (exactly 0.25 apart is allowed per controller), so max is 5. Test asserts 5, spacing >= 0.25, and inserted == on lane.

## Fix round 1
- BeltLane: capacity = floor(length/SPACING + 1e-9) enforced in canInsertAt (4 straight, 2 inner curve); class Javadoc on downstream-first ticking and asSink hand-offs.
- Tests: blockedLaneCompresses restored to max 4; added innerCurveCapacityIsTwo, blockedChainHoldsFourPerLaneWithSpacing (4 lanes, exactly 16 items, world gaps >= SPACING), chainedThroughputYellow (2 lanes via asSink, downstream-first, 75 +- 1). Existing steady-state throughput test kept and still passes.
- `./gradlew test --offline` -> BUILD SUCCESSFUL.
