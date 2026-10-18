# Changelog

Notable changes to Alchemizer, newest first. This project does not yet follow a numbered release
scheme; entries are grouped by the milestone that produced them.

## Unreleased — the fuel moves into the pressure chamber

The Ignition Chamber's change of heart: its GUI is gone and so are its features. It stays a
required multiblock part and it keeps its glow, but the fuel, the fire and the fuel slot now live
in the Pressure Chamber, whose screen grows one slot in the lower left corner.

### Changed

- **The ignition chamber is a block and nothing more.** No fuel slot, no burn state, no ticker, no
  screen — a right-click on it does nothing. It remains a required part of the still, and breaking
  it still pauses a brew the way breaking any part does. Its lit state and its light emission stay,
  and the pressure chamber's block entity now writes them: the firebox glows while the chamber
  above burns, and a broken chamber puts its firebox out on the way out of the world.
- **The pressure chamber holds the fuel and burns it.** A fuel slot sits at (7,109) — the lower
  left corner, under the enhancer slots — and the chamber lights fuel only while a brew wants the
  fire, burns it to exhaustion once lit, and pauses the brew while the fire is out, exactly as the
  firebox did. The fuel stack and the three burn numbers are saved with the chamber, so a fire
  survives an unload the way a brew does. Shift-clicking routes fuels to the fuel slot and never
  into an ingredient well, even when the fuel slot is full.
- **The flame moved to the brewing screen.** A vanilla-furnace-style flame drawn in the fuel slot's
  well shrinks as the fuel burns down; the old fuel screen's burn bar and countdown are gone with
  the screen.
- **Fuel in a pre-change ignition chamber is lost on upgrade.** The block entity is gone, so the
  world loads without complaint and the fuel is simply not there any more; a loaded chamber's fuel
  is untouched.

## Unreleased — the second play test: sleeping, closing, serving, and a customer that is not Steve

Everything here came out of somebody playing the shop rather than reading it. A night slept through
shut every shop on the server instead of the sleeper's own. The fourth customer in a queue stood on
the opposite face of the Counter from the other three. Closing a shop left its customers standing
outside it for ten seconds. There was no way at all to deal with a customer — the queue filled up
and that was that. And the customers were drawn as the default player skin, because of a trap in how
the game picks a renderer.

### Added

- **A Serve/Refuse dialogue on any customer.** Right-clicking a customer opens a small window with
  two buttons, whichever place in the queue it is standing in and whether it has arrived or is still
  walking. Serving dismisses the customer and so does refusing: nothing about asks, potions or
  payment exists yet, so the two options differ today only in which was pressed. They are structured
  so that does not stay true by accident — the press is answered by name
  (`CustomerDialogueMenu.BUTTON_SERVE`) and the departure itself is one call, `leave`, that a serve
  will grow into a sale without anything around it moving. Only an owner or co-owner of the
  customer's shop may press either, which is the same `ShopRole` check every other shop action asks,
  and it is asked again at the moment of the press rather than remembered from when the window
  opened.
- **`CustomerDialogueScreen`, the mod's first screen that is not a container screen.** The dialogue's
  menu has no slots, so drawing the player's own wells underneath — most of what
  `AbstractContainerScreen` does — would be a picture of somewhere to put things that does not
  exist. It is a plain `Screen` implementing `MenuAccess`, drawing one panel sprite and two
  `SpriteButton`s: the same widget the brewing screens' controls use, and a face this mod ships. No
  filled rectangles are involved.
- **A panel texture for it**, `textures/gui/customer/dialogue.png`: a placeholder panel of the same
  black frame and grey field every other panel in this mod is drawn in, made to be redrawn.
- **Three lang keys**: `gui.alchemizer.customer.dialogue`, `.serve` and `.refuse`.
- **Five new game tests.** A shop whose owner slept closes and one whose owner stayed up does not;
  every customer in a full queue ends up standing in its own place in the line, whatever side of the
  shop it came at; a line shorter than the config's cap seats nobody off it; a closed shop is empty
  of customers within a handful of ticks; and a stakeholder's serve dismisses a customer while a
  stranger's does nothing. Two more drive the right-click itself: a stakeholder's opens the dialogue,
  and one with no shop behind it is refused.

### Fixed

- **A customer that came at the shop from the side or the back used to stop on the wrong face.** The
  shop's navigation field was built from the Counter's own cell, which is a block that nobody may
  stand in. A field with a hole at its centre cannot be walked around: a customer approaching from
  behind descended to whichever cell of the Counter's ring its walk first touched and stopped there,
  because the next step would have taken it into the Counter — a cell at the same distance from the
  shop as its place in the line, and on whatever side it happened to arrive. The fourth customer
  standing opposite the other three was that, and it stood there for good. The field now leads to the
  cell in front of the Counter — the head of the line — so the Counter is an ordinary obstacle the
  field routes around, and a customer's journey is two legs: to the front, and then along the line to
  its own place. A customer that cannot be given a place at all is still refused, and a customer that
  cannot take up the place it was given leaves rather than being left standing somewhere else.
- **Customers no longer shove each other.** `Customer.isPushable` is false, so a customer standing in
  its place cannot be moved by anything that pushes — including another customer walking along the
  line to a place further out, which must pass it. That is also how a customer ended up standing on
  top of the Counter: a step of one block is ordinary in this game, so one pressed against the
  Counter's side climbed it, and a customer on top of the shop is on no cell of the field at all.
  The design's "a player cannot knock a customer out of its slot" is the same rule seen from the
  player's side.
- **A night slept through closes only the shops of the people who slept.** On a server one player
  sleeping passes the night for everybody, so the old rule — every customer in the level goes — shut
  the street of any shop whose owner was standing in it. The sleepers are worked out from the level
  at the moment the night is skipped and each one's own shops are closed; a shop belonging to
  somebody who stayed up keeps its customers and its trade.
