# Personal Hub — Handover Doc

**Status:** design settled, no code written yet. Working name undecided (`hub` used as placeholder).
**Audience:** the next agent/developer picking this up to start implementation.

---

## 1. What this is

A single offline Android app that acts as a **personal hub for things I own, owe, or track**. Not a suite of mini-apps bolted together — a small set of shared primitives with different views on top.

The guiding realization: there are only **three primitives** in the entire app.

| Primitive | Core fields | Examples |
|---|---|---|
| **due-date thing** | name, due date, optional repeat interval, optional cost/photo | food expiry, warranty, passport, insurance, water filter, lent drill, bill |
| **item + status + rating** | name, type, status, rating | books, movies, shows, games, places |
| **money event** | amount, date, category, optional counterparty | expenses, income, debts |

**Design rule:** a new feature must map onto an existing primitive with a new filter. If it needs a fourth primitive, it gets rejected or reshaped. This rule is the architecture — please enforce it.

Recurring is **not** a fourth primitive. It is a nullable `repeatDays` column on a due-date row. Marking done rolls the due date forward.

### Rejected ideas (and why — don't resurrect without a reason)
- **People/relationship CRM** ("haven't called X in 3 months") — feels like homework, universally abandoned.
- **Recipe suggestions from expiring pantry** — needs a recipe database. Possible later as a *view* over pantry, not a tab.
- **Generic weight/measurement logging** — shares no primitive, no cross-feature value.
- **A `Person` entity** for lending and debts — a free-text `counterparty` string column on the existing rows covers it. Do not build a contacts table.

---

## 2. Hard constraints

- **Fully offline.** No account, no backend, no login. One optional outbound HTTP call (product lookup, section 5) which must degrade gracefully to manual entry.
- **Sideloaded APK.** Self-signed release build, installed directly on the phone. No Play Store. Note: self-signed means the signing key must be kept — changing it later forces an uninstall/reinstall and data loss.
- **All data in one portable file** that can sit in a folder synced to Drive/Syncthing, and can be copied to a PC and opened with any SQLite tool.
- **Daily driver.** If a feature takes more than a few seconds to write to, it will not get used.

---

## 3. The dashboard is the product

The home tab is the reason to open the app daily. Everything else is data entry that feeds it.

It shows a ranked feed of short, plain-language insight cards:

- "Yogurt expires in 2 days"
- "Laptop warranty expires in 1 month"
- "Passport expires in 3 months"
- "You spent more on snacks this month than last"
- "You saved more last month than the 3 months before it"
- "Ardit has had your drill for 2 months"

### How to build it (keep it dumb)

```kotlin
data class Insight(
    val title: String,
    val subtitle: String?,
    val severity: Severity,      // drives ordering + color
    val deeplink: Destination?,
)

// A plain list of independent rule functions. Each returns null when it has nothing to say.
// NOT a rules engine, NOT a DSL, NOT a plugin registry.
private val rules: List<suspend (Queries) -> List<Insight>> = listOf(
    ::expiringSoon,          // pantry, <= 3 days
    ::lowStock,              // pantry qty hits 0 or a per-item threshold
    ::warrantyExpiring,      // deadlines, <= 30 days
    ::documentExpiring,      // deadlines, <= 90 days
    ::billDue,               // deadlines, <= 7 days
    ::lentTooLong,           // deadlines, counterparty != null, > 30 days
    ::categorySpendDelta,    // money, this month vs last, >= 25% swing
    ::savingsTrend,          // money, last month vs mean of 3 prior
)
```

Run them, flatten, sort by severity then date, cap at ~8 cards. Adding an insight later = adding one function to that list.

**Comparative insights (spend delta, savings trend) need at least 2 months of data before they say anything.** They return empty until then — that is correct behavior, not a bug. Do not fake them with seed data.

### The "+" button

A single FAB on the dashboard. Tapping it opens a bottom sheet of quick actions that jump straight into a capture flow:

`Scan pantry item · Add warranty · Log expense · Add document · Lend something · Mark returned · Add to list`

This is the primary write path for the whole app. Every feature added must register one entry here, or it will never get used. Order the sheet by frequency of use, not by tab order.

---

## 4. Design goals (non-negotiable — the owner cares about this more than feature count)

Target: **read easily, filter easily, write easily**, with motion that makes state changes legible rather than decorative.

### Design system: Material 3 Expressive, customized at the token level

