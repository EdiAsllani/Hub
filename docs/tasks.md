# Hub — Phase 1 task list

> **Status.** Everything from phase 0 to phase 8 is built on `feat/phase-1`, one commit per phase.
> What is left is the part that needs a phone: 9.2 has not been done, so no instrumented test has
> been *run* — all sixteen compile and are written against a real database, but the machine this was
> built on has no device attached. The barcode scanner, the notification and the SAF round trip are
> unproven until someone sideloads it. 9.4 is the owner's, and is the whole point.

Everything needed to take the two phase 1 features — **Pantry** and **Today** — from the current
scaffold to the design drawn in `design/spec.md`. Every artboard on the canvas is accounted for here.

Read `CLAUDE.md` before starting. `design/spec.md` §7 outranks `docs/HANDOVER.md` and `docs/plan.md` wherever
they disagree. The order of the phases is `docs/plan.md` §6 and is not arbitrary: backup and restore is
proven before anything else writes data, and the pantry can be read before it can be changed.

Motion is `design/spec.md` §6 throughout and is not restated per task. Colour, type and the urgency ramp
are §1. Sample data for every screenshot and preview is §8 — the same household on every board.

`Trip` and `TripDao` exist and are untouched this cycle. Nothing here needs them; trip totals are phase 2.

One assumption runs through phase 0: **no device holds v1 data.** The scaffold has never been installed.
If that turns out to be wrong, task 0.1 becomes a hand-written `Migration(1, 2)` with a test, per
`docs/plan.md` §7.

---

## Phase 0 — Foundations

Nothing visual can be built until the schema matches the owner decisions and the theme exists.

| # | Task | Files | Spec |
|---|---|---|---|
| 0.1 | Rewrite the schema to the §7 delta: drop `quantity` and `unit` from `PantryItem`, add `description`, `brand` and `disposition`; add `defaultDescription` and `runOutDismissedAt` to `Product`. Delete `PantryUnit`, add `Disposition`. Keep version 1 and regenerate `app/schemas/…/1.json`. | `data/model/PantryItem.kt`, `data/model/Product.kt`, `data/model/Enums.kt`, `app/schemas/` | §7.1, §7.5 |
| 0.2 | Update `HubDatabaseTest` for the new columns. It asserts the converters and the soft delete; both still apply, the constructor calls change. | `androidTest/…/HubDatabaseTest.kt` | — |
| 0.3 | Fixed Material 3 colour scheme seeded from deep teal, light and dark, with the plum tertiary. `dynamicColor` flips to **default off** and becomes a `SharedPreferences` setting — `docs/HANDOVER.md` §4 requires the fixed scheme shipped and dynamic offered as a setting. | `ui/theme/Theme.kt`, new `ui/theme/Color.kt` | §1 |
| 0.4 | The urgency ramp as a `CompositionLocal` supplied beside `MaterialTheme`: four buckets, each with foreground, background and icon, for both themes. | new `ui/theme/Urgency.kt` | §1 |
| 0.5 | Bundle Gabarito and Figtree in `res/font` and wire a `Typography`. Display and all numerals in Gabarito, body and labels in Figtree. **Verify the Gabarito build exposes `tnum`** before wiring `FontFeatureSettings` — a miss here is a font swap, not a code change. | `res/font/`, new `ui/theme/Type.kt` | §1 |
| 0.6 | Shape scale one step rounder than stock: 4 / 8 / 16 / 20 / 28 / full. | `ui/theme/Shape.kt` | §1 |
| 0.7 | Add the dependencies phase 1 needs and none it does not: OkHttp, Coil 3 with `coil-network-okhttp`, `play-services-code-scanner`, WorkManager, `hilt-work`. | `gradle/libs.versions.toml`, `app/build.gradle.kts` | plan §3 |
| 0.8 | Correct `docs/plan.md`: §6 step 1 lists the destinations in the wrong order, and §8 flags a navigation-order question the code already answers. Strike the open item. | `docs/plan.md` | — |

Artboard: **Foundations**. Done when the app builds, runs on the fixed scheme, and a preview of the four
urgency chips matches the board in both themes.

> `ponytail:` icons stay `Icons.Filled` / `Icons.Outlined` pairs for now rather than the Material Symbols
> variable font. The variable axes buy a fill animation on selection; the cost is bundling and subsetting a
> font. Revisit if the animation is missed.

---

## Phase 1 — The navigation shell

