# Phase 1 — the pantry, and everything under it

Phase 1 is the app shell, the pantry, the capture flow, the Today dashboard and the backup and
restore path. It is written. This document is the record of what was decided while building it and
why, so that a decision does not have to be re-argued from the code six months from now.

The blow-by-blow — the plan as it stood before the work started, and the task list it was built
from — is not kept in the repository. It lived at `docs/plan-phase-1.md` and `docs/tasks-phase-1.md`
and it is in git history under those names. `git log --follow docs/plan-phase-1.md` is the way back
to it. What survives here is the part that still binds.

Rules that are still live — the stack, the schema conventions, the backup and restore step order,
the build order for later phases — stayed in [`plan.md`](plan.md), because they govern phase 2 as
much as they governed this one.

---

## 1. The questions the handover left open

These closed §11 of [`HANDOVER.md`](HANDOVER.md) and have not moved since.

| Question | Decision |
|---|---|
| minSdk | **30** (Android 11). Guarantees SQLite ≥ 3.27 for `VACUUM INTO` with no bundled SQLite, and keeps the app installable for anyone who finds the repo. |
| compileSdk / targetSdk | **36** (Android 16 — the owner's device). |
| App name / package | **Hub** / `com.edi.hub`. |
| Currency | **Single currency**, an app-wide setting. The `currency` column stays in the schema so multi-currency is a later migration rather than a rewrite, but no picker is ever shown. |
| Pantry units | **Dropped entirely.** No `quantity`, no unit enum. A free-text `description` typed off the pack ("500 ml", "24 cope", "1 kg") replaces both, and Hub never parses, converts or sums it. See `design/spec.md` §7.1. |
| Pantry counts | **Derived, never stored.** Scanning a barcode already in the pantry adds a second row; the list groups them into one `×2` card. No count column. See `plan.md` §4. |
| Pantry locations | **Fixed enum**: `fridge \| freezer \| pantry`. |
| Vault biometric unlock | **Deferred to phase 3+.** Master password only when the vault ships. |
| Backup cadence | **Manual button only** for now. A scheduled export can reuse the same code path later. |
| "Consumed" semantics | **Soft delete** — set `consumedAt`, never delete the row. Consumption history is what makes restock and spending insights possible. A `disposition` column (`CONSUMED` \| `DISCARDED`) records which of the two swipes resolved it; `consumedAt` means *resolved at*, whichever the disposition. |
| Dependency injection | **Hilt.** |
| Module structure | **Single `:app` module**, packaged by feature. |

---

## 2. What phase 1 was built in

The order mattered more than the contents. Backup and restore came third, before there was anything
worth losing, because a round trip proven on an empty database is a round trip proven cheaply.

1. Compose shell, Material 3, `NavigationBar`, five destinations — **Pantry · Deadlines · Today ·
   Money · Backlog** — three of them ghosted rather than stubbed. The vault lives inside Deadlines,
   never as its own tab. Today sits in the centre, at the thumb's home position, and is the start
   destination.
2. Room with the Gradle plugin, `exportSchema = true`, entities `Product` + `PantryItem` + `Trip`.
3. SAF folder picker, `VACUUM INTO` backup, and restore — **round-trip proven before anything else
   was built on top of it**.
4. Barcode scan via `GmsBarcodeScanning`.
5. The capture sequence and its three-way branch. The branch worth protecting is the one where the
   barcode is already on a shelf: it stops, waits for a tap, and that tap finishes the job. Three
   taps from the button, no date step. It is the most common scan in a real week.
6. Learned `defaultShelfLifeDays` and `defaultDescription`, written back only when the user corrects
   one. Accepting a pre-filled value teaches nothing, which is what stops a wrong guess from being
   confirmed into a fact.
7. The pantry list, both swipes, the four-second undo, and the counted card that decrements in place.
8. The FAB menu, one live action and six ghosted, sharing the composable the ghosted tabs use.
9. Today, wired to exactly two rules.
10. The daily nudge.

Then: install it and live on it for two weeks before writing a second tab. That is still the next
step and it is still the largest risk to the project — five half-finished tabs instead of one that
gets used.

---

## 3. Decisions taken during the build

Things that were open when the plan was written and are not open now.

**Quantities, units and thresholds are gone.** Not simplified — removed. `design/spec.md` §7 owns
this and outranks everything else in the repository on it. The knock-on effects reach further than
they look: no `quantity` column means counts are derived by grouping, which means "two boxes" is two
honest rows with two honest dates rather than one row with a number on it, which in turn means a
swipe resolves the box that goes off first. And no thresholds means "low stock" cannot exist, so the
second Today rule became "you have run out" instead.

**One date per scan.** The date step asks for one expiry and writes it to one row. A second box is a
second scan.

**Expiry dates are `LocalDate` stored as epoch days, never `Instant`.** An expiry is a calendar date.
Storing it as an instant makes "expires in 2 days" shift across midnight, a timezone change or a DST
boundary, and the bug that produces is the kind nobody reproduces on demand.

**The learned shelf life is explained on the item detail screen.** It is the only thing in the app
that changes behaviour behind the user's back, so it is the one mechanism worth spelling out.

**Hub learns the shelf too.** `Product.defaultLocation` is written the same way as the shelf life —
only a correction teaches it — and a later scan of that barcode opens on that shelf. This was the
last open schema question before version 1 would have been frozen; it went in as part of the version
2 migration instead.

**"Not now" became "Snooze", and it is written down.** A snoozed card sets `snoozedUntil`, an epoch
day: on the product for a run-out card, on the row for an expiry card. It survives a restart, and
the daily nudge stays quiet about it too, which an in-memory hide could not manage. Nothing clears a
snooze — the date simply arrives. Snoozing resolves nothing; the item is still in the pantry and the
product is still run out, which is the whole difference between Snooze and the button beside it.

Snoozed is out of the way rather than gone, so the board carries a `N snoozed · Show` row. That row
sits outside the card branch on purpose: snoozing the last card empties the queue, and that is
exactly the moment a way back is needed. A drag gesture on the card stack was the alternative and
cannot work there, because by then there is no card left to drag.

**A failed backup can no longer destroy the last good one.** Opening the backup file in `"wt"`
truncates it to zero before the first byte of the replacement arrives, so a dead battery or a full
disk during the copy left nothing at all. The new file is staged as `hub.db.tmp` and swapped in by a
rename, and whether the provider can rename is checked before anything is written rather than after.
`plan.md` §5 carries the step order.

**Two notifications a day, at 08:00 and 18:00, behind one switch.** They are separated by horizon
rather than by wording: the morning one is the week ahead plus what you have run out of, which is
what you act on before a shop; the evening one is only what goes off today or tomorrow, which is what
you act on before dinner. The same sentence ten hours apart is how a notification gets muted. Both
stay silent on a day with nothing to say.

**Notifications ship off.** Switching them on is what asks for `POST_NOTIFICATIONS`, and a permission
prompt the user has no reason for yet is a prompt they decline. The cost is real and known: someone
who never opens settings never gets the nudge the dashboard depends on.

---

## 4. What phase 1 did not settle

- The run-out hold window is seven days and has never met real use.
- A backup file from a newer schema than the installed APK is caught and refused, but the message
  has no design. `design/spec.md` §5 says so.
- Whether either notification time should be configurable. That is a question for after two weeks of
  use, not before.
- The morning summary's eventual copy — something closer to "your pantry and wallet need checking"
  than to a list of items. It cannot be written honestly until Deadlines and Money exist.

---

## 5. Migrations

Version 1 shipped. Version 2 added three nullable columns — `product.defaultLocation`,
`product.snoozedUntil`, `pantry_item.snoozedUntil` — through Room's auto-migration, derived from the
exported schemas rather than from SQL written by hand. `MigrationTest` is the proof that rows written
under version 1 survive it.

`fallbackToDestructiveMigration()` is never shipped. Both schemas are committed under `app/schemas/`.