Do not shop for a third-party Compose design system — nothing in that space earns the dependency, and M3 already ships the spring-based motion, shape morphing, and accessibility defaults this app wants. Use it as the **foundation** and get distinctiveness by swapping tokens, not by rewriting components.

Customize at exactly three points:

1. **Color scheme** — generate with Material Theme Builder, export the Compose theme file, commit it.
2. **Type scale** — one distinctive variable display face for headlines and numerals, a neutral body face. Google Fonts via `androidx.compose.ui.text.googlefonts` (downloadable) or bundled. Owner picks the faces.
3. **Shape scale** — carries more brand identity per line of code than anything else here.

Build no custom component library in phase 1. Use M3 components as-is; extract a shared composable only after the third repeat.

**Verify current API names and opt-in annotations before pinning.** `FloatingActionButtonMenu`, `MaterialShapes`, and `MotionScheme` spent time behind `@ExperimentalMaterial3ExpressiveApi` — check the current stable `androidx.compose.material3` rather than trusting the identifiers written here.

### Specifics

- **`FloatingActionButtonMenu` is exactly the "+" concept in section 3** — a FAB that expands into a list of labelled actions, motion already built. Do not hand-roll a bottom sheet for it.
- **Shared element transitions** list → detail via `SharedTransitionLayout` + `AnimatedContent`.
- **`Modifier.animateItem()`** on lazy lists so filtering and sorting visibly reorder instead of snapping.
- **Tabular figures** (`FontFeatureSettings("tnum")`) on every days-left, price, and quantity. This app is columns of numbers; proportional digits visibly jitter during list animations.
- **Material Symbols** for icons — variable weight and fill, so selection state animates fill instead of swapping assets.
- **Haptics on scan success/failure** — the scanner is used one-handed in a shop, looking at a shelf, not at the screen.
- **Predictive back** enabled.
- **One-handed reachability.** FAB and sheets at the bottom. Nothing critical in the top corners.
- **Dark and light both first-class.**
- **Dynamic color is opt-in, not the default.** A wallpaper-derived `tertiary` or `error` can land right next to the urgency red/amber, making "expired" indistinguishable from a decorative accent. Ship the fixed scheme; offer dynamic as a setting.

### The urgency ramp — the one real extension to M3

M3 has a single `error` role; this app needs a graded one. Define it as a `CompositionLocal` alongside the M3 scheme, light and dark variants, and contrast-check the colors on both surfaces.

| Bucket | Pantry | Warranty | Document | Bill |
|---|---|---|---|---|
| `expired` | past due | past due | past due | past due |
| `critical` | 0–2 days | ≤ 7 days | ≤ 30 days | ≤ 2 days |
| `soon` | 3–7 days | ≤ 30 days | ≤ 90 days | ≤ 7 days |
| `ok` | > 7 days | > 30 days | > 90 days | > 7 days |

One ramp, four colors, kind-specific thresholds. The same buckets drive both the list chips and the dashboard insight severity ordering — do not define them twice.

**Accessibility floor:** urgency must never be communicated by color alone. Every urgency chip carries an icon and a text label ("2 days") alongside the color.

---

## 5. Barcodes — what is actually true

This was researched carefully. Do not design around assumptions here.

- A retail barcode (**EAN-13 / UPC-A**) encodes a **GTIN and nothing else**. It identifies a *product type*. Every identical can of beans on earth carries the same number. **There is no expiry date, no price, and no batch number in it.**
- Expiry *can* appear in **GS1-128 or GS1 DataMatrix** under Application Identifier **`(17)`**, formatted `YYMMDD`. Common on pharma, meat, and retail-ready cases. Rare on consumer grocery packaging. Parse it when present; never depend on it.

### Product lookup — Open Food Facts

Free, no API key, no account:

```
GET https://world.openfoodfacts.org/api/v2/product/{barcode}.json?fields=product_name,brands,quantity,image_url
User-Agent: HubApp/1.0 (contact-email)
```

A descriptive `User-Agent` is required by their policy. Sister databases with the same API shape: **Open Beauty Facts** (cosmetics), **Open Products Facts** (everything else).

**Coverage is strong for major US/EU brands and weak for regional and store brands.** Expect frequent misses.

### The miss is the feature

Unknown barcode → user types the name once → cached in the local `Product` table forever, flagged `source = USER`. After a few weeks of normal shopping, the local cache beats any public API for that specific household's actual products. Build the app so a lookup miss is a smooth one-field prompt, not an error state.

