# Hub repository audit — t_595adda7
Audited local main: 51e26292d2e72daf72a9d6d81c59b8a7e4722230
Repository: C:\Dev\Repos\Hub

## For Edi

The checkout is now on main and is clean. Your 10 local commits are still there; remote main is still 661665be00be4e4b01a3a08f54443bd3ea6000c6. No source files, commits, branches, issues or remote data were created or changed by the audit, apart from switching the existing local checkout to main.

Fix restore before trusting Hub with your only copy of important data. A healthy database from another app can pass the current checks and replace Hub's database; an interrupted restore has no automatic recovery; and leaving Settings while a restore runs can bypass the required restart. Keep an independent backup until those paths are fixed and tested on a phone.

The next priorities are Today hiding unfinished work, scanner failure having no manual fallback, and save/draft handling. The existing test suite does not cover those UI/lifecycle paths. The detailed findings below include concrete failure scenarios and suggested regression tests.

Verification did not establish that the Android app builds or runs here. Both requested Gradle tasks stopped immediately because Java is missing. Host-side SQLite and static checks did run successfully and reproduced two of the data-handling problems.

## Scope and evidence

Read-only review of application architecture, storage/restore/export, Room entities/DAOs and exported schemas, capture/navigation/pantry/detail/settings, Deadlines, Today, notifications, Android configuration, dependencies/build configuration, existing tests, and product/design documentation. The tracked inventory contains 93 files, including 58 Kotlin files. Two independent read-only reviews supported the data/backup and UI analysis; their findings were checked against source before inclusion.

High = possible loss of usable data or a broken database lifecycle. Medium = incorrect user-visible behavior or a privacy/reliability gap with a concrete trigger. Low = preventative repository hygiene. Unless an entry explicitly says host SQLite reproduction, its evidence is source analysis, not an observed Android execution. No production data was used.

For compact references below, K/ means app/src/main/java/com/edi/hub/ and AT/ means app/src/androidTest/java/com/edi/hub/. Line numbers refer to the audited commit.

## Findings, in priority order

### F01 — HIGH: restore verifies SQLite integrity, not that this is a usable Hub database.
**Refs:** K/data/backup/BackupRepository.kt:130–141,177–181; K/di/DataModule.kt:24–25.
A database with only CREATE TABLE unrelated(value TEXT) and PRAGMA user_version=3 passes the header, version and quick_check checks. It is then swapped in before Room discovers that the Hub schema is missing. Hub cannot use the restored database, and the old data is left in a private .bak file with no app recovery path. Versions below the supported migration range are not rejected either.
**Evidence:** executed host SQLite probe returned header=true, user_version=3, quick_check=ok, hub_tables_present=false. This demonstrates the pre-swap validation hole; it is not an Android Room execution.
**Fix/test:** validate an isolated candidate through Room, including actual opening, supported migrations and representative reads, before closing the live database. Test unrelated SQLite, unsupported old versions, schema identity mismatch and malformed stored values; each refusal must leave current data usable.

### F02 — HIGH: restore replacement is not safe against process death.
**Refs:** K/data/backup/BackupRepository.kt:154–173; K/di/DataModule.kt:24–25.
The live database is renamed to .bak, then the incoming file is copied into the live filename. If Android kills the process between those steps, or during the copy, the catch block cannot roll back. Next launch either creates a new empty database or encounters a partial file. Startup never inspects .bak, and the next restore deletes that potential recovery copy unconditionally. Rollback also ignores whether renameTo actually succeeds.
**Evidence:** source-defined file-operation sequence; process-kill fault injection was not run.
**Fix/test:** stage and sync the complete replacement in the database directory, use rename-based commit with a recoverable marker/backup protocol, and recover interrupted operations before Room opens. Check rollback results and retain the old file until the restored database is successfully opened. Kill the app at each replacement boundary and test disk-full/rename failures.

### F03 — HIGH: restore can outlive Settings and skip the restart barrier.
**Refs:** K/ui/settings/SettingsViewModel.kt:113–124; K/ui/HubApp.kt:102–105,210; K/data/backup/BackupRepository.kt:121–145,150–169; K/ui/settings/SettingsScreen.kt:182.
Start restoring from a slow provider and press Back. Settings owns the operation through viewModelScope, but its blocking IO can continue after cancellation and close/replace the shared database. Delivery of the result back to the canceled scope can then be skipped, so restarting is never set and other screens remain reachable. Separately, a Failed outcome after db.close() only shows a message; the singleton has been closed and scheduled work canceled without a restart requirement.
**Evidence:** navigation remains enabled and the destructive phase has no cancellation boundary or application-level restart owner. No device reproduction.
**Fix/test:** serialize restore at application/activity scope, check cancellation before committing, prevent navigation during the destructive phase, and publish a global restart requirement whenever Room is closed. Exercise Back, activity recreation and failed swap during a deliberately delayed restore.

