# Design Brief — Hub, Phase 1 (Pantry + Today)

**For:** the design agent producing the visual design for this app.
**Read first, in this order:** `HANDOVER.md` (what the app is and why), then `docs/plan.md` (the implementation decisions already locked in). This brief overrides neither — it narrows both to the slice being designed now.

---

## 1. What you are designing

Hub is a fully offline Android app. Phase 1 ships exactly two working features:

1. **Pantry** — scan groceries, track expiry dates, filter and browse them.
2. **Today** — a dashboard of short insight cards, which is the reason to open the app daily.

Everything else in the handover document (Deadlines, Money, Backlog, the vault, shopping trips, price observations, receipt totals, OCR, batch scanning) is **out of scope** and must not appear as working UI. Placeholders in the navigation bar are the only permitted reference to them.

This is a design deliverable: HTML mockups and a written spec. No Kotlin, no Android project files, no implementation.

---

## 2. Deliverables

```
design/option-a.html
design/option-b.html
design/option-c.html      ← only if it can be genuinely different; see §5
design/spec.md
```

### The HTML files

Each is a **single self-contained canvas page** — one scrollable board laying out every screen of that option as phone frames side by side, with annotations pointing at the interesting parts. Think a design handoff board, not a clickable prototype.

- Inline CSS and inline JS only. No CDN, no external fonts, no network requests — these are opened from `file://` with no internet.
- Each phone frame is labelled with the screen name and the state it represents.
- Both **light and dark** treatments shown. Do not ship one and describe the other.
- Motion is **annotated**, not merely implied: where a transition matters, draw or caption it (what moves, from where to where, roughly how long, what it communicates). Small inline JS demos of a transition are welcome where they clarify; they are not required.

### `design/spec.md`

One document covering all options. It must contain:

- A short summary of each option and the idea behind it.
- A **differentiation matrix** — the axes from §5, showing where each option sits.
- A component inventory: every distinct component each option introduces, with its states.
- A motion table: trigger → what animates → what the user learns from it.
- A states-covered checklist: for each screen, which states are drawn (empty, loading, populated, error).

---

## 3. Screen inventory — design all of these

### Navigation shell

Five destinations, in **this exact order, do not reorder**:

```
Pantry · Deadlines · Today · Money · Backlog
   ↑         ↑         ↑       ↑        ↑
  live    disabled   live   disabled  disabled
```

Today sits in the centre. Pantry sits at the far left. The other three are disabled placeholders.

Material 3's `NavigationBar` has **no native disabled-item state**, so this needs a deliberate treatment you invent and document: reduced emphasis, non-selectable, and a tap producing a "Coming soon" acknowledgement (snackbar or equivalent). Show the disabled item's resting appearance and its tapped feedback. Do not solve this by hiding the items — the user wants to see the app growing into them.

### Today

- **Insight card feed.** Phase 1 has exactly **two** insight rules live: items expiring soon (≤ 3 days) and low stock (quantity at or below its threshold). The other rules in handover §3 are not designed here. Cards are short, plain language, ranked by severity, capped at about eight.
- **Empty state** — first launch, nothing scanned yet. This is the first screen a new user sees; treat it as a real design problem, not a shrug.
- **The FAB and its action sheet.** A single FAB opens a bottom sheet of quick actions. In phase 1 exactly one action is live — *Scan pantry item*. The remaining entries (*Add warranty · Log expense · Add document · Lend something · Mark returned · Add to list*) are shown but inactive, same as the nav placeholders.

### Pantry

- **List**, with: filter by location, filter by urgency, sort by expiry.
- **Urgency chips.** Urgency must never be communicated by colour alone — every chip carries an icon *and* a text label ("2 days") alongside the colour ramp. This is an accessibility floor, not a preference.
- **Mark consumed** — the single most-used action on this screen. Design the gesture and its feedback.
- **Empty state**, and the state where a filter matches nothing (these are different problems and want different copy).

### Scan and capture flow

Design this as a **stateful sequence**, with every state drawn:

1. Launching the scanner (system-provided scanner UI — you are designing around it, not redesigning it).
2. **Lookup hit** — the product database returns a name, brand and image; the form is pre-filled.
3. **Lookup miss** — no result. This is a *normal path and not an error*: one field, "what is this?", then the date. Design it so it feels like the app asking a question, not failing.
4. **First scan of a product** — no learned shelf life yet, so the user sets the expiry date themselves.
5. **Repeat scan of a known product** — the expiry date arrives pre-filled from the learned shelf life, and the user can correct it. Corrections teach the app. Show how that is communicated without becoming noise.
6. Confirm and save.

Scan success and failure both carry haptic feedback — the scanner is used one-handed in a shop, looking at a shelf rather than the screen. Note it in the annotations.

### Item detail

The shared-element destination from the pantry list. Shows quantity, unit, location, expiry, and offers open / consume actions.

### Settings — a small slice only

Backup folder picker, a "Back up now" button, the last-backup timestamp, and a restore button. Restore is destructive and ends in an app restart, so it needs a confirmation treatment. Nothing else belongs in settings.

---

## 4. Fixed constraints

These come from decisions already made. Design within them; do not design around them.

- **Units are a fixed set:** `pcs · g · ml`. **Locations are a fixed set:** `fridge · freezer · pantry`. No "add your own" affordance anywhere.
- **Material 3 Expressive** — its motion schemes and shape system, not hand-rolled easing.
- **One-handed reachability.** FAB and sheets at the bottom. Nothing critical in the top corners.
- **Dark and light are both first-class.** Dynamic colour from wallpaper is a plausible default; show the design surviving an unusual source colour.
- **Speed of writing beats everything.** If a capture flow takes more than a few seconds, it will not get used. Count the taps in the spec.
- **Motion makes state changes legible**, not decorative. Filtering and sorting should visibly reorder the list rather than snap. List → detail is a shared-element transition. Predictive back is enabled.

### Sample data — use realistic content, not lorem ipsum

Populate the mockups with a believable household: yogurt, milk, bread, eggs, a jar of something, frozen peas. Include **two or three items in each urgency band** (expired, expiring in days, expiring in weeks, no date), **one unbarcoded loose item** (vegetables, entered by hand), and **one location that is empty**. Insight cards should read like the examples in handover §3 — "Yogurt expires in 2 days", not "Insight title here".

---

## 5. Three options, and what "different" means

Produce **three options that a person would actually have to choose between.** Three colour swaps of the same layout is a failed deliverable.

Each option must differ from the others on **at least two** of these three axes:

| Axis | Possible positions |
|---|---|
| **A — Today's card model** | dense scannable list · one large card at a time, feed-style · grouped into sections by urgency |
| **B — Capture flow** | full-screen modal sequence · bottom-sheet flow · inline row in the pantry list that expands in place |
| **C — Pantry organisation** | tabs per location · one list with filter chips · location groups in a single scroll |

The positions above are illustrative, not a menu — a better position on an axis is welcome, as long as the spec says where it sits and why.

Each option must also **stand on its own**: internally coherent, with a stated point of view (what it optimises for and what it gives up). An option that exists only to be rejected is not an option.

**Fallback rule, stated exactly:** *if a third option cannot differ from the other two on two axes, ship two options instead of three, and say in the spec why the third was dropped.* Two strong options beat three weak ones.

---

## 6. Scope lock

Do not design, mock, mention as working, or plan for: Deadlines, warranties, documents, Money, expenses, debts, Backlog, the vault or any encrypted content, shopping trips, receipt totals, per-item prices, price history, OCR of expiry dates, batch or continuous scanning, accounts, login, sync, or sharing.

The disabled navigation placeholders and the inactive entries in the FAB sheet are the **only** permitted appearance of any of these.

If something in this brief appears to conflict with `HANDOVER.md` or `docs/plan.md`, follow this brief for scope and those documents for intent, and note the conflict in `design/spec.md` rather than resolving it silently.