- **Closing a shop empties the street on the spot.** The only thing that noticed a closed shop was
  the arrival clock, which asks its gates once every ten seconds — so a shop its owner had just shut
  went on standing with customers outside it for up to a whole interval. Whether the shop is trading
  is now asked every tick (one lookup in the shop's own ledger), and the customers go on the tick it
  is closed. The gate that walks the server's player list stays on the interval, where it belongs:
  somebody signing off is an event, and `StakeholderLogout` already answers it at once.
- **The customers render with this mod's texture instead of the default player skin.**
  `EntityRenderDispatcher` chooses a renderer for a frame from the render state, not only from the
  entity type: any state that is an `AvatarRenderState` is handed to the player renderer, whatever
  produced it and whatever was registered for that entity. Borrowing `PlayerModel` — which is written
  against the avatar state — therefore drew every customer as a player, from the default skin, and
  `CustomerRenderer.getTextureLocation` was never called at all. The renderer now uses an ordinary
  `HumanoidRenderState` and a `CustomerModel` whose mesh is still the game's own slim player mesh, so
  the dispatcher looks the renderer up by entity type like any other mob's and this mod's texture is
  what is drawn. The overlay parts — jacket, sleeves, pants, hat — are drawn without being switched
  on, which is exactly what a customer's clothing is.

### Notes

- **Serving and refusing still do the same thing, deliberately.** The dialogue exists so that a queue
  can be exercised at all; giving Serve its meaning means doing something before the dismissal, and
  nothing about the shape of the code has to move for that.
- **A customer joins the line at its head and walks out along it to its place.** That means walking
  past the customers already standing between the front of the shop and wherever its place is, which
  is why they had to stop being pushable — the alternative is a line that shoves itself apart every
  time somebody arrives. Joining at the back would be tidier to watch and is the obvious thing to try
  if the walk-past is ever objectionable.
- **The dialogue's panel and the two options have been seen by nobody.** The game tests can drive the
  menu, the ledger and the click, and they prove a window is opened and handed over, but a dedicated
  server has no screen: what the panel looks like needs a client and eyes.
- **What a customer's shop is not known from is its own memory.** The Counter is found by the
  position the customer remembers walking to, so a customer whose Counter has been broken, or whose
  chunk is not loaded, has no shop to ask about and its dialogue is refused. That is the honest
  answer rather than a guess.

## Unreleased — the Counter's inventory label, and the row it was drawn through

The four container panels place their player inventory on one rule, written down once in
`PanelLabels`: the label's line eleven pixels above the first row of wells, which is a panel's own
bottom edge less 93 and less 82. The Counter was the exception. Its menu put the inventory at 150
against a panel whose label line falls at 147, so the label's ink ran through the first row of wells
— an eight-pixel disagreement nothing could catch, because it lived in two classes that had agreed
in a comment and nowhere else.

### Fixed

- **The Counter's inventory sits at `panelHeight - 82`, like the other three.** `CounterMenu
  .INVENTORY_Y` was a bare 150 where the brewing menus use 158 for the same 240-tall panel. It is
  that subtraction now, and the panel's height moved in beside it: the screen used to keep a second
  copy of the 240, which is exactly how the two came apart. The screen hands `CounterMenu
  .PANEL_HEIGHT` to its parent and to `PanelLabels` rather than naming a height of its own, so the
  panel, the label's line and the inventory's line are three readings of one number instead of three
  numbers that happen to agree.
- **The Counter's panel art moved with its slots**, as every other panel's did when its inventory
  moved. The four rows of player wells now start at 157 and at 215, under slots at 158 and 216, and
  the inventory half of the Counter's panel sits on the same rows the brewing screen's does. The
  separator at 145 and the rest of the frame are untouched.

### Notes

- **The other way round was not open.** Leaving `INVENTORY_Y` at 150 and giving the Counter a label
  line of its own at `150 - 11` would have put the label on 139, which is inside the third candidate
  row — drawn from 130, with its own ink from 132. That is a worse overlap than the one being
  fixed, so the screen that departs from `PanelLabels` would have been departing from it into the
  list. There is room to move the inventory down eight: the last candidate row ends at 142 and the
  well under the first slot now starts at 157, with the label on 147 between them.

## Unreleased — a Counter with a front, and a line that moves up

The first playthrough turned up three complaints about the same object. A shop was trading from the
moment its block landed, before its owner had said anything. The queue was drawn by walking the
field's own gradient outward, and over symmetric ground a gradient has no reason to prefer one way
round, so players saw customers queuing in several directions at once. And a customer that had taken
a place in that line kept it for ever: nothing ever moved it, so the line never shuffled forward.
This milestone is those three, plus the tests that would have caught each of them.

### Added

- **The Counter has a front, and the queue forms on it.** `CounterBlock` now carries vanilla's
  `BlockStateProperties.HORIZONTAL_FACING`, set when it is placed from the direction the placer is
  looking — the furnace's and the chest's own convention, so the Counter faces whoever built it.
  It is the one block state a Counter has, and it exists for a reason the others do not have: the
  shop's queue runs out from the face, so the direction has to be somewhere both the block and its
  block entity can read it. `ModModelProvider` emits a variant per facing with the y rotation each
  one needs instead of the single unrotated cube it generated before.
- **`FlowField#laneFacing(dx, dz, maxLength)` — the queue's line, drawn straight out from the face.**
  It takes the direction as a pair of steps rather than a direction, because `core` cannot name one,
  and it stops at the first cell the field does not reach: a queue cannot have a hole in it, and
  carrying on past an unreachable cell would put a place to stand inside a wall with the people
  behind it queuing in the rock.
- **The line moves up.** A seat's number is its distance from the face — seat 0 is the cell in front
  of the Counter and the customer being served, seat 1 the next one out, and so on to the shop's cap
  — and the shop now sweeps its seats once a tick, moving everybody behind an empty one up into it
  and telling them. The customer walks into the cell that was vacated; nobody is teleported.
- **Three game tests and four `core` unit tests.** A Counter comes out facing the player who placed
  it; a Counter facing north queues to the north over a crossroads, where the ground itself offers
  two equally good directions; and a customer behind the head walks into the head's cell when it is
  given up — asserted of the customer's own block position, not of the shop's bookkeeping, because
  bookkeeping that agrees with itself would pass on a customer that never moved.

### Changed

- **A Counter is placed closed**, and opened by its owner from the Counter's own screen. `ShopRecord
  .placedBy` was building a shop with `open = true`, which made a shop trade from the moment the
  block landed — before its owner had said what they are selling or who may sell it, and while they
  were still building the room around it. Addendum A6's Open Shop control is the deliberate act that
  starts trade, and this is what makes it one.
- **A customer arrives by standing in the cell the shop gave it**, rather than by being somewhere as
  close to the shop as that cell is. Arrival used to be a comparison of two costs read out of the
  field, which is an approximation of "has it got there" and can stop a customer a block short; it is
  now one `equals` against the cell the shop named. The guard that keeps a customer from stepping
  past its own seat — and from shouldering into the line it is not standing in — stays.
- **The queue's line is redrawn when the Counter is turned as well as when the field is rebuilt**, so
  a shop that has been rotated queues the way it now faces rather than the way it used to.
- `FlowField#approachLane` and the gradient walk under it are gone rather than kept beside the new
  line: two ways to draw the same queue would be two answers to one question, and the tests that
  pinned the old one now pin the direction they were always meant to.

### Notes

- **The Counter's turn is not visible yet.** The block is still one texture on all six faces, so a
  rotated model looks identical to an unrotated one. The model turns first so that the drawing of a
  front has something to land on; the art is still the placeholder it has been all along.
- **The queue's tests open their shop explicitly**, because a Counter no longer opens itself: a
  closed shop sends its customers away on every arrival interval, so a test about the line has to
  open its shop the way its owner would before it can test anything about a line.
- **A note for whoever runs these tests next:** a customer test can fail because its customer was
  never ticked or was blocked by another customer's body — the plots sit six blocks apart with
  sixteen-block fields, so the shops in one batch can reach into each other's streets. It was seen
  once on `customer_does_not_pathfind`, a test this milestone barely touches, and in the failing run
  the shop's own state (seats, line, occupancy, field) was exactly as designed while the customer's
  entity had a tick count of nought ten ticks after it was added. It is a game test environment
  problem rather than a queue one, and it is not fixed here.

## Unreleased — customers who walk, and the placeholders moved into the config file

Two things the stage before left behind: a customer that was steered once and then stood still, and
a set of numbers the design asks to be configurable that were constants in the code.

### Fixed

- **A customer now actually walks to its place.** The tick only asked the shop's field for a step
  when the customer crossed into a cell it had not been in, and issued the steering order only then.
  `MoveControl` consumes the order it is handed — it turns it into one tick's worth of movement and
  returns to `WAIT`, whose branch zeroes the entity's forward input — so a customer walked for
  exactly one tick, five thousandths of a block, and then stood still inside the cell it was already
  in. Having never left that cell, it was never asked again: no customer ever reached its place in
  the line. The order is repeated on every tick now, from the cell the customer is already walking
  into, which is what a vanilla goal does by asking again each tick it runs. The field itself is
  still read once per cell entered rather than once per tick, so the cost model is unchanged.

### Changed

- **`customer_walks_to_the_counter` asserts arrival rather than drift.** It asked only that the
  distance to the Counter had fallen, which the one-tick bug satisfied: the distance fell by a
  hundredth of a block, the test passed, and the feature it names did not work. It now asks that the
  customer is standing in the cell the shop gave it and has come to a stop there, and it reads that
  cell from the shop's own line rather than writing one down. It fails on the old tick — verified by
  putting the old tick back for one run — and passes with the fix.
- **The shop's numbers are config entries.** `customersPerShop`, `customerArrivalTicks`,
  `customerGraceTicks`, `shopFieldRadius`, `shopFieldYBelow`, `shopFieldYAbove`, `pathWearThreshold`,
  `pathWearEnabled`, `preferredPathCost`, `plainPathCost` and `climbSurcharge` moved out of the
  classes that used them and into the `shop` section of the config file, declared beside the bounds
  they are checked against in `TuningDefaults` and carried to the rules as part of `Tuning`. The
  defaults are the numbers that were there before, so nothing behaves differently until a server
  owner changes one.
- `CustomerQueue` and `CustomerSpawner` take their sizes from `Tuning` when a Counter is created
  rather than from constants, and `ShopPathField` builds its `FieldBounds` per Counter, so the
  invalidation listener searches the radius the field actually reaches.

### Notes

- **The grace period after a lost route is the ten seconds the code had, not the five the stage's
  plan listed.** `Customer` declared 200 ticks and its javadoc said ten seconds, while the plan's
  placeholder table said 100. The shipped behaviour won, because moving a number into the config
  file is not the place to change it; 100 would be the config file's business to set.
- **Two numbers stay where they are, deliberately.** The customer's attributes and the distance it
  has to keep from a player when it appears are placeholders the design has not asked to be
  configurable, so they are still constants.
- **A config change to the shop's numbers applies to the next Counter that is loaded**, not to the
  shops already standing in a loaded chunk: two of them are the size of an array and one is the
  interval of a countdown. The config file says so beside each of them.

## Unreleased — the queue, the path under it, and the way out

The rest of `DESIGN.md` §5.2 and the rest of addendum A6: customers now queue for the shop instead
of standing in its face, the ground they walk on wears into a path, and every way a customer can
leave — the night being slept through, the shop being closed, the route disappearing, the last
owner or co-owner signing off — takes them out of the world the same way, out of the queue, and
leaves nothing behind.

### Added

- **The queue, drawn from the field and owned by the Counter.** `FlowField#approachLane(maxLength)`
  walks outward from the counter taking the cheapest reachable neighbour other than the cell just
  left, which gives the line §5.2 asks for: cells adjacent by construction, one block apart, and the
  same cells every time the same field is asked. `CustomerQueue` holds the shop's seats, the cells
  they are, and who is standing in each; the Counter hands the first free one to each arrival and
  refuses the next one once they are all taken.
- **A customer walks to its place and stops there.** The customer's tick compares what its own cell
  costs to leave against what its place in the line costs, and walks the field's gradient while its
  own is dearer — so it stops in its place rather than at the Counter, and cannot walk past the
  front of the queue or into the Counter itself. Replaces the old `setWait()` at the Counter, which
  was what this stage existed to remove.
- **`PathWear`, and the flattening it feeds.** Repeated walking is counted per cell, per shop, up to
  a threshold, and the pass that reaches it is the one that turns the block into a path — through
  vanilla's own shovel table (grass block, dirt, podzol, coarse dirt, mycelium, rooted dirt), and
  only when the cell above is air, so a player's torch is not destroyed by a block changing shape
  under it. Counting is `core`'s, with unit tests; the block is the platform's.
- **Leaving, for the reasons A6 gives.** `PlayerLoggedOutEvent` clears the street when the last
  owner or co-owner of an open shop signs off, and the shop's own clock does the same for a shop its
  owner has closed. A broken Counter takes its customers with it, because a customer holds the
  field and the queue directly and would otherwise keep walking to a shop that is no longer there.
  Every one of those exits, and the two that were already there, frees the customer's place and
  puffs with no drops.
- **Four game tests**: a shop fills its line and refuses the next customer, the customers in the
  line stand one block apart in the shop's own cells, walking over a cell enough times turns it into
  a dirt path, and a cell with a torch above it is left alone.
- **Five `core` unit tests for the line and six for the wear**, pinning that the line starts beside
  the goal and is one cell at a time, that it stops at its bound and at a wall rather than jumping
  it, that a cell converts on exactly one pass, that counts are per cell, and that a disabled wear
  never converts anything.

### Changed

- The two walking tests now put their customer down **after** the Counter has built its field, and
  every customer test places its Counter the way a player does, through `setPlacedBy`. Both follow
  from the same fact: a customer is given a place in the shop's line when it arrives, and it cannot
  be given one by a shop that has not woken up yet.
- `CustomerSpawner`'s cap is now the queue's: how many people may wait is how many seats the line
  has, so there is one number rather than two that could disagree.

### Notes

- **Placeholders, all of them marked in the code**: four places in a shop's line, and twenty passes
  before a cell becomes a path. Both are config entries now, and the marking is gone; see the
  section above.
- **Path wear is in memory and is deliberately not saved.** A Counter that unloads or is broken
  forgets what its customers walked on, and a world that restarts starts its paths over. Per-cell
  counters in the world's saved data would be a great deal of stored state for a threshold of a
  handful of passes.
- **A shop whose walkable ground offers fewer cells than the cap has fewer places to stand**, not
  four places and an overflow: a seat is a cell of the line, and a line two cells long is a queue of
  two.
- **Found while writing the queue's tests**: a customer's steering moved it a few thousandths of a
  block a tick in a game test world, so crossing a cell took hundreds of ticks and the walking test
  passed on a fraction of a block of drift. Fixed; see the section above.

## Unreleased — customers arrive

`DESIGN.md` §5.2 and addendum A6 ask a shop to draw customers who walk to the Counter along a route
proved to exist. The Counter, its shop and its id were already there; this is the first entity in
the mod, the first in-world model, and the machinery that decides who comes and how they get there.
Nothing yet gives a customer an ask, a queue slot or a payment — that is the next stage — so what
arrives today is a visitor who walks up and waits to be served.

### Added

- **The customer: an entity that is steered and never searches.** A slim player model with its own
  placeholder skin, no goals and no navigation. Every other mob in the game asks a `PathNavigation`
  for an A* route; a customer reads one step out of its shop's field instead, which is what makes a
  street of them cheap. Its whole tick is a three-number comparison — a customer only ever changes
  its mind when it crosses a cell boundary — and `getNavigation().isDone()` stays true for its
  entire life, which is asserted by a game test rather than promised.
- **The shop's navigation field, built a slice at a time.** `ShopPathField` owns one field per
  Counter, advances the build by a fixed number of cells per tick, and serves the last finished
  field while the next one is built, so a wall going up does not freeze everybody on the street. A
  block changing near a shop marks the field dirty through `PathInvalidation`, and a cooldown means
  forty path blocks in a row cost one rebuild rather than forty. This is `StillCache`'s shape, for
  the same reason. It is the third `NeighborNotifyEvent` listener in the mod and, unlike the still's,
  it fires on removal as well as placement — a shop walled in has to be able to recover.
- **`LevelNavView`: what a customer may stand on.** The feet cell and the head cell have to be free
  and the block below has to be ground. Deliberately not vanilla's `isPathfindable`, which refuses a
  dirt path, and deliberately not the three-argument `isFaceSturdy`, which refuses one too because a
  path is fifteen sixteenths of a block — between them they would have excluded exactly the block
  the design asks customers to prefer. Unloaded chunks answer "unknown" rather than "air", and never
  force a chunk to load.
- **`alchemizer:shop_path_preferred`**, a block tag defaulting to `minecraft:dirt_path`. Walking onto
  a block in it costs less than walking onto ordinary ground, so a shopkeeper who lays a path is
  walked along.
- **`CustomerSpawner`: arrivals, four gates deep.** The shop must be open, an owner or co-owner must
  be online (A6), the field must have at least one reachable cell to appear in, and the shop's own
  cap must not be reached. The spawn cells come from the field's own perimeter, so a route to the
  Counter is guaranteed by construction rather than hoped for. The clock is the Counter's own, which
  makes the cadence per shop.
- **Night skip.** Sleeping the night away despawns every customer with a puff and no drops. A
  customer whose route disappears — a broken Counter, a field that no longer reaches it — leaves the
  same way after a grace period long enough to survive a rebuild.
- **Four game tests**: a customer walks toward the Counter, a walking customer holds no vanilla path,
  a dirt path is walkable ground and is cheaper than stone, and a shop walled in on every side has
  nowhere for anybody to appear.

### Notes

- **Placeholders, all of them marked in the code**: movement speed 0.28, health 20, the ten-second
  arrival interval, four customers per shop, the eight-block minimum distance from a player, a
  field radius of sixteen blocks reaching eight up and eight down, and the customer's texture. The
  design settles none of these numbers yet and the skin is drawn to be redrawn.
- A field's spawn cells are its **outer ring**, so a customer appears at the edge of the field and
  walks in. A shop whose walkable ground does not reach sixteen blocks in any direction therefore
  gets nobody — which is the design's own rule, but it means a Counter on a small isolated platform
  has no arrivals at all.
- The renderer and the skin have been compiled and registered but not looked at on a client.

## Unreleased — the Counter's shop screen

`DESIGN.md` §5.1 and addendum A6 give a Counter an owner, co-owners to add and remove, and an Open
Shop control. The block, its shop id and the ledger that holds the rules were already there; this is
the window a player governs them through, and the first thing in the mod that has to send a name
over the network.

### Added

- **The Counter's screen, opened by right-clicking one.** The shop's owner, who else may trade at
  it, who is online to be added, and whether it is trading. The owner alone is shown an add or
  remove button on a row and the co-owner list only they may change; any stakeholder is shown the
  Open/Closed control, because a co-owner may run the shop and closing it is part of running it. The
  panel is filled rectangles rather than art, the same choice the batch control and the recipe book's
  dropdown make, and for the same reason: it is laid out from numbers that only exist at runtime.
- **`alchemizer:counter_view`, the mod's first and only packet.** Every other screen draws numbers,
  which travel in the container synchroniser for free; a list of player names does not. It is sent to
  one player — two people can have one Counter open — and named by the block it is about, so a client
  showing a different Counter ignores it. There is no serverbound half: a press is still a button id
  the server resolves against its own list.
- **The Counter's menu is opened with its position.** Vanilla's open-screen packet carries no
  position and the names a screen draws are looked up by one, so the type is registered through
  NeoForge's `IMenuTypeExtension` and opened with `openMenu(provider, pos)`, which sends the richer
  packet and hands the position to the client's menu factory.
- **Add and remove are indices into the server's own lists.** The owner alone may press them, and the
  index is rebuilt server-side when the button is pressed rather than trusted or remembered: a
  candidate list ordered by name so that the index the screen was sent is the index that still means
  the same person, and an index past the end of the list as it now stands refused outright. What a
  client can say is a position in a list and nothing else.
- **Three new game tests** covering the owner adding a candidate by index, a stranger's add being
  refused with the ledger unchanged, a stale or out-of-range index being refused, and the toggle
  obeying the shop — open for a stakeholder, refused for a stranger.

## Unreleased — still tiers 2 to 4, condensers, and the Funnel's recipe book

`DESIGN.md` §3.1 describes four tiers, and §3.4 and §3.6 describe the batch crafting and the recipe
book that go with them. Only tier 1 was built. This is the rest of it: eight new blocks, a validator
that counts them, a batch multiplier driven by condensers, and a book a player opens once a Funnel
is on top of the chamber.

### Added

**The ladder**

- **Still tiers 2 to 4.** Cumulative, and read off the blocks around the chamber: tier 2 wants a
  valve on one of the ignition chamber's side faces and a bellows on one of that valve's four side
  faces, tier 3 a funnel on top of the pressure chamber and a flue stack on the top face of a valve,
  tier 4 a resonance crystal touching any part. A part built in the right shape but in the wrong
  place is not counted and is not a fault — a valve put down four blocks away must not break a still
  that is working.
- **Eight blocks**, all plain and none with a block entity, because every number they contribute is
  a count the validator makes: the Air Pressure Valve, the Bellows, the Funnel, the Flue Stack, the
  Resonance Crystal, the Condenser, the Advanced Condenser and Alchemical Tubing. Breaking any of
  them tells the chamber, so losing a valve loses the brew the way losing a tank already did.
  (Since changed: breaking a part now pauses the brew, and only the chamber's own break ends it —
  see the machine failure rules entry.)
- **Alchemical Tubing** carries a tank's or a condenser's connection to the chamber, up to
  `maxTubingLength` blocks along the path. A run longer than that is a fault of its own — "Tubing
  run too long" — rather than the far tank quietly reading as "No tank attached", which would send a
  player looking for a tank that is right there. Tubing ships as a **full cube**: a connected pipe
  shape is a model and an art job of its own, and it is not done.
- **Two new faults** on the status line, `Maximum 1 resonance crystal allowed.` and `Tubing run too
  long`, both of which are unavoidable rather than a part merely being in the wrong place.

**Batch crafting**

- **Condensers set a batch size.** A basic condenser is worth 2x and an advanced one 4x, summed
  from the condensers actually connected; the control appears at the left end of the controls row
  the moment there is one, and a still with none shows nothing rather than a dead box. The wheel
  sets the size, a click switches it on and off. The cost is `DESIGN.md` §3.4's own arithmetic: N of
  each ingredient, N bottles, N x 100 mB of water, one brew time and one burn.
- **The ceiling is the stack limit, not a number.** A batch past what an ingredient's stack can hold
  is refused by the well refusing to hold it, which is what the design says and what keeps the two
  rules from disagreeing. The control shows the batch the next brew would actually make rather than
  the dial, since the two differ whenever the dial was wound past what the condensers allow — a
  control reading 10 over a still that will hand over 2 is a control that lies.

**The Funnel's recipe book**

- **A book that opens with a Funnel.** A button flush with the panel's upper edge, a dropdown listing
  every recipe a player knows with its tier and its hourglass setting, and a star at the end of each
  row. Starred recipes pin to the top in the order they were starred, the rest follow in the book's
  own order, and the wheel winds the list. The list opens out of the button's corner over a tenth of
  a second, and it is as wide as its longest name rather than a fixed column — clamped to what the
  window has room for, since it hangs off the panel's right edge and a narrow window would otherwise
  put a row's own star off the screen.
- **Loading a recipe places what the player's inventory can supply.** What the machine was holding —
  the wells and the output slot — is returned to the inventory first, so a second load means "this
  recipe instead of that one" rather than "as well as". If it will not all fit, the load is refused
  and both sides are put back exactly as they were, which is the only acceptable way for a
  convenience to fail. The bottle and the enhancer slots are not returned: a recipe does not dictate
  either. N of each ingredient then moves out of the inventory into the first free wells and the
  hourglass is set to the recipe's; whatever the inventory cannot supply is left empty and shown as a
  ghost item in the well it belongs to — the way vanilla draws a slot waiting to be filled — until
  the screen closes or another recipe is loaded. Nothing is spent until the player presses start, and
  a recipe above the still's own tier is refused without moving anything at all. The refusal is a
  survival-mode promise: in creative, vanilla's `Inventory.add` discards what it cannot place
  instead of reporting it, so a creative player with no room loses the well's remainder rather than
  being told no.
- **A client has no recipe data of its own, and this is the one place that is departed from.**
  `ModRegistries` registers the three definition registries with no network codec and says why, and
  `DefinitionLoader` is a *server* reload listener, so the book has to be sent rather than looked up.
  It travels as a player attachment, is rebuilt on every join and every datapack reload, and carries
  only what a client needs to draw a list and place a recipe — including tags already resolved to a
  representative item, so the client never has to know what a tag is. The sync is filtered to the
  holder, so one player's book goes to that player and to nobody watching them.
- **Every intent the book sends is an index**, into the server's own recipe order, which is the order
  the client was sent. The server re-derives the recipe from that index, re-checks the Funnel is
  present, checks the tier clears the recipe's, and re-reads the inventory. No string crosses the
  wire and nothing the client supplies reaches the machine.
- **The favourites are the only thing saved.** The entries are derived from the datapack and are
  rebuilt from whatever pack is loaded at the time, so a copy of them in playerdata would be a second
  answer to a question the datapack already answers — one that would go stale the first time a pack
  was edited. The attachment writes a list of ids and nothing else, and carries across a death or a
  dimension change.
- **Known means every recipe.** Discovery (`DESIGN.md` §4, addendum A8) is not implemented, so the
  book is the whole recipe list for every player; `defaultUnlocked` on a potion is already authored
  and is the flag discovery will read, so narrowing it later is a change to one method.
- **A datapack with more than 64 recipes has the rest dropped**, with a warning in the log naming
  the two numbers. The book is sent on every join and every reload, so its size is bounded on
  purpose; the warning is because a recipe a player cannot see looks exactly like one that does not
  exist, and the only person who can fix it is the one who wrote the pack.
- **Two star sprites ship as placeholders**, drawn to be redrawn like every other texture here. The
  button, the panel and the rows are rectangles and text, so the stars are the only new book art
  there is.

**Fuel**

- **The Enhanced Blaze Rod**, the top rung of the fuel ladder: a blaze rod ringed by four glowstone,
  burning for 4800 ticks. **That burn time is a placeholder**, as is the recipe.
- **Three brew recipes**, one each at tiers 2, 3 and 4, each naming its tier's fuel among its
  ingredients so a player can read what a recipe is about to ask of the fire. They are placeholders
  in exactly the sense the machine recipes are, and the real ones are the design's to write.

**Other**

- **JEI is told to keep the book's column clear, but only while the book is open.** A plugin whose
  whole job is `getGuiExtraAreas`: no recipe category, no catalyst, no transfer handler. The mod's
  recipes are meant to be discovered by brewing them, and a plugin that listed them would be the
  discovery feature undone from the side. The area exists only while the dropdown does, so JEI's
  ingredient list moves aside as the book opens and comes back as it closes rather than holding a
  strip of the player's screen clear for a list that is usually shut. The dependency is
  `compileOnly` and the one class naming a JEI type sits in a package of its own, so the mod runs
  unchanged with JEI absent. EMI is not installed here and was not attempted.

### Fixed

- **The white frame a clicked button kept.** Vanilla hands the widget a click lands on the keyboard
  focus, so that Tab carries on from where the mouse left off, and a focused widget is drawn with
  its highlighted sprite — a button's being the same grey face inside a one-pixel white frame. That
  frame outlives the click and stays on the last button pressed wherever the mouse goes next, which
  on this screen reads as a second highlight competing with the one that marks the hourglass
  actually in force. The brewing screen drops the click's focus as it returns, so the frame is gone
  the moment the mouse moves on. Tab still focuses and still draws it, which is the one time it is
  saying something true.
- **JEI drew ingredient icons across the recipe book's button.** The plugin that keeps JEI off the
  book had been reserving the open dropdown's rectangle only, and deliberately not the button's: a
  column held clear for a twenty-pixel control looked like a strip of the player's screen spent on
  something usually not in the way. That was reasoning about the wrong cost. JEI does not move its
  ingredient list out of an area — it works out its slots, marks the individual ones that overlap,
  and reflows the ingredients into the slots that are left — so excluding the button costs the two
  or three icons that would have been drawn over it. At GUI scale 4 the strip beside the panel is
  narrow enough for the list to reach the button, which is where it showed. Both rectangles are
  reported now, and the button's only while the still has the Funnel that draws it.
- **The hourglass setting in force could not be read off the buttons.** The chosen one was labelled
  in square brackets, which made the selected label the widest of the four in a button twenty pixels
  wide — and a vanilla button does not clip a label too wide for it, it slides the label back and
  forth inside its own bounds. So "15s", "30s" and "45s" had always been shown as moving fragments,
  and the bracketed one worst of all: the more clearly a setting was selected, the less of it could
  be read, and loading a recipe from the book set one without the player ever pressing its button.
  The buttons are now twenty-four wide, which every setting fits in, and the setting in force is
  told apart by colour rather than by extra characters, since a cue made of text is a cue that can be
  scrolled off the thing it marks.
- **A part added to a finished still is noticed.** The gate that decides whether a block change
  could matter to a chamber near it knew only the three structural parts, so a valve, a crystal, a
  condenser or a run of tubing placed away from the chamber and its tanks was never announced to the
  cache at all. Building a still from the ground up worked and building one on afterwards did not:
  one condenser touching the chamber was counted, the row behind it was not, and a resonance crystal
  leaning against a valve was invisible — which is what made tier 4 look like it had no crystal to
  reach it with. The gate now names every part the validator knows.
- **A bare valve no longer shortens a brew.** §3.3's brew time is `base x 0.9^v`, where `v` counts
  the valves that have a bellows on them — and the tier-1 code counted every valve, so a valve put
  on the machine to move it towards tier 2 made every brew faster. The count is now of
  valve-and-bellows pairs, taken from the same validated counts the tiers are read from, which also
  removes the second, looser copy of the rule.

### Changed

- **`machineTierCap` is read at last.** It was declared, validated and shipped in the config file
  and consulted by no rule anywhere. It now clamps the ladder's answer, so a capped machine reads as
  its capped tier in all three places that read a tier — the status line, the fuel gate and the
  brew-time term — rather than validating as tier 4 and refusing tier-3 recipes.
- **The brewing screen is re-laid out, and the enhancer slots are shown.** The five ingredients are a
  shallow zigzag across the top rather than a straight row, three pixels of panel showing between
  neighbours so the stagger reads as a pattern; the bottle sits at the head of a right-hand column
  with the tank gauge beneath it, taller than the one on the tank's own screen because this one is a
  level read at a glance while a brew runs; the result keeps the panel's centre, below the
  ingredients and alone on its row. The controls row is respread: the batch control and the start
  button are now the same twenty-eight wide, facing each other at the two ends, with the four
  hourglasses between them and six pixels of panel between each pair. The title moved into the
  panel's corner and the inventory's label lines up with the inventory's leftmost well rather than
  with the slot inside it.
- **The two enhancer slots are drawn now, and still take nothing.** They were reserved-and-hidden:
  present in the menu at indices nothing could reach, inactive, and parked off the panel so that a
  bug making one active would show as an empty screen. They have real positions down the left-hand
  side instead. Nothing in the mod supplies an enhancer, so the chamber still refuses every stack
  offered to them — the pair a player meets is one that will not take an item rather than one that
  swallows it, and the test asserts the refusal and the visibility together, since either alone
  passes for the wrong reason.
- **The brewing screen's panel is 210 by 240, against 176 by 208.** Vanilla's width was the part
  that had stopped working: nine inventory wells are 162 wide, so at 176 the inventory and the
  machine's own half had to share one grid — which is why the four hourglass buttons abutted the
  batch control on one side and the start button on the other, with nothing to give away anywhere.
  The extra width goes to the margins: the slot group and the gauge sit on the new centre, the bar
  runs the new inner width, and the controls row spreads into twenty-five-pixel gaps. The inventory
  is centred at twenty-five rather than vanilla's eight, since at 210 vanilla's margin would have
  left it against the left edge with a bare band beside it, and the panel's slot wells — which are
  art, not layout — moved with the slots that stand on them. The book's column follows the panel's
  new right edge, and the width it may draw is still whatever the window has room for.
- **240 is the tallest this panel can be and still be guaranteed to fit.** The size is in GUI units,
  so it scales with the player's GUI scale setting exactly as vanilla's own screens do; what a fixed
  height decides is only what happens when the *viewport* is small. Vanilla's automatic scale stops
  stepping up at the point where the window is 320 by 240 GUI units, so a 240-tall panel takes the
  whole of that height at 1280 by 720 — it fits, with nothing to spare — and 320 is the width past
  which nothing would. A manually chosen scale is not bound by that rule and can be smaller, and
  there the panel overflows its window, as every vanilla screen does in the same situation.

## Unreleased — placeholder recipes for the machine

The three machine blocks had no crafting recipe, so a still could only be built in creative. They
have one each now, and every one of them is a placeholder: `DESIGN.md` says nothing about what the
blocks should cost, and a recipe is the one thing in this mod a player is guaranteed to read, so
these are deliberately cheap and shaped after the vanilla block each one is nearest to.

### Added

- **Crafting recipes for the Ignition Chamber, the Pressure Chamber and the Alchemical Tank.** A
  furnace walled in stone bricks; a cauldron in glass and iron; a bucket in a glass vessel with an
  iron band. Each unlocks off the vanilla block it is built around, so a player with a furnace, a
  cauldron or a bucket finds it in the recipe book without having to know it exists.
- **The tank's clearing craft now has an id of its own**, `alchemizer:alchemical_tank_clearing`. A
  recipe's id defaults to its result item's id, so a tank built from materials and a tank built from
  a tank both wanted to be `alchemizer:alchemical_tank`, and the generator throws on the second.
  Nothing about either craft changed — only the second one stopped being anonymous.

## Unreleased — the tank keeps its water, and two screens re-laid out

`DESIGN.md` §3.5 has always said a broken tank keeps its water in the item. It did not: the level
lived in the block entity, and breaking the block threw it away. The tank also had no screen, so the
only way to see what one was holding was to place it. Both are now what the design says, along with
the brewing screen's layout, the wording of its faults, and a water readout that says something.

### Added

- **The Alchemical Tank has a screen of its own.** One gauge, filled to the water level, and the
  player's inventory — the tank holds a level rather than items, so there is nothing else to draw.
  Clicking the gauge with a water container on the cursor pours it in, through the same helper the
  brewing screen's gauge already uses.
- **A tank item says how much water it is carrying**, in millibuckets, on hover. An empty tank says
  nothing: an empty tank is what a crafted tank is, and a line reading zero on every tank a player
  has ever made is one more thing to read past. The figure is the client's own reading — the
  capacity is the client's config value, which can differ from the server's — so the level is
  clamped through the same core type the block entity clamps with, and a client whose config is
  smaller than the server's shows a full tank rather than an impossible "5000/4000".
- **Both gauges read in millibuckets on hover**, in the tank's screen and in the brewing screen's.
  The line was added through the game's item tooltip event rather than by an item subclass: the
  method that would override is deprecated in this version, and overriding a deprecated method is
  itself a warning, which this build refuses.
- **Crafting a tank empties it.** One tank, shapeless, in any grid. A craft's result is built from
  the recipe rather than from what went in, so the tank that comes out carries nothing.

### Changed

- **A broken tank keeps its water.** The block builds its own drop rather than letting the loot table
  build it, because the water is the block entity's plain save data and the loot system cannot copy
  that: the one vanilla function that carries a block entity onto its item copies registered data
  components, and there is no component here to name. The tank's table is therefore empty on purpose
  and the drop comes from the block. Placing the item back restores the level — that half is
  vanilla's own block entity tag handling and needed nothing written for it.
- **The brewing screen is re-laid out.** The five ingredient slots are centred on the panel in one
  row; the bottle slot is centred one row above them, and the output slot centred one row below, so
  the machine fills its half in three rows. Under all of them runs the progress bar, thin and the
  full width of the panel, and under the bar is the status line. Holding those rows makes the panel
  taller than vanilla's — 176 by 208 against 166 — so the player's inventory sits under the machine
  rather than beside it.
- **The brew timer is gone.** It was drawn at the very top, above the title. The four hourglass
  buttons already say how long a brew will take and the bar already says how far it has got, so a
  countdown was a third telling of the same thing; the buttons moved up into the space.
- **The fault wording names the limit rather than the count.** A machine with two ignition chambers
  said "2 ignition chambers"; it now says "Maximum 1 ignition chamber allowed." A player who built it
  wrongly wants to be told what is allowed, not how far past it they went.

### Removed

- **The `x/y doses` caption under the tank gauge.** A dose is a coinage of this mod's config that
  appears nowhere in `DESIGN.md`, and it was not clear what it meant. The gauge and its hover
  reading say the same thing in a unit that does not need explaining.

## Unreleased — machine failure rules

The design's addendum fixes what happens to a brew when the machine stops being a machine. The
tier-1 code disagreed with it in two places, and both are now what the design says: a broken part
pauses the brew instead of ending it, and the brew is saved with the chamber so an unload or a
restart finds it where it was. Only the chamber's own break ends the brew.

### Changed

- **A brew is saved, and resumes.** It is written to the chamber's save data — what it is making,
  its length, and how far it has got — so a chamber that comes back from disk carries on where it
  left off. A chunk that unloads and comes back, or a server restart, finds the brew intact with
  its ingredients and water already spent. The slots persist too, because those are the player's.
- **Breaking a part of the still pauses the brew.** The timer freezes where it is and re-forming
  the machine resumes it; the contents stay in the chamber, and only breaking the chamber itself
  ends the brew and drops what was inside. This is not a rule polled every tick — a chamber cannot
  tell a part that was removed from a part whose chunk has unloaded, and guessing would pause brews
  that were never broken. The part's block tells the chamber on the way out instead, and the
  chamber re-validates: breaking a tank the still was not drawing on does not even pause the brew.
- **The status line says how the machine is doing.** It was a flat "Not assembled" for every
  unformed machine. A sound still now reads `Tier N - Working perfectly.` in green, and a broken one
  names the fault in red — "No tank attached", "Chamber not above ignition" — so a player who built
  it wrongly is told which block to fix rather than that something is wrong. The line is about the
  multiblock and nothing else: a still that is sound reads that way whether or not the player has
  stocked it, since the missing ingredients, the short tank and the full output are already said by
  the slots that redden and the gauge that drains. The faults are a typed enum in `core` and their
  wording lives in the language file, where all the other player-visible text is.
- The water figure moved off the status line to make room for the longer status text. It sat in a
  caption under its own gauge at this point; the caption has since gone in favour of a hover reading
  — see the entry above.

### Fixed

- **The validator's explanation was thrown away.** It has always worked out why a machine is not a
  still, and the sentences were built in `core` as English and then read by nobody. They are now
  codes the screen translates.

## Unreleased — after the tier-1 baseline

Six things found by playing the baseline, and what was done about them.

### Fixed

- **A water bucket poured into an Alchemical Tank spilled onto the ground beside it.** The tank had
  no interaction handler at all, so the click reached the bucket's own use and placed a water source.
  Right-clicking a tank with a water container now fills the tank, and an empty bucket does nothing
  rather than being refused.

### Added

- **A water container can be poured into the still from the brewing screen.** Clicking the tank
  gauge with a container on the cursor fills the tanks attached to the still, spending the container
  and leaving the empty one on the cursor. A full cluster refuses rather than taking part of it.
  What counts as a water container is the `c:buckets/water` item tag, so a mod's own water bucket
  works without this mod knowing about it; a vanilla water bottle counts too and is worth one dose.
- **A whole stack of bottles may be loaded.** The bottle slot takes a stack and a brew spends the
  bottles it needs rather than requiring exactly one.
- **The finished potion has an output slot of its own**, instead of landing back in the bottle slot.
  Identical potions stack there, and a full output slot refuses a new brew rather than spending its
  ingredients and water on a potion that has nowhere to go.

### Changed

- **Fuel only lights when a brew wants it.** A loaded coal used to burn down to nothing while the
  player was still assembling the machine. Once alight it still burns to exhaustion, whether or not
  the brew is still running.
- **Potions stack, up to a configurable 64**, instead of vanilla's one. This is a rule about the item
  rather than about this mod's machines, so it applies to every potion a player brews, in a chest as
  much as in the still. `potionStackSize` in the config file sets the limit; the value binds as the
  game loads its item components, so changing it takes effect on the next load.

### Corrected

- The notes below claimed the Alchemical Tank "keeps its contents when broken", and a correction
  that stood here said the opposite. The first claim was the right one and the correction was wrong:
  breaking a tank lost its water because nothing had been written to stop it, not because that was
  ever intended. It keeps it now — see the entry above. The parentheticals that pointed down here
  have been removed. (The Pressure Chamber and the Ignition Chamber are a different case, and the
  correction that first stood here was wrong about them too: both hold items, and a broken one drops
  everything that was in it — ingredients, bottles, output, fuel.)

## Unreleased — tier-1 baseline

The first slice of the still, from an empty scaffold to a working brewing machine. Everything below
is on `minecraft 26.1.2` / `neoforge 26.1.2.116` / Java 25.

### Added

**Machine**

- Ignition Chamber: burns one item of fuel at a time, keeps its remaining burn across a reload, and
  drives a lit block state. Fuel is loaded through its own screen rather than by right-clicking.
- Pressure Chamber: five ordered ingredient slots, one bottle slot, and two reserved enhancer slots
  that are present in the menu at fixed indices but neither shown nor placeable. Owns the brew.
- Alchemical Tank: stores water in millibuckets, and is filled by right-clicking it with a water
  container. A broken tank did not keep its water at this point; it does now.
- Multiblock validation: one ignition chamber, one pressure chamber on top of it, and at least one
  attached tank form a tier-1 still. The result is cached and rechecked when a neighbouring block
  changes.

**Brewing**

- Brew recipes are datapack data (`alchemizer:brew_recipe`): an ordered list of one to five
  ingredients plus an hourglass preset, resolving to a potion.
- Potions are datapack data (`alchemizer:potion`): a named list of effects. An empty list is legal
  and means a drinkable potion that does nothing.
- Effect values are datapack data (`alchemizer:effect_value`) keyed by any mob effect id, vanilla or
  not. An effect with no entry falls back to a configured base value rather than failing, so a
  potion built from a newly added effect still brews.
- Adding a potion or an effect value is a JSON file and no Java change.
- Four hourglass presets — 5, 15, 30 and 45 seconds. Brew time is the preset scaled by
  `0.9^n` for each bellows or valve, floored at a configurable one second.
- A brew freezes rather than resets when the fire goes out, and resumes when it is lit again.
- A layout no recipe describes brews Murky Mess, and a failed brew is paid for at the same moment a
  successful one is.
- Brew output is a real `minecraft:potion` carrying the recipe's effects, so it stays compatible
  with vanilla brewing stands, splash and lingering forms, and tipped arrows.

**Screens**

- A fuel screen for the Ignition Chamber: one slot, a flame that empties downwards, and a burn bar.
- A brewing screen for the Pressure Chamber: five ingredient wells, a bottle well, a tank gauge, a
  brew progress bar, four hourglass buttons, a start button, a status line, and a red highlight over
  the slots a refused brew is waiting on. The gauge read in doses and was captioned as such; it reads
  in millibuckets since — see the entry at the top.
- Both screens are views of the machine: every figure they draw arrives from the server over the
  container synchroniser, and a button press sends a button id and nothing else. A start intent is
  re-derived server-side from the machine's own state.

**Foundations**

- Three modules: `core` (pure Java, no Minecraft on the classpath), `mod-common` (the adapter seam,
  with a test that fails the build on a client import), and `platform-26` (the mod).
- A quality gate: Spotless with google-java-format, and `-Xlint:all -Werror`. The build is
  warning-clean.
- Unit tests for the whole of `core`, and in-game tests for the still, the brew loop, the three
  menus, and a tank keeping its water across a break.
- A config file covering tank capacity, the dose, the potion stack size, the still scan radius, the
  hourglass base times, the bellows factor, the minimum brew time, the value factors and the
  fuel-tier gate.

### Not in this baseline

Enhancement and the enhancer slots, per-tier potency and duration, customers, the shop, reputation,
barter, recipe discovery, the Almanac, labels, decor, workshops, and splash and lingering potions.
Each is designed to land as an addition rather than a rewrite: the enhancer slot indices and
`defaultUnlocked` on potions were already in place, and the tiers, the blocks and the recipe book
that were on this list have since shipped — see the entry at the top.

### Known gaps

- The art is placeholder. Every texture ships as a correctly sized stand-in drawn to be redrawn.
- The three machine recipes are placeholders awaiting sign-off, and there are two brew recipes
  rather than the three an earlier draft of this file claimed.
- Murky Mess does not yet grant the experience `DESIGN.md` describes.
- The fuel tags, the item model definitions, the block models, the loot tables and the recipes are
  generated into `platform-26/src/generated/resources`. That tree is not committed — `./gradlew
  runData` rebuilds it — with one exception: the language files are, because the text a player reads
  is worth reviewing in a diff.