### F04 — MEDIUM: Today can say everything is clear while more due items remain.
**Refs:** K/domain/Insights.kt:74–88; K/ui/today/TodayViewModel.kt:78–95,149–155; K/ui/today/TodayScreen.kt:402–407.
With nine eligible items, reload stores only the top eight. Clearing those eight only drops queue entries; it does not refill from the database. The ninth item is still due, but the screen says “Nothing needs you today” and “nothing goes off this week.” Switching tabs/reloading reveals it again. The cap is intentional; claiming that the capped batch is the entire workload is the bug.
**Fix/test:** refill the visible capped queue after actions, or retain an explicit backlog and offer the next batch. Test nine or more eligible items through complete queue exhaustion.

### F05 — MEDIUM: repeated Today actions can clear a different card from the one written to the database.
**Refs:** K/ui/today/TodayViewModel.kt:99–110,118–130,142–155; K/ui/today/TodayScreen.kt:119–150,320–345.
Tap an action twice before its suspended DAO call finishes. Both handlers capture the same item, but each completion blindly drops the current queue head. The second completion can therefore hide the next untouched item and increment the cleared count. Snooze has the same pattern and can append the first item twice. Outgoing animated content also retains action callbacks briefly.
**Fix/test:** disable/serialize in-flight actions and update the queue by the acted-on insight ID, not drop(1). Hold the first DAO call, deliver two taps, and verify the second card is unchanged and visible.

### F06 — MEDIUM: the deadline editor has no way to reach controls when the form is taller than the viewport.
**Refs:** K/ui/deadlines/DeadlinesScreen.kt:197–232,164–181; app/src/main/AndroidManifest.xml:24.
The editor is a fixed fillMaxSize Column with six kind chips, multiple fields, a multiline note and the Save button last. There is no scroll container. On a short screen, landscape, large font scale or an IME-reduced viewport, lower fields/actions are clipped or constrained out of view. The detail screen uses the same non-scrolling structure and can lose its action row with long notes.
**Evidence:** layout source; exact screen sizes and keyboard behavior require device checks.
**Fix/test:** make form/detail content scrollable and ensure actions remain reachable with IME insets. Test small-height, landscape, large-font and long-note cases.

### F07 — MEDIUM: scanner failure has no manual-entry fallback, and unbarcoded items have no entry route.
**Refs:** K/ui/capture/BarcodeScanner.kt:23–28; K/ui/capture/CaptureScreens.kt:81–89; K/ui/components/FabMenu.kt:107–119; K/ui/HubApp.kt:221–243.
Scanner failure and user cancellation both return null, and the UI abandons capture on null. The only pantry-add action starts scanning. A missing/unavailable Google scanner, or loose produce with no barcode, therefore cannot reach the naming flow. CaptureViewModel.identify(null) supports manual input but this route never calls it.
**Fix/test:** expose Enter manually independently of scanning and distinguish cancellation from failure. Test first-use scanner failure/offline availability and an item without a barcode.

### F08 — MEDIUM: skipping lookup does not stop its result from overwriting typed input.
**Refs:** K/ui/capture/CaptureViewModel.kt:90–116,120–123; K/ui/capture/CaptureScreens.kt:99–106,128–129.
Skip only changes identified. The existing lookup continues, and a later success overwrites name, brand, description, location and image in the draft after the user has started editing them.
**Fix/test:** cancel or invalidate the lookup and ignore stale results. Suspend lookup, skip, type a custom name, then deliver the old result and assert the custom fields remain.

### F09 — MEDIUM: duplicate Save/restock submissions can insert duplicate records.
**Refs:** K/ui/capture/CaptureViewModel.kt:145–159,167–184; K/ui/capture/CaptureScreens.kt:211–214,369–374; K/ui/deadlines/DeadlinesViewModel.kt:123–144; K/ui/deadlines/DeadlinesScreen.kt:231.
Neither capture nor deadline creation synchronously marks submission in progress. Two taps before insert completion can launch two writes. Capture retains only one Saved value, so the offered Undo does not necessarily remove every accidental insert. Deadline creation similarly constructs two id=0 rows before navigation completes.
**Fix/test:** use a synchronous in-flight guard and disable submission controls; clear it only on a defined failure/retry path. Test double taps with delayed insert completion and verify one row and one navigation/undo event. Keep capture item/product learning writes transactionally consistent as part of that path.

