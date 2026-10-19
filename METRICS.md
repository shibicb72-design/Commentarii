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

## v2 Week + Month (branch opus/v2-week-month): Opus plan -> Sonnet implement -> Opus review -> Sonnet fix

| Step | Model | Input | Output | Cache read | Cache write | Cost USD |
|---|---|---|---|---|---|---|
| v2-plan | claude-opus-5-5 | 10 | 8030 | 270448 | 56022 | 0.663 |
| v2-plan | claude-haiku-5-5 | 30 | 15249 | 504167 | 58894 | 0.020 |
| v2-impl | claude-sonnet-5-5 | 122 | 108302 | 12498889 | 261195 | 3.378 |
| v2-impl | claude-haiku-5-5 | 34 | 9532 | 171934 | 96530 | 0.019 |
| v2-review | claude-opus-5-5 | 42 | 11091 | 2044836 | 116379 | 1.562 |
| v2-review | claude-haiku-5-5 | 10 | 3416 | 46809 | 35608 | 0.007 |
| v2-fix | claude-sonnet-5-5 | 44 | 15699 | 1009780 | 108895 | 0.599 |
| v2-fix | claude-haiku-5-5 | 8 | 735 | 56293 | 10120 | 0.002 |

Usage after each step: start 31% / week 3%; plan 32% / 3%; impl 41% / 4%; review 43% / 4%; fix 44% / 4%. (+13 session points: over the 10-point mark.)
v2 total: $6.25. Running total: $18.55.

Diagnosis of the spike: the Sonnet implement step was 99 turns in one context. It re-read 12.5M cache tokens (about $3.4, 9 of the 13 points). Output was 108k and cache writes 261k, so the cost came from re-reading a growing context on every turn, not from big files or logs (gradle output went through Haiku). Opus review: 2.0M cache reads over 26 turns (it read the full diff file by file).
Fix for next time: split a large feature's IMPLEMENT into 2-3 sub-tickets (data/domain, UI, tests+screenshots), each a fresh `claude -p --model sonnet` run, so context resets between them. Give the review a `git diff --stat` plus a list of the plan's key files instead of the whole diff. Judgment: v2 (2 screens, 3 new page types, migration, 40 tests, 8 screenshots) justified a big run, but the context growth did not.

## fix4: wrapping rows + Week/Month load gate. Short pipeline: one fresh Sonnet run, then a brief Opus review given only the summary and diff stat

| Step | Model | Input | Output | Cache read | Cache write | Cost USD |
|---|---|---|---|---|---|---|
| fix4-impl | claude-sonnet-5-5 | 32 | 18176 | 1398869 | 96227 | 0.707 |
| fix4-impl | claude-haiku-5-5 | 12 | 1389 | 50964 | 31473 | 0.005 |
| fix4-review | claude-opus-5-5 | 12 | 2462 | 316264 | 46889 | 0.488 |

Usage: start 44% / week 4%; impl 46% / 4%; review 46% / 4% (+2 points). fix4 total: $1.20. Running total: $19.75.
Top consumers: the Sonnet implement step (49 turns, 1.4M cache read, $0.71), then the Opus review (316k cache read, $0.49, mostly its fixed system/context load). The review couldn't see the DailyComponents diff within its 3-file limit, so the planner checked the wrapping in the screenshot instead.