### Scanner choice

Start with **Google Code Scanner**:

```kotlin
GmsBarcodeScanning.getClient(context).startScan()
```

No CameraX setup, no camera permission, system-provided UI. One scan per invocation. Requires Play Services.

Upgrade to **CameraX + ML Kit barcode scanning** only when batch scanning (continuous scan of a whole shopping trip) is actually needed. That is a phase 2 decision.

---

## 6. Expiry dates without retyping them

Three layers, cheapest first. **Layer 1 is the real mechanism** — the other two are opportunistic.

1. **Learned shelf life.** Store `defaultShelfLifeDays` per barcode on the `Product` row. First time an item is scanned, the user sets/corrects the date once. Every future scan of that barcode pre-fills `scanDate + defaultShelfLifeDays`. The app converges on this specific household's actual products within a few weeks.
2. **GS1 DataMatrix AI(17)** when the packaging happens to carry it. Free accuracy, parse and move on.
3. **On-device OCR** (ML Kit text recognition — free, offline) as a **bonus that pre-fills a field the user confirms**. Set expectations honestly: dot-matrix ink, embossed plastic, curved cans, and the format zoo (`DD.MM.YY`, `MM/YYYY`, `BB 12/26`, local-language "best before") mean it fails often. Never write an OCR result straight to the database without confirmation.

---

## 7. Data model sketch

Starting point, not gospel. Room entities.

Two type conventions, both deliberate:

- **Due dates are `LocalDate`, stored as epoch-day `Long`** — never `Instant`. An expiry is a calendar date, not a moment. Using `Instant` makes "expires in 2 days" flip a day across midnight, timezone changes, and DST, which breaks the dashboard's headline feature. Timestamps of *events* (`addedAt`, `occurredAt`) stay `Instant`.
- **Money is `Long` in minor units** (cents/qindarka), never `Double`. Float sums drift, and the comparative insights are subtraction over many rows.

```kotlin
// Learned product cache, keyed by barcode. Grows into a private household catalog.
@Entity
data class Product(
    @PrimaryKey val barcode: String,
    val name: String,
    val brand: String?,
    val category: String?,
    val defaultShelfLifeDays: Int?,
    val imageUrl: String?,          // remote URL only, never a BLOB
    val source: Source,             // OFF | USER
    val updatedAt: Instant,
)

// High-churn, high-volume. Stays its own table — do NOT merge into Deadline.
@Entity
data class PantryItem(
    @PrimaryKey val id: Long,
    val barcode: String?,           // null for unbarcoded things (loose vegetables)
    val name: String,
    val quantity: Double,
    val unit: String,               // pcs | g | ml
    val location: String?,          // fridge | freezer | pantry
    val addedAt: Instant,
    val expiresOn: LocalDate?,      // epoch-day Long
    val openedAt: Instant?,
    val consumedAt: Instant?,       // soft delete; keeps consumption history for insights
    val tripId: Long?,
)

// Low-volume, low-churn. Warranty + document + upkeep + bill + vehicle + lending all live here.
@Entity
data class Deadline(
    @PrimaryKey val id: Long,
    val kind: DeadlineKind,         // WARRANTY | DOCUMENT | UPKEEP | BILL | VEHICLE | LENDING
    val name: String,
    val dueOn: LocalDate,           // epoch-day Long
    val repeatDays: Int?,           // null = one-shot. Non-null = rolls forward on complete.
    val counterparty: String?,      // free text. NOT a foreign key to a Person table.
    val costMinor: Long?,
    val note: String?,
    val completedAt: Instant?,
)

// Separate table on purpose. A 200KB BLOB on Deadline itself means every list query
// drags ~10MB through a 2MB CursorWindow and janks the animated lists in section 4.
// Load only on the detail screen.
@Entity
data class DeadlinePhoto(
    @PrimaryKey val deadlineId: Long,
    val bytes: ByteArray,           // nonce-prefixed ciphertext when encrypted; see section 9
    val encrypted: Boolean,
)

@Entity
data class MoneyEvent(
    @PrimaryKey val id: Long,
    val amountMinor: Long,          // cents, not Double
    val currency: String,
    val occurredAt: Instant,
    val category: String,
    val counterparty: String?,      // debts: "Ardit owes me"
    val note: String?,
    val tripId: Long?,
)

// One shopping trip = N pantry items + 1 money event. Avoids per-item price entry.
@Entity
data class Trip(
    @PrimaryKey val id: Long,
    val store: String?,
    val occurredAt: Instant,
)

// Sparse by design. Three observations per product is enough to be useful.
@Entity
data class PriceObservation(
    @PrimaryKey val id: Long,
    val barcode: String,
    val store: String?,
    val priceMinor: Long,
    val observedAt: Instant,
)

@Entity
data class BacklogItem(
    @PrimaryKey val id: Long,
    val type: BacklogType,          // BOOK | MOVIE | SHOW | GAME | PLACE
    val name: String,
    val status: BacklogStatus,
    val rating: Int?,
    val note: String?,
)

// Encrypted low-stakes secrets only. See section 9.
@Entity
data class Secret(
    @PrimaryKey val id: Long,
    val label: String,              // plaintext, so the list is browsable while locked
    val ciphertext: ByteArray,      // 12-byte GCM nonce prefixed; see section 9
    val updatedAt: Instant,
)
```

