# Minetorio stage 4: logistic network — playtest checklist

**Status: not played yet.** Built by an agent without a client window. Checked automatically:

- `./gradlew test` passes (logistics core: networks, delivery priorities, request/trash math, research data).
- `./gradlew runGameTestServer` passes, including `LogisticsGameTest`: requester ← passive provider,
  active provider → (filtered) storage, buffer chests, personal requests and trash, two roboports in one
  network, a player outside the network is not served, the personal roboport builds a ghost.
- Every new model was rendered offline (`art/logistics-preview.png`) and checked for z-fighting.

Tick the boxes as you go; write findings at the end.

## 0. Setup

Survival world, cheats on (creative mode to skip crafting; the research itself still needs labs or the
demo world, `/minetorio demo`, which researches everything). Keys: **K** personal logistics (new), **R** hand crafting, **Y** research.

What Minetorio does like Factorio 1.1:

| | |
|---|---|
| Logistic area | 50×50 around each roboport (construction area stays 54×54). Roboports whose logistic areas touch or overlap form **one network**. |
| Logistic robot | 3 blocks/s, 1 item per trip, same battery (1.5 MJ) and charging as construction robots; lives in the roboport's robot slots. |
| Pickup priority | active provider first, then the nearest storage / passive provider / buffer chest. |
| Drop priority | requester chests and players before buffer chests; active provider contents and player trash go to storage (filtered storage → storage already holding the item → empty storage). |
| Buffer chest | fills its requests from providers/storage (not from other buffers); supplies players and construction robots always, requester chests only with "request from buffer chests". |
| Personal logistics | request slots with min and max; robots bring items up to min, take everything above max to storage — only while you stand inside a network. |
| Personal roboport | an item (no armor in Minetorio): kept in the inventory it makes you a mobile roboport for the construction robots in your inventory; radius 15 (30×30), 10 robots per personal roboport. |

Minetorio adaptations: chests have 27 slots (Factorio 48); trash is taken straight from the inventory
(no trash slots); personal robots need no charging (no armor energy). Recipes and research now follow Factorio 1.1: logistic robotics needs chemical science, the logistic system needs utility science, and personal roboports retain their equipment prerequisites. Later-science production is implemented in its own roadmap stage.

## 1. Research and crafting

- [ ] Research tree shows **Logistic Robotics** (after Robotics), **Logistic System** (after Logistic Robotics) and **Personal Roboport**.
- [ ] Logistic Robotics unlocks logistic robot, passive provider and storage chests; Logistic System unlocks active provider, requester and buffer chests; Personal Roboport unlocks the personal roboport.
- [ ] All seven items have icons and names (English and Russian); chests look like the Factorio chests (coloured top plate with the round porthole, louvred sides).

## 2. Chests and robots

- [ ] Place a roboport, power it, put **logistic robots** in its slots (they share the 7 slots with construction robots).
- [ ] Holding a roboport, logistic robot or logistic chest shows the green-yellow logistic area outline (and the orange construction area for roboport/construction robot).
- [ ] Passive provider with iron plates + requester chest requesting 20 plates (open it, click a request slot with plates, select the slot, type 20, Set): white logistic robots leave the roof iris, fly to the provider, then to the requester, come back and charge.
- [ ] Active provider: everything in it is carried to storage; with a storage chest filtered to an item (click its filter slot with the item) that item goes there.
- [ ] Requester with "Buffers: off" ignores a buffer chest's items; "Buffers: on" takes them.
- [ ] Construction robots still build ghosts from provider/storage/buffer chests, never from a requester chest.
- [ ] Inserters can put into and take out of every logistic chest; comparators read them.
- [ ] Breaking a chest drops it and its contents.

## 3. Roboport GUI

- [ ] Opening the roboport shows a **Logistic network** panel on the right: roboports in the network, logistic and construction robots (available / total), and the items in the network with counts.
- [ ] Two roboports whose areas touch show the same network figures (2 roboports).

## 4. Personal logistics (K)

- [ ] K opens the personal logistics screen: two rows of request slots, Min/Max boxes, Set, and the Requests on/off toggle.
- [ ] Request 50 iron plates (min 50): standing in the network, robots fly to you and bring plates one at a time; outside the network nothing comes.
- [ ] Set gears max 10 while carrying 30: robots take 20 to storage.
- [ ] Requests off: nothing is delivered or taken.
- [ ] Requests survive death and relog.

## 5. Personal roboport

- [ ] With a personal roboport and construction robots in the inventory, ghosts within 15 blocks are built by robots launched from you (yellow construction robots), using items from your inventory; the robots come back into your inventory.
- [ ] At most 10 robots are out per personal roboport.

## Findings

(write here)
