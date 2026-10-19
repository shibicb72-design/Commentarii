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
