# Hub — Phase 1 design spec (Pantry + Today)

Companion to `docs/design-prompt.md`. The mockups live as artboards on a Design canvas rather than
`design/option-*.html` — agreed with the owner before drawing. The canvas carries what the brief
asked the HTML board to carry: every screen as a phone frame, laid out side by side, light and
dark, with annotations pointing at the interesting parts.

Three options were explored first. **Option B was chosen**, with the pantry taking Option A's card
design and a second gesture. §7 records the decisions made along the way, including five that
override `HANDOVER.md` and `docs/plan.md`.

---

## 1. Foundations

**Colour.** Fixed Material 3 scheme seeded from deep teal (`primary` `#0C6B66` light, `#81D5CF`
dark). Dynamic colour stays a setting, as `HANDOVER.md` §4 requires. Teal was chosen because the
urgency ramp owns red, deep orange and amber — a brand accent anywhere in that arc makes "expired"
read as decoration rather than alarm.

The scheme's tertiary is a muted plum (`#6A4E71` light, `#D8BCE0` dark), for exactly one job: the
throw-away gesture. It is clearly a third thing next to the ramp and next to teal, and it survives
greyscale, where the bin icon and the word carry the meaning instead.

**Urgency ramp.** Four buckets as one `CompositionLocal` alongside the M3 scheme. Every chip carries
an icon *and* a text label alongside the colour — drawn that way everywhere, not described. Contrast
on both grounds is at or above 6:1.

| Bucket | Light fg / bg | Dark fg / bg | Icon |
|---|---|---|---|
| expired | `#8E1710` / `#FFDAD5` | `#FFB4AB` / `#4E110C` | circle-cross |
| critical | `#8A3A00` / `#FFDCC2` | `#FFB77C` / `#4A2400` | triangle-alert |
| soon | `#66500A` / `#FBE8A6` | `#EACD6C` / `#3C3000` | clock |
| ok | `#3E4948` / `#E3EBEA` | `#BEC9C7` / `#2E3635` | check |

`ok` is neutral grey rather than green: green would compete with teal and would imply "good" where
the honest meaning is "nothing to say".

**Type.** Gabarito for display and all numerals, Figtree for body and labels. Every days-left and
count is set in tabular figures — in production `FontFeatureSettings("tnum")`. Proportional digits
visibly jitter through `animateItem()` reordering, which is the app's most-seen animation.

**Shape.** One step rounder than stock M3 — 4 / 8 / 16 / 20 / 28 / full.

**Icons.** Inline stroke SVG in the mockups; production uses Material Symbols with the variable
weight and fill axes, so selection state animates fill rather than swapping assets.

---

## 2. The navigation shell — "the ghosted slot"

`NavigationBar` has no disabled-item state, so one is invented. Order is fixed as the brief
specifies: Pantry · Deadlines · Today · Money · Backlog, Today centred.

A ghosted slot drops to 38% emphasis, thins its icon stroke from 1.9 to 1.3, and carries a dotted
underline where a live destination would grow a solid active indicator. Three signals, and the
dotted rule is the one that survives greyscale and low vision — the treatment never depends on
colour alone.

Tapping produces no ripple, no selection, no navigation. The indicator pill draws itself as a dashed
outline for 200 ms while a snackbar rises over 400 ms and holds four seconds. Ghosted slots stay in
the TalkBack focus order and announce "dimmed, not available yet". The same treatment covers the six
reserved entries in the FAB menu — one rule, two places.

---

## 3. The design

**Today shows one insight at a time.** A large card with its actions on it and a counter. Clearing
is a visible event and the next card rises. A passive feed gets read and forgotten; a queue gets
emptied. The cost is breadth, and it makes the cleared state a real design problem rather than an
afterthought, which is why it is drawn.

Two rules are live, and they get two card treatments. An expiry card takes its urgency band's
colour; a run-out card takes the brand teal. Running out of eggs is not an emergency, and colouring
it like one would devalue the red on the card above it.

**Pantry is location tabs over swipeable cards.** Tabs switch between fridge, freezer and pantry,
each with a count of cards. Rows are cards: name, an optional `×n` badge, a meta line of brand ·
description · location, and an urgency chip. Two gestures resolve an item without opening it —
swipe right marks it consumed, swipe left marks it binned. Both arm at 40% and commit on release,
with a haptic and a four-second undo, and neither gets a confirmation dialog precisely because undo
exists.

