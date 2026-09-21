# Working on Hub

Hub is a fully offline Android app, single module, Kotlin and Compose. The repository is **public**.
Read this file before doing anything in it.

## The documents, and which one wins

| Document | Settles |
|---|---|
| `docs/HANDOVER.md` | What the app is and why. Product decisions. |
| `docs/plan.md` | How it gets built: stack, schema, storage mechanics, build order. §8 is the open-items ledger. |
| `docs/phase-1.md` | What a built phase decided, and why. One per phase. The plan and task list it was built from live in git history, not the repo. |
| `design/spec.md` | The phase 1 design. **Its §7 owner decisions outrank both documents above.** |
| `README.md` | What actually exists right now. |

The mockups are artboards on a Design canvas. The owner holds the link; it is a share URL and does not
belong in the repository.

**Keep them true.** When a decision is made, a plan diverges from reality, or new context arrives, edit
the document that owns the subject in place. Never append a contradiction and leave the old text
standing — a plan that argues with itself is worse than no plan. A schema change edits `docs/plan.md` §4
and commits the regenerated `schemas/` alongside the code.

**Keep them few.** When a phase is finished, its decisions go into `docs/<phase>.md` and the plan and
task list it was built from are deleted rather than kept. Git history is the archive; the repository
holds what still binds. Rules that outlive the phase — the stack, the schema conventions, the backup
step order — stay in `docs/plan.md`.

## Before writing code

Gather evidence first. Read the sections of the documents above that cover the change, read every file
the change touches, and check `git log` on those files. Verify that any skill, API or dependency you are
about to name actually exists here before you rely on it.

## Ask the advisor

Call the `advisor` tool before starting substantive work and again before declaring the work done. If the
call fails or the tool is not offered, use the `Agent` tool with `model: "fable"` and
`subagent_type: "general-purpose"` instead: give it the task, the files involved and the approach you
intend to take, and treat its answer as the advisor's.

## Which skill for which job

| Work | Skill |
|---|---|
| Any code at all | `ponytail:ponytail` — the ladder, shortest working diff |
| A new feature or product slice | `caveman:lean-build` |
| A bug fix or small behaviour change | `caveman:surgical-patch` |
| Restructuring without behaviour change | `caveman:safe-refactor` |
| A schema, data or API migration | `caveman:migration` |
| An unexplained or intermittent failure | `caveman:investigate-first` |
| Proving existing work meets its acceptance conditions | `caveman:verify-and-stop` |
| Any Compose UI, including M3 Expressive | `material-3` |
| Before opening a PR | `ponytail:ponytail-review` |
| README, PR text, docs, and on-screen strings | `humanizer` |

If `humanizer` is unavailable, write that text as plain human prose.

## Branches and commits

Branch off `main` for each unit of work — `feat/`, `fix/`, `docs/`, `design/`, `chore/`, `refactor/`.
Commit per unit, Conventional Commits, message written with `caveman:caveman-commit`. Commit freely;
**push and merge to `main` only when asked.**

## Never commit

The repository is public and its history is permanent. Before every commit, read `git diff --cached` and
confirm none of the following is in it:

- `local.properties`, `keystore.properties`, `*.jks`, `*.keystore`, `.env` — all gitignored, keep them that way
- Any artifact or share URL carrying an `sk=` token. Those are credentials.
- The owner's email address, or any personal address. The Open Food Facts `User-Agent` contact string
  comes from a Gradle property into `BuildConfig` and defaults to the repository URL — see `docs/plan.md` §3.
- API keys, tokens, device identifiers, or a real backup file.

If something like this has already been committed, stop and say so. Do not quietly amend it away —
rotating the secret matters more than tidying the history.

## Verify before committing

`./gradlew :app:testDebugUnitTest` and `./gradlew :app:assembleDebug` must pass. Run
`connectedAndroidTest` when a device is attached. `docs/plan.md` §7 says what each piece needs covered.

## Report what you find

When you hit a real bug you are not fixing in the current unit of work, or a decision only the owner can
make, open a GitHub issue with `gh issue create` and link it in your reply. Plain prose in the body, and
none of the secrets listed above. Ask before opening the first issue in a session.

## Rules that do not bend

- `fallbackToDestructiveMigration()` is never shipped. It wipes real data on a schema bump.
- Calendar dates are `LocalDate` stored as epoch-day `Long`. Never `Instant`. Money is `Long` in minor units.
- No quantities, no units, no thresholds anywhere in the app. `design/spec.md` §7 explains why.
- Backup and restore is the one place a mistake destroys real data. Follow `docs/plan.md` §5 exactly.
