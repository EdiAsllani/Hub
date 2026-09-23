# Phase 2 — Deadlines

Phase 2 makes the due-date primitive real. Warranties, documents, upkeep, bills, vehicle dates and
lent items share one Room entity and one set of screens; each kind changes filters, urgency and copy,
not storage architecture.

## Decisions

- Room version 3 adds `Deadline` only. `MoneyEvent` stays deferred to the Money slice.
- `dueOn` is a calendar date stored as an epoch day. For lending it means the expected return date;
  Hub says a return is overdue and does not pretend it knows how long the item has been held.
- `snoozedUntil` belongs on the row. Snoozing from Today survives process death and suppresses the
  same card in both scheduled summaries until the date arrives.
- Completing a one-shot sets `completedAt`. Completing a recurring row keeps its cadence and advances
  to the first scheduled `repeatDays` occurrence after today, clearing any snooze.
- Costs are exact `Long` minor units. One ISO currency preference applies app-wide; rows do not carry
  a currency picker.
- Photos, a contacts table, Money events and vault encryption are not part of this phase.

## What shipped

- A `Deadline` entity, six `DeadlineKind` values, due-date and kind indices, DAO, Hilt wiring,
  exported schema 3 and a 2-to-3 migration test.
- Pure recurrence, kind-specific urgency, validation and integer currency parsing with unit tests.
- A live Deadlines navigation tab with an active list, kind filters, date/name sorting and distinct
  empty states.
- One generic add/edit screen with a calendar picker and optional repeat, counterparty, cost and note;
  one detail screen with kind-aware completion copy.
- A live “Add a deadline” FAB action.
- Today cards for warranties, documents, bills and overdue lending. Pantry and deadline insights share
  severity/date ranking, an eight-card cap and persistent Snooze.
- Morning summaries cover all deadline horizons. Evening summaries include only urgent bills and
  returns due today or tomorrow.

## Verification

`./gradlew test`, `:app:assembleDebug` and `:app:assembleDebugAndroidTest` pass with JDK 21, Android
platform 36 and build-tools 36.0.0. No device or emulator was attached, so the instrumented migration
suite and the interaction flow were compiled but not executed; those remain device checks rather than
claims in this record.