A swipe on a counted card does not remove it. The badge counts down and the card rewrites itself to
the next date while the row stays put; only the last one collapses. That difference has to be
legible or the gesture feels broken, which is why the badge sits beside the name where the eye
already is.

**Capture is a full-screen sequence that branches at step 1.** A new product auto-advances through
the lookup. A product already in the pantry stops and waits for a tap — that tap adds another and
finishes there. A miss or a timeout moves on to the naming step by itself.

### Taps from FAB to saved item

| Path | Taps | Notes |
|---|---|---|
| Restocking something you already have | 3 | FAB, "Scan pantry item", tap the existing card. No date step. |
| New product, lookup hit, learned date | 4 | FAB, action, confirm name, save. Lookup auto-advances. |
| New product, lookup miss | 4 + typing | Same count; the name step exists either way. |
| First scan of an unknown product | 4–5 | One extra tap to pick the date on the calendar, or type it in the field. |

The three-tap restock path is the one worth protecting in build. It is the most common scan in a
real week and it is the shortest route in the app.

---

## 4. Component inventory

| Component | States drawn |
|---|---|
| Ghosted navigation bar | resting, ghosted, ghosted-pressed, selected (light + dark) |
| Urgency chip | expired, critical, soon, ok, no-date (light + dark) |
| FAB | resting, expanded-to-menu, close |
| FAB menu entry | live, ghosted |
| Hero insight card | expiry variant, run-out variant, with peek of the next |
| Progress counter and dots | first position, mid position |
| Location tab bar with counts | selected, unselected, zero-count (light + dark) |
| Filter chip row | selected, unselected, sort control |
| Pantry card | resting, counted (`×2`), swiped right, swiped left, hand-entered marker |
| Count badge | on a card, on a detail header, on a found-it card |
| Snackbar | message only, message with Undo |
| Stepper header | steps 1, 2, 3 |
| Product card | skeleton (loading), populated, existing-item (tappable, with +1) |
| Product photo placeholder | 46 / 58 / 62 / 70 px |
| Text field | empty with placeholder, filled, focused with caret, with helper line |
| Date field | empty with placeholder, pre-filled from learning, focused |
| Quick-pick date chip | resting, emphasised |
| Learned-shelf-life callout | informational, correction acknowledged |
| Info row | label + value |
| Entry row (detail) | "goes first" marked, plain |
| Destructive dialog | restore confirmation |
| Empty state | four distinct ones — see §5 |

---

## 5. States covered

| Screen | Empty | Loading | Populated | Error / edge |
|---|---|---|---|---|
| Navigation shell | — | — | drawn | ghosted tap drawn |
| Today | first launch drawn | — | expiry and run-out cards drawn (light + dark) | queue-cleared drawn |
| FAB menu | — | — | drawn | ghosted entries drawn |
| Pantry | first launch and empty location drawn | — | drawn (light + dark), including a counted card | filter-matches-nothing drawn |
| Pantry gestures | — | — | swipe right and swipe left drawn, both with undo | — |
| Capture 1 identify | — | lookup in flight drawn | new product drawn, already-in-pantry drawn | miss path annotated, escape hatch drawn |
| Capture 2 name | — | — | name, brand, description drawn | — |
| Capture 3 date | no learned date drawn | — | learned date drawn | correction acknowledged drawn |
| Item detail | — | — | drawn, two entries under one card | — |
| Settings | — | — | drawn | restore confirmation drawn |

Four empty states, four different jobs, deliberately not one shared layout. Filter-matches-nothing
keeps the tabs and chips in place and reports the good news ("nothing here is expired") with one way
out. Empty location says what belongs on that shelf. First-launch pantry shows all three shelves and
teaches the scan loop. First-launch Today draws the card stack as three dashed slots and explains
the 08:00 nudge that will make it fill.

Loading states are mostly absent by design: database reads are local and return `Flow`, so lists
paint immediately. The one real latency is the Open Food Facts lookup, and it is drawn — two-second
timeout, skeleton of the card that is coming, and a path onward the user never experiences as a
failure.