| # | Task | Files | Spec |
|---|---|---|---|
| 1.1 | Replace the current dimmed `NavigationBarItem` with the ghosted-slot composable: 38% emphasis, thinner icon stroke, dotted underline where the active indicator would be, no ripple, no navigation. | `ui/HubApp.kt`, new `ui/components/GhostedNavItem.kt` | §2 |
| 1.2 | The tap response: dashed indicator for 200 ms, snackbar rising over 400 ms and holding four seconds. | same | §2, §6 |
| 1.3 | Ghosted slots stay in the TalkBack focus order and announce "dimmed, not available yet". | same | §2 |

Artboard: **Navigation shell**. Done when a ghosted slot is distinguishable from a live one in greyscale
and with colour inverted, and TalkBack reads the announcement.

---

## Phase 2 — Backup and restore

The highest-risk code in the app, and the reason it comes third: nothing else may write data until the
round trip is proven. The Settings screen ships here because it is where the buttons live.

| # | Task | Files | Spec |
|---|---|---|---|
| 2.1 | SAF folder picker, persisted URI permission, stored in `SharedPreferences`. | new `data/backup/` | plan §5 |
| 2.2 | Backup: delete the temp target, `VACUUM INTO` via `execSQL` outside a transaction, assert non-empty, stream to SAF with `"wt"`, delete the temp, record `lastBackupAt`. Fixed filename `hub.db`, written to the existing document's URI rather than created anew. | same | plan §5 |
| 2.3 | Restore, in the order given: copy to cache, validate the header and `PRAGMA quick_check` before touching anything, cancel and await the WorkManager job, close Room, **rename** the live file rather than deleting it, copy in, delete `-wal` and `-shm`, then the restart dialog. | same | plan §5 |
| 2.4 | Catch a backup from a newer schema than the installed APK and show a plain message. **This message has no design** — draw it or write it plainly and flag it. | same | §5, plan §5 |
| 2.5 | The Settings screen: the backup slice only, plus the dynamic-colour switch from 0.3. The switch is undrawn; a plain M3 `Switch` row is enough. | new `ui/settings/` | §5 |
| 2.6 | The restore confirmation dialog: what is thrown away, what replaces it, which file and from when, and that Hub closes itself. The confirming button says what it does, and the safe choice sits below it. | same | §5 |
| 2.7 | Instrumented round-trip test — insert, back up, wipe, restore, assert the rows. This is the acceptance criterion for the phase. | `androidTest/` | plan §7 |
| 2.8 | Unit tests for the header check and the `quick_check` rollback against a deliberately truncated file. | `test/` | plan §7 |

Artboards: **Settings — backup slice**, **Settings — restore confirmation**.

---

## Phase 3 — Pantry, read only

| # | Task | Files | Spec |
|---|---|---|---|
| 3.1 | The grouped query. Filter to one location and to open rows, **then** group on `COALESCE(barcode, 'id:' \|\| id)`, selecting `MIN(expiresOn)` and `COUNT(*)`. Grouping on the bare barcode column collapses every hand-entered item into one card. | `data/dao/PantryDao.kt` | §7.3, plan §4 |
| 3.2 | `androidTest` for 3.1: two rows sharing a barcode become one card with the earlier date and a count of 2; two null-barcode rows stay two cards; the same barcode in two locations is two cards. | `androidTest/` | plan §7 |
| 3.3 | Location tabs with card counts. Counts are cards, not boxes — eggs `×2` counts once. | `ui/pantry/` | §3 |
| 3.4 | The pantry card: name, optional `×n` badge beside the name, meta line of brand · description · location, urgency chip. The badge sits beside the name because that is where the eye already is. | `ui/pantry/` | §3 |
| 3.5 | The urgency chip: icon **and** text label alongside the colour, five states including no-date. | new `ui/components/UrgencyChip.kt` | §1, §4 |
| 3.6 | Filter chip row and the sort control, with `Modifier.animateItem()` on the list. | `ui/pantry/` | §3 |
| 3.7 | Three empty states, three layouts, deliberately not shared: filter-matches-nothing keeps the tabs and chips and reports the good news; empty location says what belongs on that shelf; first launch shows all three shelves and teaches the scan loop. | `ui/pantry/` | §5 |

Artboards: **Pantry — swipe right, consumed (light)** — no board draws the pantry at rest, so its rows
underneath the gesture are the populated state — plus **Pantry — filter matches nothing**, **Pantry —
first launch**, and **Pantry — Freezer tab, empty location (dark)**. Done when the §8 sample household renders correctly in
both themes.

---

