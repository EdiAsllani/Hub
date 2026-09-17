# Hub

A fully offline Android app for the things you own, owe, or track. No account, no backend, no login — all data lives in a single SQLite file you can copy to a PC or drop in a synced folder.

Hub is built on the idea that the whole app is only three primitives: a **due-date thing**, an **item with a status and a rating**, and a **money event**. A new feature has to map onto one of those with a new filter, or it does not get built. See [`HANDOVER.md`](HANDOVER.md) for the reasoning and [`docs/plan.md`](docs/plan.md) for the implementation decisions.

## Status

**Phase 1 — in progress.** The project scaffold, database layer and navigation shell are in place. The pantry and dashboard screens are stubs while the visual design is chosen (see [`docs/design-prompt.md`](docs/design-prompt.md) and the `design/` folder).

### What exists today

- **App shell** — Compose, Material 3, edge-to-edge, dynamic colour, predictive back.
- **Navigation bar** with five destinations in a fixed order: `Pantry · Deadlines · Today · Money · Backlog`. Today sits in the centre and is the start destination; Pantry is at the far left. The other three are visible but disabled, and answer a tap with a "coming soon" message.
- **Database** — Room, schema version 1, with the three pantry-phase tables:
  - `Product` — the learned product cache, keyed by barcode. Holds `defaultShelfLifeDays`, which is what lets a rescan pre-fill the expiry date.
  - `PantryItem` — what is actually in the kitchen. Soft-deleted on consumption so the history survives.
  - `Trip` — one shopping trip, so prices are entered once rather than per item.
- **Dependency injection** with Hilt, and an instrumented test covering the date converters and the soft-delete behaviour.

Expiry dates are stored as epoch days (`LocalDate`), not instants, so "expires in 2 days" does not shift across midnight or a DST boundary. Money, when it arrives, is stored as a `Long` in minor units.

### The pantry feature, once the design lands

Scan a barcode, look the product up, correct the expiry date once, and every later scan of that barcode pre-fills it. A lookup miss is a normal path — one field asking what the thing is — and the answer is cached locally forever. After a few weeks of shopping, the local cache beats any public database for this household's actual products.

## Next up

In value order. Each one is a schema migration on top of what exists now — nothing here is scaffolded in advance.

1. **Finish phase 1** — pantry list with urgency filters, the scan and capture flow, the dashboard's first two insight rules (expiring soon, low stock), a SAF backup and restore round-trip, and a daily summary notification.
2. **Deadlines** — warranties, documents, upkeep, bills, vehicles and lent items, all one table with a `kind` and an optional repeat interval.
3. **Money** — expenses, income and debts, plus per-trip receipt totals.
4. **Comparative insights** — spending deltas and savings trends. These stay silent until there are two months of data, which is correct, not a bug.
5. **Backlog** — books, films, shows, games and places, with a status and a rating.
6. **Vault** — low-stakes secrets and encrypted document photos, AES-GCM under a key derived from a master password.
7. **Later** — batch scanning with CameraX, opportunistic price observations.

## Installation

Hub is sideloaded. There is no Play Store listing.

### Requirements

- Android 11 (API 30) or newer.
- Android Studio, or the Android SDK command-line tools with **platform 36** and **build-tools 36.x**.
- A JDK to run Gradle. The build provisions its own JDK 21 toolchain, so whichever JDK you have is fine.

### Build and install

```bash
git clone <this repo>
cd Hub
echo "sdk.dir=$HOME/Android/Sdk" > local.properties   # or let Android Studio write it

./gradlew :app:installDebug                            # build and install over adb
./gradlew :app:assembleDebug                           # or just produce the APK
```

The debug APK lands in `app/build/outputs/apk/debug/`.

### Release builds

A release build needs your own signing key. Create one, then put a `keystore.properties` in the project root:

```properties
storeFile=../hub-release.jks
storePassword=…
keyAlias=hub
keyPassword=…
```

```bash
./gradlew :app:assembleRelease
```

`keystore.properties` and any `*.jks` are gitignored. **Keep the keystore somewhere safe and backed up** — signing a later build with a different key forces an uninstall and reinstall, which deletes the app's data.

### Tests

```bash
./gradlew test                  # unit tests
./gradlew connectedAndroidTest  # instrumented tests; needs a device or emulator
```

## Notes for contributors

- `fallbackToDestructiveMigration()` is never used. Every schema change ships a migration, and the exported schemas in `app/schemas/` are committed.
- Urgency is never communicated by colour alone — every urgency chip carries an icon and a text label too.
- Compose and AndroidX are held one release behind the latest, because the newest versions require `compileSdk 37` and that platform is not published yet. Bump both together when it is.