### Money capture rule

Per-item price entry kills daily use — nobody types 20 prices standing in a kitchen. Default flow: **scan N items during a trip, enter the receipt total once**, producing one `MoneyEvent` linked to N `PantryItem` rows via `tripId`. Store is entered once per trip.

Per-item price is an **optional long-press action** that writes a `PriceObservation`. Sparse data is fine. This powers a "this is 15% above what you usually pay" hint on the item detail screen.

**Price book is not a tab.** It is a field captured opportunistically plus a view on item detail.

---

## 8. Storage gotchas — read this before writing the Room config

These are the traps that break the stated requirements. All four have bitten people.

### 8.1 "One file" is false by default

Room's default journal mode is `AUTOMATIC`, which selects **WAL** on any normal device. That produces `hub.db` **plus** `hub.db-wal` **plus** `hub.db-shm`. Copying only `hub.db` to Drive silently loses recent writes.

Fix — export a single consistent file:

```kotlin
db.query("VACUUM INTO ?", arrayOf(tempFile.absolutePath))
```

Requires SQLite ≥ 3.27. Guaranteed on API 30+, or use `androidx.sqlite:sqlite-bundled` to pin a known version regardless of OS. Alternative (worse for write throughput): `.setJournalMode(TRUNCATE)` on the Room builder.

**`VACUUM INTO` cannot write to a SAF `content://` URI** — SQLite needs a real filesystem path. So the backup is two steps: `VACUUM INTO` a temp file in `cacheDir`, then stream that file to the user's SAF folder via `contentResolver.openOutputStream(uri)`, then delete the temp file.

**Restore is the harder half and is easy to get subtly wrong.** Dropping a `.db` file into place while Room still holds it open, or while a stale `hub.db-wal` sits next to it, replays the old WAL over the freshly restored file and silently corrupts it. Correct sequence: close the Room instance → delete `hub.db-wal` and `hub.db-shm` → replace `hub.db` → reopen.

Verify the full round-trip early — write rows, back up, wipe app data, restore, confirm the rows are there. Do this in phase 1, not phase 4.

### 8.2 Schema changes will eat the data

The feature set is explicitly expected to change over time. Every entity change needs a migration.

- Set `exportSchema = true` from the very first commit.
- Use `@AutoMigration` where possible, hand-written `Migration` where not.
- **Never ship `fallbackToDestructiveMigration()`.** It silently wipes a year of real daily data on a schema bump.

### 8.3 Backup location

`Android/data/<package>/files/` is not browsable by file managers on modern Android, which makes "copy it to my PC" fail.

Use the **Storage Access Framework**: the user picks a backup folder once (e.g. `Documents/Hub/`), the app persists the URI permission, and writes backups there. That folder is then visible to the Drive app, Syncthing, or a USB cable. No Google Drive API, no OAuth, no account.

### 8.4 Photos vs "one file"

Warranty receipts and document scans are images. Files-in-a-folder breaks the single-file requirement, so store them as **BLOBs in the database**, JPEG-compressed to roughly 200 KB — in their **own `DeadlinePhoto` table**, never as a column on `Deadline` itself. A BLOB on the main row means every list query drags megabytes through a 2 MB `CursorWindow` and kills the list animations.

This is fine at the expected volume (~20–50 receipt/document photos). It is **not** fine for pantry items — pantry stores the Open Food Facts `imageUrl` string only and never a BLOB.