A backup that fails mid-write no longer needs a screen of its own. The new file is staged beside the
old one and swapped in by a rename, so a write that dies halfway leaves the previous backup intact
and the snackbar carries the ordinary failure message. The one case that still speaks for itself is
a rename the provider refuses: the message names `hub.db.tmp`, because renaming it by hand is the
recovery. See `plan.md` §5.

Still missing, and worth drawing before build: a backup file from a newer schema than the installed
APK (`plan.md` §5 says catch it and show a plain message; that message has no design yet).

---

## 6. Motion

Material 3 Expressive motion schemes and the standard easing set; nothing hand-rolled.

| Trigger | What animates | What the user learns |
|---|---|---|
| Card cleared | Card scales to 0.92 and exits top over 200 ms (emphasized accelerate); next rises 0.94 to 1.0 over 400 ms (emphasized decelerate); counter ticks | One thing is done, one fewer remains |
| Last card cleared | Final exit hands over to the cleared board; check scales in from 0.8 on the same spring | The queue is finished, not broken |
| FAB tapped | FAB morphs into the labelled column, entries staggered ~25 ms bottom to top, 400 ms emphasized decelerate; icon cross-fades to close | The menu came out of the button |
| Swipe right past 40%, release | Teal panel commits, haptic CONFIRM, undo snackbar 4 s | Eaten, and reversible |
| Swipe left past 40%, release | Plum panel commits, haptic CONFIRM, undo snackbar 4 s | Binned, and reversible |
| Either swipe on a `×n` card | Badge counts down and the date rewrites in place, 300 ms standard; the row does not collapse | One box gone, another still here |
| Either swipe on the last one | Row collapses over 200 ms | That product is out of the pantry |
| Swipe released below 40% | Row snaps back on the spring, no haptic | The gesture exists but did not fire |
| Tab switched | Content slides on the shared x-axis, 300 ms standard; 3 dp indicator stretches then settles | A sibling view, same list |
| Filter chip tapped | Non-matching rows fade and collapse over 200 ms; survivors re-rank on `animateItem()` | Filtered, not replaced — you keep your place |
| Sort toggled | Rows translate to new slots over 500 ms emphasized, no fade | Same items, new order |
| Scan success / failure | Haptic CONFIRM / REJECT, no visual required | Read one-handed in a shop, looking at the shelf |
| Lookup returns a new product | Skeleton cross-fades to the product card, auto-advance after 600 ms | Found, and no tap spent confirming |
| Lookup returns something you have | Skeleton cross-fades to the existing card and stops | This needs your decision, not your confirmation |
| Existing card tapped | Card lifts, +1 fills, sheet dismisses over 200 ms, snackbar with Undo | Added, done, no further steps |
| Lookup misses or times out | Moves to step 2 on the shared x-axis | A question, not an error |
| Date corrected | Learned badge is replaced in place by the acknowledgement, 200 ms cross-fade | Noted, nothing to dismiss |
| List to detail | Shared element on name and urgency chip, 500 ms emphasized | This detail is that row |
| Predictive back | Scrubs the same shared-element transition | Where back goes, before committing |
| Ghosted slot tapped | Dashed indicator 200 ms, snackbar 400 ms in, 4 s hold | The slot is real and empty, not broken |

---

## 7. Owner decisions

**The five decisions in this section were made by the owner and are final.** They are recorded here
because they contradict `HANDOVER.md` §7 and `docs/plan.md` §2, §4 and §8, and the next person to
pick this up should treat the documents as superseded on these points rather than as a conflict to
resolve. The design has been drawn to match them.

### 7.1 No quantity tracking

`PantryItem.quantity: Double` and the `pcs | g | ml` unit enum are both dropped. In their place:

- `description: String?` — free text, typed off the pack. "500 ml", "24 cope", "1 kg".
- `brand: String?` — free text on the item itself, pre-filled from the product cache on a lookup hit.

Hub never parses, converts or sums either field. They are labels for a human, not data.

This closes `HANDOVER.md` §11's unit question and reverses `plan.md` §2's fixed `PantryUnit` enum.
Locations stay a fixed enum of `fridge | freezer | pantry`; only units are affected.

The reasoning, in the owner's words: the point is knowing whether there are eggs in the house, not
how many. Tracking amounts means maintaining amounts, and an app that needs maintaining stops being
used.

### 7.2 Count by scan

