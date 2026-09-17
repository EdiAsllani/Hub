# Hub — Implementation Plan

Companion to `HANDOVER.md`. That document settles *what* the app is and *why*; this one settles *how* it gets built: stack, schema, storage mechanics, and the order of work. Where the two disagree, `HANDOVER.md` wins on product decisions and this document wins on implementation detail.

---

## 1. Context

`HANDOVER.md` describes a fully offline Android app — a personal hub for things owned, owed, or tracked — built on three primitives (due-date thing, item + status + rating, money event) with a dashboard of insight cards as the product. The design is settled and no code exists yet. The repository currently contains only that handover document.

This plan exists to turn that design into something buildable: it fixes the open questions from §11 of the handover, pins the technology choices, expands the data model sketch into a schema that Room will actually accept, and hardens the backup/restore path — which is the one part of the app where a mistake destroys real data rather than annoying the user.

Intended outcome: a sideloadable, self-signed APK, open-sourced on GitHub, whose phase 1 (shell + pantry) is good enough to live on daily for two weeks before a second tab is written.

---

## 2. Decisions made (closes §11 of the handover)

| Question | Decision |
|---|---|
| minSdk | **30** (Android 11). Guarantees SQLite ≥ 3.27 for `VACUUM INTO` with no bundled SQLite, and keeps the app installable for anyone who finds the repo. |
| compileSdk / targetSdk | **36** (Android 16 — the owner's device). |
| App name / package | **Hub** / `com.edi.hub`. |
| Currency | **Single currency**, an app-wide setting. The `currency` column stays in the schema so multi-currency is a later migration rather than a rewrite, but no picker is ever shown. |
| Pantry units | **Fixed enum**: `pcs | g | ml`. |
| Pantry locations | **Fixed enum**: `fridge | freezer | pantry`. |
| Vault biometric unlock | **Deferred to phase 3+.** Master password only when the vault ships. |
| Backup cadence | **Manual button only** for now. A scheduled export can reuse the same code path later. |
| "Consumed" semantics | **Soft delete** — set `consumedAt`, never delete the row. Consumption history is what makes restock and spending insights possible. |
| Dependency injection | **Hilt.** |
| Module structure | **Single `:app` module**, packaged by feature. |

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
| Background work | **WorkManager** | Daily ~08:00 summary notification. |
| Settings (SAF tree URI, currency, last-backup time) | **`SharedPreferences`** | Three values. DataStore is a dependency and a coroutine API for no gain here. |
| Encryption (phase 3+) | `javax.crypto` — `PBKDF2WithHmacSHA256` (~600k iterations) + AES-GCM | No dependency. See §9 of the handover; the scheme there stands unchanged. |

### Architecture

Single `Activity`, Compose-only, no fragments. Per feature: `Dao` → `Repository` → `ViewModel` exposing a `StateFlow<UiState>`. DAO queries return `Flow`, so list animations and the dashboard update themselves on write. Insight rules live in a `domain` package as plain suspend functions over a `Queries` facade — a `List<suspend (Queries) -> List<Insight>>`, exactly as sketched in the handover. Not a rules engine.

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

Every one of these backs either a dashboard rule or a list filter. Room additionally requires an index on any foreign-key column.

### Foreign keys

- `PantryItem.tripId → Trip.id`, `ON DELETE SET NULL`.
- `MoneyEvent.tripId → Trip.id`, `ON DELETE SET NULL`.
- `DeadlinePhoto.deadlineId → Deadline.id`, `ON DELETE CASCADE`.

A database-level constraint is cheaper and more reliable than the equivalent application code.

### Tables by phase

| Phase | Tables |
|---|---|
| 1 | `Product`, `PantryItem`, `Trip` |
| 2 | `Deadline`, `MoneyEvent` |
| 3 | `BacklogItem` |
| 4 | `Secret`, `DeadlinePhoto` |
| 5 | `PriceObservation` |

Field definitions are taken verbatim from §7 of the handover; this plan does not restate them. Two clarifications:

- `PantryItem.unit` and `PantryItem.location` become enums rather than `String`, per §2. Name them **`PantryUnit`** and **`PantryLocation`** — an `enum class Unit` in the data package shadows `kotlin.Unit` for every file in that package, and a bare `Location` invites confusion with `android.location.Location`.
- `DeadlinePhoto.bytes` and `Secret.ciphertext` are both AES-GCM ciphertext with the 12-byte nonce prefixed. One helper, one format, no separate nonce column.

### Migrations

`exportSchema = true` with the Room Gradle plugin from the first commit; the `schemas/` directory is committed. `@AutoMigration` where the change allows it, hand-written `Migration` where it does not. **`fallbackToDestructiveMigration()` is never shipped** — it silently wipes a year of real data on a schema bump.

---

## 5. Backup and restore

This is the highest-risk code in the app and it is built in phase 1, not deferred.

### Backup

1. Delete the temp target first — `VACUUM INTO` fails if the file already exists.
2. `openHelper.writableDatabase.execSQL("VACUUM INTO ?", arrayOf(tempFile.absolutePath))`, outside any transaction. (Going through `RoomDatabase.query()` requires stepping the cursor or the statement never runs; `execSQL` avoids the trap.)
3. Assert the temp file is non-empty.
4. Stream it to the user's SAF folder via `contentResolver.openOutputStream(uri, "wt")`. The `"wt"` mode matters: the default mode may not truncate, leaving a tail of the previous, larger backup appended to the new one.
5. Delete the temp file. Record `lastBackupAt` in `SharedPreferences`.

The backup uses a **fixed filename, `hub.db`, overwritten in place** — friendly to Drive and Syncthing versioning, and it keeps the folder from filling up. Note the SAF trap: `DocumentFile.createFile` with an existing name silently produces `hub (1).db`, so look up the existing document and write to its URI rather than creating a new one each time. (Timestamped filenames are the alternative if keeping a history locally turns out to matter more — decide before shipping.)

The user picks the backup folder once through the Storage Access Framework (e.g. `Documents/Hub/`) and the app persists the URI permission. That folder is visible to file managers, Drive, Syncthing, and USB — `Android/data/<package>/files/` is not, which is why it is not used.

### Restore

Order matters; getting it wrong corrupts the database silently. **Validate the incoming file completely before touching the live database** — that keeps the window in which Room is closed as short as possible, and makes the rollback path a copy failure rather than a corruption recovery.

1. Let the user pick the file with `ACTION_OPEN_DOCUMENT` and a `*/*` filter — the MIME type reported for `.db` files is inconsistent across providers, so filtering on it hides valid backups.
2. Copy the picked file to a temp file in `cacheDir`.
3. Validate the temp file: read the first 16 bytes and require the literal `SQLite format 3\0` header, then open it raw and run `PRAGMA quick_check`. Abort here on any failure — nothing has been touched yet.
4. Cancel and await the WorkManager daily job — it holds a database connection.
5. Close the Room instance.
6. **Rename** the current `hub.db` to `hub.db.bak` — never delete it.
7. Copy the validated temp file into place as `hub.db`. On failure, rename `hub.db.bak` back and report it.
8. Delete `hub.db-wal` and `hub.db-shm`. A stale WAL replayed over a freshly restored file is the classic silent corruption.
9. Show a "Restored — tap to restart" dialog, and call `exitProcess(0)` when it is tapped. The dialog has to come first: after the process is killed there is nobody left to prompt. Rebuilding the Hilt-provided singleton database in place is more moving parts than it is worth for an operation performed a handful of times a year.

A backup taken from a **newer** schema version than the installed APK will make Room throw on open. Catch it and show a plain message ("this backup is from a newer version of Hub"), not a crash.

---

## 6. Build order

Unchanged from §10 of the handover. Phase 1 is the shell plus pantry:

1. Compose shell, Material 3, `NavigationBar`, five destinations — **Today · Pantry · Deadlines · Money · Backlog** — three of them empty stubs. The vault lives inside Deadlines, never as its own tab.
2. Room with the Gradle plugin, `exportSchema = true`, entities `Product` + `PantryItem` + `Trip`.
3. SAF folder picker, `VACUUM INTO` backup, and restore — **round-trip proven before anything else is built on top of it**.
4. Barcode scan via `GmsBarcodeScanning`.
5. Open Food Facts lookup, with a miss degrading to a one-field name prompt cached to `Product` with `source = USER`. The miss is a normal path, not an error state.
6. Learned `defaultShelfLifeDays` written back whenever the user corrects a date.
7. Pantry list: filter by location and urgency, sort by expiry, `Modifier.animateItem()`, urgency chips carrying **icon + text label + color** — never color alone.
8. The "+" FAB action sheet, initially with one live action.
9. Today tab wired to exactly two rules: `expiringSoon` and `lowStock`.
10. Daily WorkManager job at ~08:00 posting a summary notification. The dashboard is passive; without the nudge the app gets forgotten. Enqueue it with `enqueueUniquePeriodicWork(..., KEEP)` on every launch, so a job cancelled during a restore re-establishes itself on the next start.

Then install the APK and **live on it for two weeks before writing a second tab.** The largest risk to this project is five half-finished tabs instead of one that gets used.

Later phases, in value order: Deadlines (warranty and documents first) → Money and trip totals → comparative insight rules → Backlog → vault and encrypted document photos → batch scanning with CameraX → price observations.

---

## 7. Verification

- **Backup round-trip, instrumented test, phase 1**: insert rows → back up → wipe app data → restore → assert the rows are present. This test is the acceptance criterion for step 3 above.
- **Restore hardening**: unit-test the header check and the `quick_check` rollback with a deliberately truncated file.
- **Migrations**: `MigrationTestHelper` scaffolded from schema v1, with the exported schemas wired in as `androidTest` assets. Every migration adds a test.
- **Insight rules**: plain unit tests over a fake `Queries`. Comparative rules must return *empty* with less than two months of data — assert that, and do not fake it with seed data.
- **Manual, before declaring phase 1 done**: sideload the release APK, scan three real items in a shop, confirm the learned shelf life pre-fills on a rescan, force-stop, restore from the SAF folder, and confirm the daily notification fires.

---

## 8. Open items

- `Product.defaultUnit` and `Product.defaultLocation`, learned the same way as `defaultShelfLifeDays`, would remove two taps from every rescan. Not in the handover — **the owner's call** before the schema is frozen; adding it later is a trivial migration either way.
- Low-stock thresholds: a per-item column, or a single global rule ("quantity hits 0")? Global is assumed for phase 1.
- Notification time (08:00 assumed) — configurable, or fixed until it annoys someone?