```
// ponytail: photo BLOBs in-db, ceiling ~50 images before VACUUM INTO gets slow.
// Upgrade path: move to a SAF-backed folder + relative path column, zip on export.
```

---

## 9. Encryption — narrow scope, one scheme

Two kinds of sensitive data end up in a file that syncs to Drive:

1. Vault entries (wifi passwords, door codes, loyalty numbers).
2. **Document photos — passport and ID scans.** This is the bigger risk and the one most likely to be overlooked, because the document tracker looks harmless.

Rules:

- **Encrypt per row, not the whole database.** Whole-DB SQLCipher forces a master-password prompt at every cold start, including while standing in a shop scanning groceries. Wrong trade-off for this app.
- **Derive the key from a master password via a KDF.** Start with `PBKDF2WithHmacSHA256` — it is in `javax.crypto`, no dependency, and at ~600k iterations it is adequate here. Argon2id is stronger but pulls in a library; take it only if there is a reason to.
- **Do not use an Android Keystore-backed key.** A Keystore key does not survive reinstall or a new phone, which turns every synced backup into unrecoverable noise. KDF keys are portable, which is the entire point of the backup file.
- Biometric unlock, if added, gates *access to the derived key in memory* for a session. It is never the only way in.
- Document photos reuse **the same** derived key and the same encrypt/decrypt helper as the vault. Do not invent a second scheme.
- One helper, one format: **AES-GCM with the 12-byte nonce prefixed to the ciphertext**, for both `Secret.ciphertext` and `DeadlinePhoto.bytes`. No separate nonce column anywhere.

**Stated honestly to the owner and worth repeating:** a hand-built vault is weaker than KeePassDX or Bitwarden. Scope it to low-stakes secrets. It is not a password manager replacement.

---

## 10. Build order

The owner has chosen to **start with pantry**, which is the feature they actually want to use.

Worth knowing: pantry is not the smallest first slice — it pulls in the camera, the network lookup, the product cache, and the learned-shelf-life logic all at once. The Deadlines tab would have proven the core architecture in a weekend with no camera and no API. That trade-off was raised and the decision stands; pantry it is. Just do not let the shell work get skipped on the way there.

### Phase 1 — shell + pantry

1. Compose app shell, Material 3, `NavigationBar`. Five destinations: **Today · Pantry · Deadlines · Money · Backlog**. Three of them are empty stubs. (Vault lives inside Deadlines, not as its own tab.)
2. Room set up with `exportSchema = true`, entities `Product` + `PantryItem` + `Trip` only.
3. SAF folder picker + `VACUUM INTO` backup + restore. **Prove the round-trip works now**, not later.
4. Barcode scan via `GmsBarcodeScanning`.
5. Open Food Facts lookup with graceful miss → manual name entry → cached to `Product`.
6. Learned `defaultShelfLifeDays` write-back on user correction.
7. Pantry list: filter by location and by urgency, sort by expiry, urgency chips with icon + label + color.
8. The "+" FAB action sheet, initially with one live action (scan pantry item).
9. Today tab wired to exactly two rules: `expiringSoon` and `lowStock`.
10. One `WorkManager` daily job at ~08:00 posting a summary notification ("3 items expiring · 1 bill due"). **The dashboard is passive; without this nudge the app gets forgotten.** No extra library needed.

Then: **install the APK and actually live on it for two weeks before writing a second tab.** The single biggest risk to this project is building five half-finished tabs instead of one that gets used.

### Phase 2 and beyond, roughly in value order

Deadlines (warranty + documents first, they are the highest value per line of code) → Money + trip totals → comparative insight rules → Backlog → vault + encrypted document photos → batch scanning with CameraX → price observations.

---

## 11. Open questions for the next session

- `minSdk` target? (API 30+ makes the `VACUUM INTO` decision trivial and unlocks better Compose defaults.)
- App name and package id.
- Single currency or multi? (Single is strongly preferred until proven otherwise.)
- Unit handling in pantry: free-text, or a fixed enum of `pcs | g | ml`?
- Locations: fixed list (fridge/freezer/pantry) or user-defined?
- Is biometric unlock wanted for the vault, on top of the master password?
- Backup cadence: manual button only, or also a scheduled automatic export? (Manual first.)
- Does "consumed" delete a pantry row or soft-delete it? (Soft-delete recommended — consumption history is what makes the spending and restock insights possible later.)