### F10 — MEDIUM: capture and deadline drafts do not survive process death.
**Refs:** K/ui/capture/CaptureViewModel.kt:63–76,167–169; K/ui/HubApp.kt:239–252; K/ui/capture/CaptureScreens.kt:369–371; K/ui/deadlines/DeadlinesViewModel.kt:89–117.
Draft values are ordinary ViewModel/Compose state rather than saved state. Android can restore the capture navigation destination on the date step with a fresh blank draft; Save appears enabled but silently returns because the name is empty. Deadline editor route arguments survive, but unsaved field edits do not: an existing record reloads and a new record resets.
**Fix/test:** persist user-entered draft state with SavedStateHandle and validate route prerequisites. Recreate the process after editing each step and verify the entered fields and valid navigation state survive.

### F11 — MEDIUM: “No date” hides undated boxes in mixed-date groups.
**Refs:** K/data/dao/PantryDao.kt:37–43; K/ui/pantry/PantryViewModel.kt:70,121–125.
One dated and one undated row for the same product/shelf produce a card with a non-null MIN(expiresOn). The No date filter tests that aggregate, so neither row is visible there even though one box has no date.
**Evidence:** executed the actual observeCards SQL extracted from the DAO against schema 3: one card, entryCount=2, one underlying undated row, zero No date cards. This is a host SQLite reproduction, not a Compose test.
**Fix/test:** carry an undated count/flag or define entry-level filtering with consistent display and resolution semantics. Add mixed dated/undated group filtering coverage above the DAO layer.

### F12 — MEDIUM: explicitly clearing a learned date can be undone by revisiting the step.
**Refs:** K/ui/capture/CaptureScreens.kt:326,361–364,397–400,450; K/ui/capture/CaptureViewModel.kt:133–137,207–218.
Clear writes expiresOn=null, but returning to the date step treats null as “not initialized” and restores the learned date. Saving the cleared value also does not clear the learned default because correctedDate requires a non-null expiry, despite the correction callout saying Hub will remember it.
**Fix/test:** distinguish uninitialized from explicitly no date, and represent clearing separately from “leave the learned value unchanged.” Test Clear → Back → Next and a later rescan after saving the no-date choice.

### F13 — MEDIUM: date-based screens can remain on yesterday's date.
**Refs:** K/ui/pantry/PantryViewModel.kt:61–89; K/ui/detail/ItemDetailViewModel.kt:44–53; K/ui/today/TodayViewModel.kt:78–95; K/ui/today/TodayScreen.kt:80.
Dates are sampled when data/filter flows emit or when Today first enters composition, not when the day changes. Leaving a screen subscribed across midnight can keep old urgency/filter/snooze results until another event triggers a refresh. A background/resume that preserves the composition has no explicit refresh either.
**Fix/test:** provide a lifecycle-aware current-date signal and refresh on midnight/resume/time-zone changes. Advance a test clock without database writes and verify urgency and snoozed cards update.

### F14 — MEDIUM: database-only backup loses the currency needed to interpret stored costs.
**Refs:** K/data/HubPrefs.kt:65–74; K/data/backup/BackupRepository.kt:108–114; K/domain/DeadlineLogic.kt:39–55; K/ui/deadlines/DeadlinesViewModel.kt:63,91.
Deadline costs travel as minor-unit integers, but the app-wide currency stays in SharedPreferences. Restore on a clean device with a different locale or currency setting silently relabels the costs; differing currency fraction digits also change the displayed number. For example, the same stored 1234 is 12.34 in EUR but 1234 in JPY. The file is not sufficient to interpret its own financial data.
**Fix/test:** put the single data currency in the backed-up database or explicit portable backup metadata, keeping device-specific settings separate. Round-trip between different source/destination currency settings. Do not silently convert historical costs when merely changing a display preference.

### F15 — MEDIUM, platform-dependent privacy risk: root-only extraction exclusions do not exclude each backup domain.
**Refs:** app/src/main/res/xml/data_extraction_rules.xml:4–9; app/src/main/AndroidManifest.xml:12–13.
The rules exclude only the root domain. Android traverses database, shared-preference and file domains separately; excluding the root traversal does not exclude their separate traversals. Android also documents that some manufacturers permit device-to-device transfer despite allowBackup=false. On those implementations Hub's database/preferences remain eligible for transfer, contrary to the file's “Nothing leaves the device automatically” claim.
**Evidence:** Android documentation and AOSP BackupAgent source, not an observed transfer or leak. BackupAgent calls separate domain traversals (database and shared preferences included) and its manifest exclusion matching compares exact canonical paths.
**Fix/test:** explicitly exclude database/sharedpref/file/external and relevant device-protected domains using path="." in both rule sections. Test the target manufacturer's D2D behavior.
**Sources:** https://developer.android.com/identity/data/autobackup and https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/app/backup/BackupAgent.java (onFullBackup, fullBackupFileTree, manifestExcludesContainFilePath).

