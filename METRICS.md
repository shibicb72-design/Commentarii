# METRICS - Claude Code usage for v1

Headless `claude -p --output-format json` runs (Claude Code 2.1.296), token-budget setup from Theatrum: opusplan main, android-implementer on Sonnet, gradle-runner / screenshot-reviewer / Explore on Haiku. Costs are API list-price equivalents reported by Claude Code (`costBasis: list`), not money charged.

| Phase | Model | Input | Output | Cache read | Cache write | Cost USD |
|---|---|---|---|---|---|---|
| phaseA | claude-sonnet-5-5 | 86 | 62328 | 1671478 | 188086 | 1.362 |
| phaseA | claude-haiku-5-5 | 26 | 5532 | 173006 | 43318 | 0.010 |
| phaseB | claude-sonnet-5-5 | 86 | 119270 | 2513074 | 239482 | 2.129 |
| phaseB2 | claude-sonnet-5-5 | 76 | 40739 | 1976221 | 172344 | 1.130 |
| phaseB2 | claude-haiku-5-5 | 18 | 4336 | 50476 | 75725 | 0.012 |
| phaseC | claude-sonnet-5-5 | 64 | 41684 | 2209960 | 148107 | 1.083 |
| phaseC | claude-haiku-5-5 | 6 | 558 | 28109 | 17885 | 0.003 |

**Total: $5.73** across 4 runs (phaseB was cut off by the headless 10-min background-task ceiling while a subagent ran in the background; phaseB2 resumed it with foreground-only delegation).

Note: no Opus tokens were recorded; in headless `-p` mode the main loop ran on Sonnet.

## Follow-up fix round (Oct 18 2026, 11:03 PM ET)

| Run | Model | Input | Output | Cache read | Cache write | Cost USD |
|---|---|---|---|---|---|---|
| fix1 | claude-sonnet-5-5 | 62 | 42513 | 2265880 | 176682 | 1.179 |
| fix1 | claude-haiku-5-5 | 6 | 716 | 28514 | 18309 | 0.003 |

**Fix round: $1.18. Running total: $6.91.**

## Reminder fix (fix2, Oct 18 11:12 PM ET), single Sonnet session (opusplan falls back to Sonnet headless)

| Run | Model | Input | Output | Cache read | Cache write | Cost USD |
|---|---|---|---|---|---|---|
| fix2 | claude-sonnet-5-5 | 92 | 73814 | 2607368 | 219511 | 1.652 |
| fix2 | claude-haiku-5-5 | 6 | 1118 | 28942 | 18627 | 0.003 |

Usage (`claude -p /usage`): session 21% -> 24%, week 2% -> 2%.

## Exact-minute reminders + checklist alignment (fix3): Opus plan -> Sonnet implement -> Opus review -> Sonnet fix

| Step | Model | Input | Output | Cache read | Cache write | Cost USD |
|---|---|---|---|---|---|---|
| fix3-plan | claude-opus-5-5 | 12 | 7910 | 341597 | 81862 | 0.881 |
| fix3-plan | claude-haiku-5-5 | 22 | 11291 | 310540 | 51834 | 0.015 |
| fix3-impl | claude-sonnet-5-5 | 84 | 34945 | 4631232 | 125290 | 1.314 |
| fix3-impl | claude-haiku-5-5 | 30 | 6953 | 136454 | 67495 | 0.013 |
| fix3-review | claude-opus-5-5 | 24 | 8386 | 1024511 | 100351 | 1.176 |
| fix3-review | claude-haiku-5-5 | 14 | 5567 | 55271 | 40481 | 0.008 |
| fix3-fix | claude-sonnet-5-5 | 20 | 4665 | 636135 | 57375 | 0.340 |
| fix3-fix | claude-haiku-5-5 | 10 | 2534 | 45887 | 20371 | 0.004 |

Usage after each step: start 24% / week 2%; plan 26% / 2%; impl 29% / 3%; review 30% / 3%; fix 31% / 3%. (+7 session points for the feature.)
fix3 total: $3.75. Running total: $12.30.
