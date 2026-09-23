# Hub

A fully offline Android app for the things you own, owe, or track. No account, no backend, no login — all data lives in a single SQLite file you can copy to a PC or drop in a synced folder.

Hub is built on the idea that the whole app is only three primitives: a **due-date thing**, an **item with a status and a rating**, and a **money event**. A new feature has to map onto one of those with a new filter, or it does not get built. See [`docs/HANDOVER.md`](docs/HANDOVER.md) for the reasoning, [`docs/plan.md`](docs/plan.md) for the implementation decisions, and [`docs/phase-1.md`](docs/phase-1.md) for the pantry foundation, and [`docs/phase-2.md`](docs/phase-2.md) for Deadlines.

## Status

**Phase 2 is written, but the app has not yet been lived on.** Pantry, Deadlines, Today, backup and
restore all build; device-only behaviour still needs a real phone — see *What is not there* below.

### What exists today

- **App shell** — Compose, Material 3, edge-to-edge, predictive back. Five destinations in a fixed
  order: `Pantry · Deadlines · Today · Money · Backlog`, with Today centred and the start
  destination. The two that are not built are visible but ghosted: 38% emphasis, a thinner icon
  and a dotted underline where a live tab would grow an indicator. Tapping one answers with a
  snackbar, and TalkBack reads it as dimmed rather than skipping it.
- **The look** — a fixed Material 3 scheme seeded from a deep teal, with a plum tertiary reserved for
  the throw-away gesture. Dynamic colour from the wallpaper is a setting and is off by default.
  Gabarito and Figtree are bundled rather than fetched, and every count and days-left number is set
  in tabular figures.
- **Pantry** — location tabs over swipeable cards, with counts, urgency filters and a sort control.
  Two boxes of the same thing are two rows with two honest dates, shown as one `×2` card carrying
  the sooner one. Swipe right marks it consumed, swipe left marks it binned; both arm at 40%, commit
  with a haptic, and give you four seconds of undo instead of a confirmation dialog. Swiping a
  counted card counts the badge down and rewrites the date in place — only the last one collapses.
- **Capture** — scan a barcode and Hub branches three ways. Something already on a shelf stops and
  waits for a tap, and that tap adds another box and finishes: three taps from the button, no date
  step. A hit on something new shows what it found and moves on by itself. A miss or a two-second
  timeout moves on too, because the naming step exists on every path.
- **Learning** — correct the date, the description or the shelf once, and Hub offers it the next
  time you scan the same barcode. Only a correction teaches it anything; accepting what it offered
  writes nothing.
- **Item detail** — reached through a shared-element transition on the name and the urgency chip.
  It lists both entries of a `×2` under one card with the earlier one marked "goes first", and it is
  the only screen that explains the count model.
- **Today** — one insight card at a time with a counter and a peek of the next, covering food expiry,
  run-out, warranties, documents, bills and overdue lending. Clearing the queue is a
  visible event and the empty state is a designed screen. Snooze puts a card down until tomorrow and
  writes that down, so it stays down across a restart and the notifications stay quiet about it; a
  `N snoozed · Show` row picks them back up for as long as the app stays open. Snoozing does not
  count towards the cleared tally, because putting a card aside is not dealing with it.
- **Deadlines** — warranties, documents, upkeep, bills, vehicles and lending share one active list,
  one editor and one detail/completion flow. Filters and urgency thresholds vary by kind; recurring
  rows roll to their next scheduled date, while one-shots complete. Lending dates mean “expected
  back”, and costs use one app-wide currency stored as exact minor units.
- **Deadline insights** — warranties, documents and bills join Today inside their documented
  horizons; lending appears only once its expected-return date is overdue. All insight families use
  one severity/date ranking, an eight-card cap and the same persistent Snooze behaviour.
- **Backup and restore** — pick a folder once through the Storage Access Framework and Hub writes
  `hub.db` into it. The new file is staged beside the old one and swapped in by a rename, so a write
  that dies halfway leaves the previous backup whole. Restoring validates the whole file — header,
  schema version and `PRAGMA quick_check` — before touching anything, and renames your current
  database aside rather than deleting it.
- **Two summaries a day**, at 08:00 and 18:00, off until you switch them on — which is also when
  Hub asks for permission to post them. They are not the same sentence twice: the morning one is
  the week ahead plus what you have run out of and what is coming due; the evening one is only what
  needs attention today or tomorrow. Both stay silent on a day with nothing to say. They are the only notifications the app
  sends.
- **Database** — Room, schema version 3, with `Product`, `PantryItem`, `Trip` and `Deadline`. There is no
  quantity column and no unit: a free-text description typed off the pack replaces both, and Hub
  never parses, converts or sums it.

Expiry dates are stored as epoch days (`LocalDate`), not instants, so "expires in 2 days" does not
shift across midnight or a DST boundary. Money, when it arrives, is stored as a `Long` in minor
units.

### What is not there

- Money and Backlog are ghosted tabs. The Deadline tab and “Add a deadline” FAB action are live.
- The instrumented tests compile and are written against a real database, but they have not been
  executed. The barcode scanner, the two notifications and the SAF round trip all need a phone to be
  believed, and the staged-then-renamed backup in particular has only been reasoned about.

## Next up

In value order. Each one is a schema migration on top of what exists now — nothing here is scaffolded in advance.

1. **Live on it for two weeks.** The largest risk to this project is five half-finished tabs instead
   of one that gets used, so the next thing is not a feature.
2. **Money** — expenses, income and debts, plus per-trip receipt totals.
3. **Comparative insights** — spending deltas and savings trends. These stay silent until there are two months of data, which is correct, not a bug.
4. **Backlog** — books, films, shows, games and places, with a status and a rating.
5. **Vault** — low-stakes secrets and encrypted document photos, AES-GCM under a key derived from a master password.
6. **Later** — batch scanning with CameraX, opportunistic price observations.

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
- There are no quantities, no units and no thresholds anywhere in the app. `design/spec.md` §7 says why.
- Compose and AndroidX are held one release behind the latest, because the newest versions require `compileSdk 37` and that platform is not published yet. That also keeps Material 3 Expressive components such as `FloatingActionButtonMenu` out of reach; the FAB menu is hand-built in the meantime. Bump them together when the platform lands.
- The two bundled typefaces are under the SIL Open Font License. See [`NOTICE.md`](NOTICE.md).