### F16 — LOW: .env is not ignored despite the repository's stated secret policy.
**Refs:** .gitignore:1–18; CLAUDE.md:65–74.
The policy says .env is gitignored, but git check-ignore --no-index confirms it is not. A later local environment file can be accidentally staged in this public repository. No tracked .env, local.properties, signing properties, .jks or .keystore filename was found; this is prevention, not a claim that a secret leaked.
**Fix/test:** add the intended environment-file ignore rules and verify them with git check-ignore. Keep any intentionally public example file explicitly allowed. This audit did not read credential stores or perform a full history secret scan.

## Additional coverage and release risks

Existing test inventory: 22 unit @Test methods and 19 instrumented @Test methods across eight files. This is a source count, not a passing-test count. Unit coverage is BackupFile, DeadlineLogic and Insights; instrumented coverage is DeadlineDao, HubDatabase, Migration, PantryGrouping and BackupRoundTrip. There are no UI/ViewModel interaction tests or worker/provider fault tests in that inventory.

BackupRoundTripTest exercises a local-file restore and PantryItem values, not SAF provider failure/rename semantics, process death, restart gating, currency or a complete multi-table round trip. MigrationTest covers 1→2 and 2→3 individually; its 2→3 preservation assertion seeds only a product, not pantry rows/trips and their relationships. Add 1→3 upgrades and multi-table preservation before trusting existing user databases. The exported schema deltas look consistent, but host DDL checks do not execute generated Room migrations.

DailyNudge scheduling uses a 24-hour periodic interval after an initial local-time delay (K/work/DailyNudge.kt:172–194,202–221). WorkManager is inexact and this setup does not realign on timezone/DST changes. Treat 08:00/18:00 as approximate; verify permission denial, opt-out, doze, DST/time-zone travel and restoration/re-enqueue on a device rather than promise exact delivery.

The document claim that the phase-1 round trip was “proven” (docs/phase-1.md:49–50) conflicts with README.md:72–74 saying instrumented tests were never executed. This audit supplies no missing device evidence; preserve the more cautious statement until a real instrumented run is recorded.

## Checks actually executed

1. Initial branch/status/history and read-only git ls-remote: checkout began clean on feat/capture-date-picker at 19d8dad. Existing main already pointed to 51e2629. git switch main succeeded without overwriting changes. main is 10 commits ahead of the remote main ref.
2. ./gradlew :app:testDebugUnitTest && ./gradlew :app:assembleDebug: stopped before Gradle initialization with “JAVA_HOME is not set and no 'java' command could be found in your PATH.” Unit tests did not run; the shell && did not reach assemble.
3. ./gradlew :app:assembleDebug, separately: same bootstrap error. No APK was produced. java, javac, adb and kotlinc were not found on PATH; JAVA_HOME/ANDROID_HOME/ANDROID_SDK_ROOT were empty. Standard Java/Android Studio/SDK/JDK toolchain directories checked were absent. No software was installed or configuration changed.
4. python audit_probes.py: passed after correcting the probe's handling of omitted optional Room JSON fields. Host SQLite version 3.53.1. Parsed four tracked JSON files, nine XML files and one TOML file. Executed exported schema 1/2/3 DDL and indices in memory, with integrity_check=ok and no foreign-key errors. Compared schema deltas: 1→2 adds the expected nullable columns, 2→3 leaves existing entities identical and adds deadline.
5. The same probe reproduced F01's SQLite validation bypass and F11's mixed-group filtering problem. Its exact output is in audit_probe_results.json. It does not execute Kotlin, Android SQLite, Compose, Room-generated migrations or the Gradle test suite.
6. git diff --check, git fsck --no-reflogs and bash -n gradlew: passed, exit 0.
7. Final branch/status/remote ref verification: main at 51e26292d2e72daf72a9d6d81c59b8a7e4722230, clean; remote main unchanged at 661665be00be4e4b01a3a08f54443bd3ea6000c6.

Not executed: Android unit/instrumented tests, APK build/install, device UI checks, SAF provider fault injection, restore process-kill tests, D2D transfer, notification delivery, release/R8 verification and dependency vulnerability scanning. No adb/SDK was available here; no claim is made about whether an unseen physical device is connected. Missing runtime verification is a limitation of the audit, not a passing result.

## Recommended next work

First harden restore schema validation, transactional replacement/recovery and the global restart barrier, then run a fault-injection/multi-table round trip on a disposable Android test database. Next add Today queue/action tests and capture/manual-entry/submission/draft tests. Make deadline forms reachable on small screens and preserve data currency across backups before broader daily use.

No fixes or follow-up GitHub issues were made because this card explicitly requested an audit without source changes or remote changes. No commit was created for an unchanged repository. The checkout was intentionally left on main, rather than returned to the original feature branch, as Edi requested. All evidence files live outside the repository in this task's scratch directory and are supplied with the handoff.
