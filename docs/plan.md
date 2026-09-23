# Hub — Implementation Plan

Companion to `docs/HANDOVER.md`. That document settles *what* the app is and *why*; this one settles
*how* it gets built: stack, schema, storage mechanics, and the order of work. Where the two disagree,
`docs/HANDOVER.md` wins on product decisions and this document wins on implementation detail.

`design/spec.md` is the third document, and it outranks both on the owner decisions recorded in its
§7. Those points are settled, and this plan is written to match them rather than to reconcile them.
The design itself lives as artboards on a Design canvas the owner holds the link to; §1
(foundations), §6 (motion) and §8 (sample data) there are the reference for anything visual, and are
deliberately not restated here.

This document holds what is still binding. A phase that has been built keeps its own record —
[`phase-1.md`](phase-1.md) is the first — and the plan and task list it was built from are in git
history rather than in the repository.

---

## 1. Context

`docs/HANDOVER.md` describes a fully offline Android app — a personal hub for things owned, owed, or
tracked — built on three primitives (due-date thing, item + status + rating, money event) with a
dashboard of insight cards as the product.

Intended outcome: a sideloadable, self-signed APK, open-sourced on GitHub, whose phase 1 (shell +
pantry) is good enough to live on daily for two weeks before a second tab is written.

---

## 2. Decisions already made

The questions §11 of the handover left open were closed during phase 1 — minSdk, currency, units,
counts, locations, backup cadence, soft deletes, DI and module structure. They are recorded in
[`phase-1.md`](phase-1.md) §1 and are not re-opened here.

---

## 3. Stack

Single module, Kotlin, KSP, Gradle version catalog (`gradle/libs.versions.toml`). **Do not pin the patch versions below from this document** — resolve them in Android Studio at setup time and record them in the catalog.

| Concern | Choice | Note |
|---|---|---|
| UI | Jetpack Compose via the **Compose BOM**, December '25 stable line (`2025.12.xx` → Compose 1.10, Material3 1.4.x) | Verify the current BOM at setup. Material 3 **Expressive** APIs may still require `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` in 1.4 — check before designing around them; do not assume they are stable. |
| Navigation | `androidx.navigation:navigation-compose` with type-safe routes (`kotlinx-serialization` `@Serializable` destination objects) | Navigation 3 is not worth the churn for five destinations. |
| DI | **Hilt** + KSP | Plus `androidx.hilt:hilt-work` for the WorkManager job — see §7. |
| Persistence | **Room** + KSP, with the **`androidx.room` Gradle plugin** and `schemaDirectory("$projectDir/schemas")` | `exportSchema = true` alone does nothing without the plugin configured. Schemas are committed. |
| Dates/times | **`java.time`** (`LocalDate`, `Instant`) | Available unconditionally at minSdk 30. No `kotlinx-datetime`, no desugaring. |
| HTTP | **OkHttp** + `kotlinx-serialization-json` | One endpoint. OkHttp is pulled in anyway by Coil's network loader, so a hand-rolled `HttpURLConnection` client would save nothing. |
| Images | **Coil 3** + `coil-network-okhttp` | Only ever loads the Open Food Facts `imageUrl`. Never used for BLOBs. |
| Barcode scanning | `com.google.android.gms:play-services-code-scanner` | `GmsBarcodeScanning.getClient(context).startScan()`. No camera permission, no CameraX. |
| Background work | **WorkManager** | Two summary notifications a day, ~08:00 and ~18:00. |
| Settings (SAF tree URI, last-backup time, dynamic colour, the notification switch) | **`SharedPreferences`** | A handful of values. DataStore is a dependency and a coroutine API for no gain here. |
| Encryption (phase 3+) | `javax.crypto` — `PBKDF2WithHmacSHA256` (~600k iterations) + AES-GCM | No dependency. See §9 of the handover; the scheme there stands unchanged. |

### Architecture