## Phase 4 — Pantry gestures

| # | Task | Files | Spec |
|---|---|---|---|
| 4.1 | `SwipeToDismissBox` in both directions. Right is consumed and teal; left is binned and plum. Both arm at 40% and commit on release, with a haptic. Below 40% the row snaps back with no haptic. | `ui/pantry/` | §3, §6 |
| 4.2 | Resolving writes `consumedAt` **and** `disposition`. Within a group it takes the row with the earliest date: `ORDER BY expiresOn ASC NULLS LAST LIMIT 1`. Plain `ASC` puts a dateless box first and contradicts the card. | `data/dao/PantryDao.kt` | §7.3, §7.5 |
| 4.3 | Four-second undo snackbar on both gestures, nulling both columns on the row that was returned. No confirmation dialog — undo already covers the misfire. | `ui/pantry/` | §3 |
| 4.4 | A swipe on a `×n` card decrements the badge and rewrites the date in place over 300 ms; the row does not collapse. Only the last one collapses. **This is the part to get right** — if it is not legible the gesture feels broken. | `ui/pantry/` | §3, §6 |

Artboards: **Pantry — swipe right, consumed**, **Pantry — swipe left, binned + undo**.

---

## Phase 5 — Capture

| # | Task | Files | Spec |
|---|---|---|---|
| 5.1 | The FAB menu: `FloatingActionButtonMenu`, one live action, six reserved entries reusing the ghosted composable from phase 1. Ordered by frequency so the live action sits nearest the thumb. | `ui/components/` | §2, §3 |
| 5.2 | Barcode scan through `GmsBarcodeScanning`, haptic on success and on failure. | new `ui/capture/` | plan §6 |
| 5.3 | Open Food Facts lookup over OkHttp with a two-second timeout. The `User-Agent` contact string comes from a Gradle property into `BuildConfig` — no personal address in the repository. | new `data/off/` | plan §3 |
| 5.4 | Step 1, the three-way branch. **Already in the pantry:** show the existing card and stop; tapping it adds a row and finishes there, three taps from the FAB with no date step. The new row copies `barcode`, `name`, `brand`, `description` and `location` from the group it joins, and takes `expiresOn` = today + `defaultShelfLifeDays`, or null where nothing has been learned. **This is the path worth protecting.** | `ui/capture/` | §3, plan §6 |
| 5.5 | The other two branches: a hit on a new product auto-advances after 600 ms; a miss or a timeout moves to step 2 by itself, which the user never experiences as a failure. Plus the skeleton card and the "skip, I will name it" escape. | `ui/capture/` | §3, §5 |
| 5.6 | Step 2, the product form: name, brand and description, all free text, with the helper line that teaches what description is for. Without it people type a sentence or expect the app to add things up. | `ui/capture/` | §3, §7.1 |
| 5.7 | Step 3, the date. First scan offers relative quick-picks (+3 d, +1 wk, +1 mo, +6 mo) before the field. A rescan of a product that is **no longer in the pantry** arrives pre-filled from `defaultShelfLifeDays`. The restock path in 5.4 never reaches this step at all. | `ui/capture/` | §3 |
| 5.8 | Learning: a correction writes back `defaultShelfLifeDays` and `defaultDescription` and acknowledges once, inline, in the container colour — no snackbar, no dialog. Only the **date** correction is acknowledged; a changed description writes back silently, since step 2 draws no callout. Accepting a pre-filled value teaches nothing. | `data/`, `ui/capture/` | §3, §7 |
| 5.9 | Each step is its own destination on the shared x-axis, so system back reverses one step and predictive back shows the previous step. | `ui/capture/` | §6 |

Artboards: **FAB menu**, **Capture 1 — looking up**, **Capture 1 — new product**, **Capture 1 — already
in the pantry**, **Capture 2 — name, brand, description**, **Capture 3 — first scan**, **Capture 3 —
learned date**. Done when the three-tap restock path works end to end on a real barcode.

---

## Phase 6 — Item detail

| # | Task | Files | Spec |
|---|---|---|---|
| 6.1 | The detail screen, reached by a shared-element transition on the name and the urgency chip; predictive back scrubs the same transition. `docs/plan.md` §3 warns that threading `AnimatedVisibilityScope` through `NavHost` is the awkward part — budget for it. | new `ui/detail/` | §6, plan §3 |
| 6.2 | Both entries of a `×2` listed under one card, with the earlier one marked "goes first". This is the only screen that explains the count model, and it explains it by showing. | `ui/detail/` | §7.3 |
| 6.3 | Actions matching the two swipes — same colours, same words, and "Used one" rather than "Mark consumed". The gesture and the button must not be two vocabularies for one thing. | `ui/detail/` | §3 |
| 6.4 | The learned shelf life stated in plain language on the item that has one. It is the only thing in the app that changes behaviour behind the user's back, so it is the one mechanism worth explaining. | `ui/detail/` | §3 |