Scanning a barcode that is already in the pantry shows the existing card and waits. Tapping it adds
another and the card reads `×2`. The count is how many boxes, packets or jars you have — never how
much is inside them, and never adjusted for partial use. Eating four of twenty-four eggs changes
nothing in the app.

### 7.3 Each scan keeps its own date

`×2` is two entries behind one card. The card carries the soonest date; consuming resolves that
entry and the card drops to `×1` showing the later date.

For implementation: there is no count column. The count is derived, and the pantry list groups by
`barcode` over rows where `consumedAt IS NULL`, selecting `MIN(expiresOn)` and `COUNT(*)`. Rows with
a null barcode — loose, hand-entered things — never group, since two hand-typed "tomatoes" are not
reliably the same product. Consuming or binning resolves the row with the earliest `expiresOn`
within the group, which is also the correct real-world behaviour.

The alternative, one row with one date, was rejected in both directions: keeping the earlier date
makes the app nag about food already eaten, and keeping the later one lets the older box go off
unmentioned, which is the exact failure the app exists to prevent.

### 7.4 Low stock is replaced by "run out"

The threshold rule cannot survive the loss of quantities. Today's second live rule becomes: when the
last entry for a product is resolved, it appears on Today as "No eggs left" and holds for a few days
as a restock hint. "Got it" dismisses it for that product; "Snooze" puts it down until tomorrow.
Dismissal is a timestamp, a snooze is a date.

**A snooze is written down, not remembered.** `snoozedUntil` is an epoch day on the product for a
run-out card and on the row for an expiry card, so a card put aside survives a restart and the daily
nudge stays quiet about it too. Nothing clears a snooze; the date simply arrives. Snoozing resolves
nothing — the item is still in the pantry and the product is still run out.

**Snoozed is out of the way, not gone.** Today carries a `N snoozed · Show` row that puts them back
for the rest of the day, one tap, no undoing of anything. It sits below the card stack and stays
there when the queue empties: snoozing the last card is exactly when a way back is needed, and a
drag gesture on a stack that no longer has a card in it has nothing to offer.

No thresholds exist anywhere in the app. This closes the open item in `plan.md` §8 by removing the
question rather than answering it.

### 7.5 Disposition column

Swipe left needs somewhere to land. Keep the soft delete and add `disposition` (`CONSUMED` |
`DISCARDED`) beside `consumedAt`. One column, no additional UI in phase 1, and a later "you binned
four things this month" insight needs no migration. The reason to take it now is that waste data
cannot be reconstructed after the fact — every item resolved before the column exists is permanently
ambiguous.

### Schema delta, collected

| Table | Change |
|---|---|
| `PantryItem` | remove `quantity`, remove `unit` |
| `PantryItem` | add `description: String?`, `brand: String?`, `disposition: Disposition?` |
| `Product` | add `defaultDescription: String?`, learned the same way as `defaultShelfLifeDays` |
| indices | `PantryItem(barcode)` already planned, and now load-bearing for the grouped list query |

`PantryUnit` is never created. `plan.md` §4's note about naming it to avoid shadowing `kotlin.Unit`
is moot.

### Navigation order, settled

The brief's order stands: **Pantry · Deadlines · Today · Money · Backlog**, Today centred at the
thumb's home position and the start destination. `HANDOVER.md` §10 lists Today first; that is a
documentation artefact, not a second decision, and `plan.md` §6 has been corrected to match.

---

## 8. Sample data

One believable household, the same rows on every board.

| Item | Brand | Description | Location | Expiry |
|---|---|---|---|---|
| Baby spinach | — | 200 g | fridge | expired 4 days (hand-entered) |
| Greek yogurt | Rugove | 400 g | fridge | expired 2 days |
| Milk | Vita | 1 L | fridge | 1 day |
| Tomatoes | — | 500 g | fridge | 3 days (hand-entered) |
| Eggs `×2` | Rugove | 24 cope | fridge | 5 days and 12 days |
| Sourdough loaf | — | 1 pcs | pantry | today |
| Ajvar, mild | — | 330 g | pantry | 8 months |
| Rice, long grain | — | 1 kg | pantry | no date |

Every size is free text, exactly as it would be typed off the pack. The freezer is empty, and frozen
peas are what the capture flow is scanning — the scan is what fills it.