Single `Activity`, Compose-only, no fragments. Per feature: `Dao` → `ViewModel` exposing a `StateFlow<UiState>`, with a repository in between only where there is work that is not a query — backup and restore is one, the Open Food Facts lookup is another. A class that forwards each DAO call unchanged is a layer, not an abstraction. DAO queries return `Flow`, so list animations and the dashboard update themselves on write. Insight rules live in a `domain` package as plain suspend functions over a `Queries` facade — a `List<suspend (Queries) -> List<Insight>>`, exactly as sketched in the handover. Not a rules engine.

### What the design costs the build

- **The urgency ramp is a `CompositionLocal`** supplied next to `MaterialTheme`, not colours picked at each call site. Four buckets, each with a foreground, a background and an icon; `design/spec.md` §1 has the hex values for both themes. Every chip draws icon *and* text label alongside the colour.
- **Fonts are bundled in `res/font`,** not fetched. Gabarito for display and numerals, Figtree for body. The mockups pull them from Google Fonts because they are web pages; the app is offline and must not. Material Symbols ships as a variable font too — subset it, or the APK carries a few MB of unused glyphs. **Verify at setup that the Gabarito build in use actually exposes `tnum`**; the tabular-figure requirement for every count and days-left number depends on it, and a fallback is a font swap, not a code change.
- **The FAB menu is hand-built.** `FloatingActionButtonMenu` is a Material 3 Expressive API and is **not** present in material3 1.4.0, which is the version this project can build against until `compileSdk 37` is published. The menu is a column of entries staggered in behind the FAB, and it gains nothing by waiting for the component.
- **The ghosted navigation slot is hand-built.** `NavigationBar` has no disabled-item state, so the treatment in `design/spec.md` §2 — reduced emphasis, thinner icon stroke, dotted underline, no ripple, a snackbar, and a TalkBack announcement of "dimmed, not available yet" — is a local composable reused by the six reserved FAB entries.

### Platform details that are easy to miss

- **`POST_NOTIFICATIONS`** is a runtime permission at targetSdk 36. The daily summary is silently dropped without it. Request it when the notification feature is first enabled, not at app start.
- **Predictive back** requires `android:enableOnBackInvokedCallback="true"` in the manifest.
- **Shared element transitions** through `NavHost` need each destination's `AnimatedVisibilityScope` threaded into the shared-element modifier. This is the awkward part of `SharedTransitionLayout` with nav-compose; budget time for it rather than being surprised.
- **Hilt + WorkManager** needs `hilt-work`, a `@HiltWorker` constructor-injected worker, `HiltWorkerFactory` supplied from the `Application` via `Configuration.Provider`, and the default `WorkManagerInitializer` removed in the manifest.

### Open-source hygiene (the repo is going public)

- The release keystore and `keystore.properties` are **gitignored from the first commit**. The keystore itself lives outside the repository and is backed up separately — losing it or changing it forces an uninstall/reinstall and destroys the app's data.
- The Open Food Facts `User-Agent` contact string comes from a Gradle property into `BuildConfig`, defaulting to the public repository URL. No personal email is committed.

---

## 4. Data model

The entities from §7 of the handover are correct in shape. What follows adds the parts Room needs and tags each table by the phase that introduces it. **Phase 1 creates only `Product`, `PantryItem`, and `Trip`** — every later table arrives as a migration, which also exercises the migration path early and cheaply.

### Conventions (all tables)