Artboard: **Item detail — two boxes, two dates**.

---

## Phase 7 — Today

| # | Task | Files | Spec |
|---|---|---|---|
| 7.1 | `expiringSoon` over the existing `observeExpiringThrough`. | new `domain/` | plan §3 |
| 7.2 | `ranOut`: a product with zero open rows, a most recent `consumedAt` inside the hold window, and `runOutDismissedAt` null or older than that resolution. **Barcoded items only** — a null-barcode row has no `Product` to hold the dismissal. | `domain/`, `data/dao/` | §7.4, plan §4 |
| 7.3 | Unit tests for both rules over a fake `Queries`, including that `ranOut` stops firing once dismissed and fires again after a re-buy. The join in 7.2 is SQL, not rule logic, so it joins 3.2's `androidTest` rather than being faked. | `test/`, `androidTest/` | plan §7 |
| 7.4 | One card at a time with a counter and a peek of the next. An expiry card takes its urgency band's colour; a run-out card takes the brand teal, because running out of eggs is not an emergency. | `ui/today/` | §3 |
| 7.5 | Clearing as a visible event: the card exits, the next rises, the counter ticks. | `ui/today/` | §6 |
| 7.6 | "Got it" writes `runOutDismissedAt`. "Not now" leaves the card for tomorrow — **assumed to be an in-memory same-day hide** until the owner says otherwise; a `snoozedUntil` column is the alternative and is listed in `docs/plan.md` §8. | `ui/today/`, `data/` | §7.4 |
| 7.7 | The cleared board, whose check scales in on the same spring, with two tallies beneath it so it is not a void. | `ui/today/` | §3, §5 |
| 7.8 | First launch: the card stack drawn as three dashed slots, one primary action, and an honest line about the 08:00 nudge. | `ui/today/` | §5 |

Artboards: **Today — expiry card (light and dark)**, **Today — run-out card**, **Today — first launch**,
**Today — queue cleared**.

---

## Phase 8 — The daily nudge

| # | Task | Files | Spec |
|---|---|---|---|
| 8.1 | `@HiltWorker` daily job at ~08:00 posting a summary notification, with `HiltWorkerFactory` from the `Application` and the default `WorkManagerInitializer` removed from the manifest. | new `work/`, `HubApplication.kt`, `AndroidManifest.xml` | plan §3, §6 |
| 8.2 | `enqueueUniquePeriodicWork(…, KEEP)` on every launch, so a job cancelled during a restore re-establishes itself. | `HubApplication.kt` | plan §6 |
| 8.3 | Request `POST_NOTIFICATIONS` when the notification is first enabled, never at app start. Without it the summary is dropped silently. | `ui/` | plan §3 |

No artboard. The dashboard is passive; without the nudge the app gets forgotten.

---

## Phase 9 — Prove it, then live on it

| # | Task | Spec |
|---|---|---|
| 9.1 | Confirm every test target in `docs/plan.md` §7 has landed: the round trip (2.7), restore hardening (2.8), the grouped query (3.2), and the insight rules (7.3). Migration tests arrive with phase 2 of the product, when the first real migration does. | plan §7 |
| 9.2 | Sideload the release APK. Scan three real items in a shop. Scan one of them twice and confirm the card reads `×2` with the sooner date; swipe it and confirm it drops to `×1` with the later one. Confirm the learned shelf life pre-fills on a rescan. Force-stop, restore from the SAF folder, confirm the daily notification fires. | plan §7 |
| 9.3 | Update `README.md` to what exists rather than what is planned. | — |
| 9.4 | **Then live on it for two weeks before writing a second tab.** The largest risk to this project is five half-finished tabs instead of one that gets used. | plan §6 |

---

## Known gaps, carried forward

- A backup that fails mid-write has no design. The newer-schema message is written plainly in 2.4.
- The dynamic-colour switch is undrawn and ships as a plain switch row.
- `Product.defaultLocation` would remove a tap from every rescan. Still the owner's call; a trivial migration either way.
- "Not now" is assumed to be an in-memory hide. See 7.6.
