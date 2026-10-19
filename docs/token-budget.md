# Token budget: how to measure

The Claude Code config in `.claude/` (opusplan, Haiku/Sonnet subagents, read-deny rules,
`scripts/gradle-brief.ps1`, status line) is meant to cut tokens per ticket. Measure it; don't assume it.

## Before / after on the same ticket
1. Pick one real ticket. Note the time, then run it once with the old setup.
2. Afterwards record:
   - `npx ccusage@latest blocks` (or `blocks --recent`): tokens and cost of the 5-hour block(s) the ticket used.
   - `/usage` in the Claude Code session: the 5-hour and weekly limits used.
   - The status line (`[model] ctx N% | 5h N%`) at the end of the ticket.
3. Run the same (or an equivalent) ticket with the new setup and record the same three numbers.
4. Add one change at a time (e.g. only gradle-brief first, then the subagents, then opusplan), so a
   saving or a regression can be traced to a single change. `/clear` between tickets.

## Notes
- The main conversation should never read PNGs, full Gradle logs or `build/` files; if the context % jumps
  after a build, check that the build went through `gradle-runner`.
- Headroom (github.com/headroomlabs-ai/headroom) is only a possible later experiment, not part of this
  setup: it has open Windows bugs, and routing a subscription (OAuth) session through it is a
  Terms-of-Service grey area.

## Headless pipeline (since Oct 18 2026)
In `claude -p`, `opusplan` falls back to Sonnet, so Opus is invoked explicitly per step
(script: `C:\Users\shibi\cc_commentarii\pipeline.ps1 -Name <ticket> -TaskFile <file> -Repo <path>`):
1. PLAN `claude -p --model opus` (tools Read/Grep/Glob/Task/Write): reads AGENTS.md, uses Haiku `Explore`, writes `docs/plans/<ticket>.md` (<= 80 lines). No code.
2. IMPLEMENT `claude -p --model sonnet`: implements the plan itself (no extra android-implementer hop, saves one context load); builds via Haiku `gradle-runner`, screenshots via Haiku `screenshot-reviewer`.
3. REVIEW `claude -p --model opus`: reads the diff vs the plan, writes `docs/plans/<ticket>-review.md` (fix list or `NO FIXES`). No edits.
4. FIX `claude -p --model sonnet` only if the review lists fixes.
After every run: `claude -p "/usage"` (free) and record session/week % plus per-model cost (from `--output-format json` `modelUsage`) in METRICS.md.
All subagents run in the foreground; headless stops background subagents after 10 min (`CLAUDE_CODE_PRINT_BG_WAIT_CEILING_MS=0` to wait).