- `@PrimaryKey(autoGenerate = true) val id: Long = 0`, with two deliberate exceptions: `Product` is keyed by `barcode`, and `DeadlinePhoto` is keyed by `deadlineId` (one photo row per deadline, no surrogate id).
- **`LocalDate` ↔ epoch-day `Long`** and **`Instant` ↔ epoch-milli `Long`** via `@TypeConverters`. Due dates are calendar dates and must never be stored as `Instant` — "expires in 2 days" flipping across midnight or a DST boundary breaks the app's headline feature.
- **Money is `Long` in minor units.** Never `Double`.
- Enums stored by `name` (Room's default), so adding a variant is not a migration.

### Indices

`PantryItem(expiresOn)`, `PantryItem(barcode)`, `PantryItem(consumedAt)`, `PantryItem(tripId)`, `Deadline(dueOn)`, `Deadline(kind)`, `MoneyEvent(occurredAt)`, `MoneyEvent(tripId)`, `PriceObservation(barcode)`.

Every one of these backs either a dashboard rule or a list filter. Room additionally requires an index on any foreign-key column. `PantryItem(barcode)` is no longer merely useful: it backs the grouped pantry query below, which runs on every frame of the list.

### Foreign keys

- `PantryItem.tripId → Trip.id`, `ON DELETE SET NULL`.
- `MoneyEvent.tripId → Trip.id`, `ON DELETE SET NULL`.
- `DeadlinePhoto.deadlineId → Deadline.id`, `ON DELETE CASCADE`.

A database-level constraint is cheaper and more reliable than the equivalent application code.

### Tables by phase

| Phase | Tables |
|---|---|
| 1 | `Product`, `PantryItem`, `Trip` |
| 2 | `Deadline` |
| 3 | `MoneyEvent` |
| 4 | `BacklogItem` |
| 5 | `Secret`, `DeadlinePhoto` |
| 6 | `PriceObservation` |

Field definitions come from §7 of the handover, with the following delta, which is not optional — the handover's `PantryItem` predates the owner decisions in `design/spec.md` §7.

| Table | Change |
|---|---|
| `PantryItem` | remove `quantity`, remove `unit` |
| `PantryItem` | add `description: String?` — free text off the pack, never parsed |
| `PantryItem` | add `brand: String?` — free text on the item, pre-filled from `Product` on a lookup hit |
| `PantryItem` | add `disposition: Disposition?` — `CONSUMED` \| `DISCARDED`, set beside `consumedAt` |
| `PantryItem` | `location` becomes **non-null** `PantryLocation` |
| `Product` | add `defaultDescription: String?`, learned exactly as `defaultShelfLifeDays` is |
| `Product` | add `runOutDismissedAt: Instant?` — backs the run-out rule's "Got it", see below |
| `Deadline` | add `snoozedUntil: LocalDate?` — Today snooze persists across restarts and notifications |

Four clarifications:

- `PantryItem.location` becomes an enum **and loses its nullability**. The pantry is location tabs now, so an item with no location has nowhere to appear; the capture flow always sets one. Name it **`PantryLocation`** — a bare `Location` invites confusion with `android.location.Location`. The parallel `PantryUnit` is never created, and the note about it shadowing `kotlin.Unit` is moot.
- `consumedAt` keeps its name but now means *resolved at*, whichever way the item went; `disposition` says which. Undo on either swipe nulls both columns on the row it returned.
- `DeadlinePhoto.bytes` and `Secret.ciphertext` are both AES-GCM ciphertext with the 12-byte nonce prefixed. One helper, one format, no separate nonce column.
- `Deadline.dueOn` is the expected return date for `LENDING`, not a loan-start date. Its Today rule fires when that date is overdue and never claims how long the other person has held the item.

Recurring deadlines keep one row. Completing one advances `dueOn` by whole `repeatDays` intervals to the first scheduled occurrence after today and clears its snooze. Completing a one-shot sets `completedAt`. This preserves cadence without inventing occurrence history the schema cannot represent.

### The pantry list query

The `×n` card is derived at read time. There is no count column, and adding one later would be the wrong fix — the whole point of `design/spec.md` §7.3 is that two boxes are two rows with two honest dates.

The list filters to one location and to open rows — `WHERE location = ? AND consumedAt IS NULL` — and *then* groups, selecting `MIN(expiresOn)` and `COUNT(*)` per group. The same product in the fridge and in the freezer is therefore two cards, one on each tab, which is also why the tab counts are cards rather than boxes. Two further details decide whether it works:

- **Group on `COALESCE(barcode, 'id:' || id)`, not on `barcode`.** SQLite treats NULLs as equal in `GROUP BY`, so grouping on the bare column collapses every hand-entered item in the pantry into a single card — spinach, tomatoes and the sourdough as one row reading `×3`. The spec's "rows with a null barcode never group" is intent; this expression is what implements it.
- **Resolving picks `ORDER BY expiresOn ASC NULLS LAST LIMIT 1` within the group.** Plain `ASC` sorts NULL first in SQLite, so a dateless box would be consumed ahead of a dated one — and would contradict the card, since `MIN()` skips NULLs. minSdk 30 guarantees SQLite ≥ 3.32, where `NULLS LAST` is available.

The card's date is therefore always the date the next swipe will resolve, which is the property the whole model rests on.

### The run-out rule

Today's second rule fires for a product whose open rows have all been resolved. It needs no new machinery beyond one column: a product is a run-out candidate when it has zero rows with `consumedAt IS NULL`, its most recent `consumedAt` is within the hold window, and `runOutDismissedAt` is either null or older than that `consumedAt` — so a product bought and finished again re-fires without any extra bookkeeping. "Got it" writes the timestamp.

The rule is **barcoded items only**: a null-barcode row has no `Product` to hold the dismissal, and two hand-typed "tomatoes" are not reliably the same thing, which is the same reason they never group in the list.

### Migrations

`exportSchema = true` with the Room Gradle plugin from the first commit; the `schemas/` directory is committed. `@AutoMigration` where the change allows it, hand-written `Migration` where it does not. **`fallbackToDestructiveMigration()` is never shipped** — it silently wipes a year of real data on a schema bump.

---

## 5. Backup and restore

This is the highest-risk code in the app and it is built in phase 1, not deferred.

### Backup

1. Delete the temp target first — `VACUUM INTO` fails if the file already exists.
2. `openHelper.writableDatabase.execSQL("VACUUM INTO ?", arrayOf(tempFile.absolutePath))`, outside any transaction. (Going through `RoomDatabase.query()` requires stepping the cursor or the statement never runs; `execSQL` avoids the trap.)
3. Assert the temp file is non-empty.
4. Look up, or create, `hub.db.tmp` in the user's SAF folder. **Nothing that already exists in that folder has been touched yet, and nothing is until step 7.**
5. Check `COLUMN_FLAGS` on that document for `FLAG_SUPPORTS_RENAME`. A provider without it cannot finish this sequence, and finding that out after step 7 would mean the old backup is already gone — so the check happens here, and a provider that fails it is refused with a message telling the user to pick a different folder. **Falling back to overwriting `hub.db` directly is deliberately not done**: that path is the window this sequence exists to close, and taking it quietly would make the safety depend on which folder happened to be picked.
6. Stream the temp file into `hub.db.tmp` via `contentResolver.openOutputStream(uri, "wt")`. The `"wt"` mode matters: the default mode may not truncate, leaving a tail of a previous, larger write behind. **This is the long step and the one that can die halfway through** — it now dies over a scratch file.
7. Delete the existing `hub.db`, then rename `hub.db.tmp` to `hub.db`. Two metadata operations, measured in milliseconds, with a complete backup already on disk throughout.
8. If that rename fails, the folder holds a good backup under the wrong name: say so, and name `hub.db.tmp` in the message, because renaming it by hand is the recovery.
9. Delete the temp file. Record `lastBackupAt` in `SharedPreferences`.

The backup uses a **fixed filename, `hub.db`**, written through `hub.db.tmp` — friendly to Drive and Syncthing versioning, and it keeps the folder from filling up. Writing straight over `hub.db` is what the staging file exists to avoid: `"wt"` truncates the old backup to nothing before the first byte of the new one arrives, so a dead battery or a full disk during the copy leaves no backup at all. Note the SAF trap: `DocumentFile.createFile` with an existing name silently produces `hub (1).db`, so look up the existing document and write to its URI rather than creating a new one each time. (Timestamped filenames are the alternative if keeping a history locally turns out to matter more — decide before shipping.)

The user picks the backup folder once through the Storage Access Framework (e.g. `Documents/Hub/`) and the app persists the URI permission. That folder is visible to file managers, Drive, Syncthing, and USB — `Android/data/<package>/files/` is not, which is why it is not used.

### Restore

Order matters; getting it wrong corrupts the database silently. **Validate the incoming file completely before touching the live database** — that keeps the window in which Room is closed as short as possible, and makes the rollback path a copy failure rather than a corruption recovery.

1. Let the user pick the file with `ACTION_OPEN_DOCUMENT` and a `*/*` filter — the MIME type reported for `.db` files is inconsistent across providers, so filtering on it hides valid backups.
2. Copy the picked file to a temp file in `cacheDir`.
3. Validate the temp file: read the first 16 bytes and require the literal `SQLite format 3\0` header, read the schema version out of bytes 60–63 of that same header and abort if it is newer than this build, then open it raw and run `PRAGMA quick_check`. Abort here on any failure — nothing has been touched yet.
4. Cancel and await the WorkManager daily job — it holds a database connection.
5. Close the Room instance.
6. **Rename** the current `hub.db` to `hub.db.bak` — never delete it.
7. Copy the validated temp file into place as `hub.db`. On failure, rename `hub.db.bak` back and report it.
8. Delete `hub.db-wal` and `hub.db-shm`. A stale WAL replayed over a freshly restored file is the classic silent corruption.
9. Show a "Restored — tap to restart" dialog, and call `exitProcess(0)` when it is tapped. The dialog has to come first: after the process is killed there is nobody left to prompt. Rebuilding the Hilt-provided singleton database in place is more moving parts than it is worth for an operation performed a handful of times a year.

A backup taken from a **newer** schema version than the installed APK would make Room throw on open — and by then the live file is already gone. The header carries that version at bytes 60–63, so it is read during validation instead, while nothing has been touched, and the restore is refused with a plain message rather than a crash.

---

## 6. Build order

Phase 1 — the shell, the pantry, capture, Today and the backup path — is built. What it was built in,
and in what order, is in [`phase-1.md`](phase-1.md) §2.

Then install the APK and **live on it for two weeks before writing a second tab.** The largest risk
to this project is five half-finished tabs instead of one that gets used.

Later phases, in value order: Deadlines (warranty and documents first) → Money and trip totals →
comparative insight rules → Backlog → vault and encrypted document photos → batch scanning with
CameraX → price observations. Each is a migration on top of what exists, and nothing is scaffolded
in advance of the phase that needs it.

---

## 7. Verification

- **Backup round-trip, instrumented test, phase 1**: insert rows → back up → wipe app data → restore → assert the rows are present. This test is the acceptance criterion for step 3 above.
- **Restore hardening**: unit-test the header check and the `quick_check` rollback with a deliberately truncated file.
- **Migrations**: `MigrationTestHelper` with the exported schemas wired in as `androidTest` assets. `MigrationTest` covers 1 → 2 and 2 → 3. **Every migration adds a test** — the assertion is that rows written under the old version are still there afterwards, unchanged.
- **The grouped pantry query**, `androidTest` against a real in-memory Room database rather than a fake — the SQL is the thing under test, so nothing about it can be mocked: two rows sharing a barcode collapse to one card carrying `MIN(expiresOn)` and a count of 2; two rows with null barcodes stay two cards; resolving the group takes the row with the earliest non-null date, and a dateless row in the group is taken last. These three assertions are what stop the count model from quietly breaking.
- **Insight rules**: plain unit tests over a fake `Queries`. `ranOut` fires only when the product's last open row is resolved, holds for the window, and stops firing once `runOutDismissedAt` is newer than the resolution. Comparative rules must return *empty* with less than two months of data — assert that, and do not fake it with seed data.
- **Manual, before declaring phase 1 done**: sideload the release APK, scan three real items in a shop, scan one of them twice and confirm the card reads `×2` with the sooner date, swipe it and confirm it drops to `×1` with the later one, confirm the learned shelf life pre-fills on a rescan, force-stop, restore from the SAF folder, and confirm the daily notification fires.

---

## 8. Open items

Decisions that are settled live in the record of the phase that settled them —
[`phase-1.md`](phase-1.md) §3. What is left here is genuinely still open.

- **The run-out hold window** is seven days and has never met real use. Two weeks of living on the
  app is what decides whether that is too long, too short, or right.
- **A backup file from a newer schema than the installed APK** is caught and refused, and the
  message is written plainly in code. It has no design. `design/spec.md` §5 says so.
- **Whether either notification time should be configurable.** They are fixed at 08:00 and 18:00.
  This is a question for after two weeks of use, not before.
- **The morning summary's eventual copy** — it now covers the pantry and deadlines. Money can join
  the same ranked summary when that table exists.
