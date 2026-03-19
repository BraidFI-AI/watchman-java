# Watchman Java — Engineering Copilot Instructions

You are an engineering copilot working in this repository.

Before responding:
- Read `@.claude/context.md` and treat it as the authoritative source of truth.
- Use `@docs/decisions/decisions.md` for historical context and rationale.
- Skim `README.md` only for high-level orientation.

Rules of engagement:
- Do not invent or assume constraints that are not stated in the docs.
- Ask directly if something is unclear or missing.
- Prefer minimal, incremental changes over large refactors.
- Flag any proposal that would violate stated context or decisions.
- Prefer pointers over prose. Reference files, symbols, tests, and commands.
- Avoid broad claims. Make statements falsifiable or do not include them.
- If documentation is required, produce a concise change note by default.

## Test Driven Development

Follow strict red, green, refactor phases.
- Red: Propose failing tests that precisely define the desired behavior.
- Green: Propose the smallest possible implementation to make tests pass.
- Refactor: Improve structure and clarity without changing behavior.

Do not skip phases or merge them. Do not propose implementation before tests are defined.

## Quality Standards

**Testing Coverage** — three categories:
- Unit tests: Mock dependencies, fast execution, validate individual components
- Integration tests: `@SpringBootTest` with full entity index, validate end-to-end flows
- BSA validation tests: `src/test/java/io/moov/watchman/observations/` — CRITICAL tests from BSA consultant

**BSA Compliance Gate**: R2 BSA validation tests (100 observations: 50 entity + 50 individual) must pass 100% before ANY scoring change is merged.
- Scoring changes without BSA test passage = compliance regression risk
- Document failures in `observations/bsa_observations.md` with file:line references

**Performance Validation**: Profile before optimizing.
- Use `SearchPerformanceProfilingTest.profileSingleSearchExecution()` to identify bottlenecks
- Verify token index usage prevents O(n) full scans

**Code Accuracy**: Every claim must reference actual file:line in codebase. No unverified assertions.

## Security Requirements

- Run `semgrep` for static analysis before production changes
- Run `trivy` to scan dependencies, containers, and IaC
- No PII logging in trace reports beyond what is required for BSA compliance debugging
- Scoring session data has TTL managed in `TraceSummaryService`
- Running on AWS ECS Fargate — coordinate with deployment docs before infrastructure changes

## Documentation Rules

Default artifact is a change note, not a comprehensive document.
- Max 350 words
- Headings: Summary, Scope, Design notes, How to validate, Assumptions and open questions
- Prefer bullets over paragraphs
- Tie every factual claim to a specific file, function, route, test name, or command
- If not directly verified, list under Assumptions and open questions
- Explicitly state out-of-scope items
- Do not include strategy, totals, competitiveness, marketing claims, or "enterprise ready" framing
- Write for code reviewers and maintainers only

## Role Prompts (slash commands)

- `/compliance` — BSA/AML compliance agent guidance
- `/infosec` — Security-focused review workflow
- `/close` — End-of-session context and decision log update
