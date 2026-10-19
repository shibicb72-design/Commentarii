# CLAUDE.md

Read `AGENTS.md` first; it is the source of truth for this repo.

Token limits are mandatory: follow **Hard limits (v2)** in `docs/token-budget.md`.
- Steps of at most ~30 turns, each in a fresh `claude -p` session; Sonnet runs with `--max-budget-usd 1.50`.
- No Sonnet helper subagents; subagents are Haiku only (gradle-runner, screenshot-reviewer, Explore).
- Read files by line range (Grep, then Read with offset/limit); never read a file over 200 lines whole.
- Full Gradle builds run outside Claude, by the operator.
- Opus reviews a pre-generated diff of 1-3 key files only.
- Pin every model explicitly (`--model opus|sonnet|haiku`); do not rely on opusplan in `-p` mode.
- After each run: `claude -p "/usage"` and log per-model cost in METRICS.md.
