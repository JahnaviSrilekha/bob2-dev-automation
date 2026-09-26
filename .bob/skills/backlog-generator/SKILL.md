---
name: backlog-generator
description: >-
  Use when the user wants to produce, write, or refine a product backlog --
  decomposes an SRS into Epics, INVEST-validated User Stories with Fibonacci
  story point estimates, vertical slice enforcement, sub-tasks with hour
  estimates, sprint assignment based on team velocity, and a sprint plan.
  Splits any story estimated at 13+ points. Reads the SRS for REQ-IDs.
---

# Backlog Generator

Follow this procedure to produce a fully groomed, sprint-ready product backlog
from a Software Requirements Specification. Never skip a step.

---

## Step 1 — Gather Inputs (ask_followup_question)

Before writing anything, establish:

1. **SRS file** — Path to the SRS document. Use `read_file` to load it.
   Extract every REQ-F-NNN, US-NNN, and business objective. This is the
   source of truth for scope.
2. **Team velocity** — How many story points does the team complete per sprint
   on average? (If unknown, use 30 as a default and flag it.)
3. **Sprint duration** — 1-week, 2-week (default), or 3-week sprints?
4. **Team size** — Number of developers. Used to sanity-check sub-task hours.
5. **Sprint start number** — What number is the next sprint? (Default: Sprint 1)
6. **Known priorities** — Are any Epics or stories Must-Have for a specific
   release or deadline?
7. **Known dependencies** — Are there stories that must be done before others?

If the user cannot answer velocity, document the assumption and add an open
question. Do not invent priorities — escalate via human-escalation skill if
business priority is unclear.

---

## Step 2 — Plan output files

Default output layout:
```
delivery-documentation/
  BACKLOG-<feature>.md         (full backlog: Epics, Stories, Sub-tasks)
  SPRINT-PLAN-<feature>.md     (sprint-by-sprint assignment view)
```

Confirm paths with the user.

---

## Step 3 — Decompose into Epics

An Epic is a large body of work that groups related user stories delivering a
coherent capability. One Epic per major capability area from the SRS.

For each Epic:

```
## EPIC-NNN: <Title>
**Goal:** <One sentence — the business outcome this Epic delivers>
**REQ-IDs covered:** REQ-F-NNN, REQ-F-NNN, ...
**Business Objective:** BO-NNN
**Priority:** Must Have | Should Have | Could Have | Won't Have
**Stories:** US-NNN, US-NNN, ... (filled in Step 4)
```

Epics do not get story points — only stories do.

---

## Step 4 — Write INVEST User Stories

For every US-NNN in the SRS, produce a fully groomed story entry.
If the SRS does not have pre-written stories, derive them from REQ-F IDs.

### INVEST check — apply to every story before writing it

| Letter | Criterion | Failure signal |
|---|---|---|
| I | Independent | Story depends on another story being done first |
| N | Negotiable | Story prescribes implementation, not outcome |
| V | Valuable | Story delivers no user-visible value on its own |
| E | Estimable | Story is too vague to estimate with confidence |
| S | Small | Story cannot be completed within one sprint |
| T | Testable | No clear acceptance criteria can be written |

If a story fails any criterion, rewrite or split it before proceeding.

### Vertical slice check — apply to every story

A story MUST touch at least two of: UI / API / business logic / database.
A story that is ONLY "add DB column" or ONLY "write unit tests" is a
horizontal slice — split or merge it into a vertical story.

### Story format

```
### US-NNN: <Title>
**Epic:** EPIC-NNN
**REQ-IDs:** REQ-F-NNN, ...
**Story:** As a <role>, I want <goal> so that <benefit>.

**INVEST validation:**
- Independent: <yes / explain dependency>
- Negotiable: <yes / concern>
- Valuable: <yes / what user value>
- Estimable: <yes / uncertainty level>
- Small: <yes / fits in one sprint>
- Testable: <yes / acceptance criteria below>

**Vertical slice:** <List the layers touched: API / logic / DB / UI>

**Acceptance Criteria:**
- Given ... When ... Then ...
- Given ... When ... Then ...

**Story Point Estimate:** <1 | 2 | 3 | 5 | 8> points
**Estimation rationale:**
- Complexity drivers: <what makes this hard>
- Risk/uncertainty: <what is unknown>
- Comparable to: <another story of similar size, if any>

**Sub-tasks:**
| ST-ID | Description | Layer | Estimate |
|---|---|---|---|
| ST-NNN-01 | <specific technical step> | API/Logic/DB/UI/Test | ~Nh |
| ST-NNN-02 | ... | ... | ~Nh |

**Dependencies:** <none | US-NNN must be done first>
**Definition of Done:**
- [ ] All acceptance criteria pass
- [ ] Sub-tasks complete
- [ ] Code reviewed and merged
- [ ] TC-IDs linked in RTM pass
```

### Story point rules (Fibonacci: 1, 2, 3, 5, 8, 13)

| Points | Meaning |
|---|---|
| 1 | Trivial — well-understood, minimal risk, ~half a day |
| 2 | Simple — clear, low risk, ~1 day |
| 3 | Small — straightforward, some unknowns, ~1–2 days |
| 5 | Medium — moderate complexity, some design needed, ~2–3 days |
| 8 | Large — complex, significant design or integration, ~3–5 days |
| 13 | Too large — MUST be split before entering a sprint |

**Hard rule:** Any story estimated at 13 points must be split into two or more
stories, each estimated at 8 or below, before proceeding to sprint assignment.
Document the split under a `<!-- SPLIT: reason -->` comment.

### Sub-task rules

- Each sub-task is a concrete technical step completable in ~1 day or less.
- Estimate in hours (e.g. ~2h, ~4h, ~6h, ~8h). Cap at ~8h.
- Sub-tasks over ~8h must be broken down further.
- Typical sub-task types: schema migration, service method, API endpoint,
  unit tests, integration tests, UI component, documentation update.
- The sum of sub-task hours should roughly match the story point estimate
  (1pt ≈ 4h, 2pt ≈ 8h, 3pt ≈ 12–16h, 5pt ≈ 20–24h, 8pt ≈ 32–40h).

---

## Step 5 — Sprint assignment

Sort stories by:
1. Must-Have priority first
2. Dependencies (blocked stories after their blockers)
3. Highest business value per point ratio

Assign stories to sprints greedily: fill each sprint up to velocity, then
start the next. Never split a story across sprints.

For each sprint, produce:

```
## Sprint N (DD MMM – DD MMM YYYY)
**Capacity:** <velocity> points
**Committed:** <sum of story points>
**Remaining capacity:** <velocity - committed>

| Story | Title | Points | Epic | Dependencies |
|---|---|---|---|---|
| US-NNN | ... | N | EPIC-NNN | none |

**Sprint Goal:**
<One sentence describing the business outcome this sprint delivers.
Must be testable: "By the end of this sprint, a user can <X>.">

**Risks / flags:**
- <any story with high uncertainty or external dependency>
```

---

## Step 6 — Write BACKLOG file

File: `delivery-documentation/BACKLOG-<feature>.md`

Structure:
```
# Product Backlog: <Feature Name>
**Version:** 1.0  **Status:** Draft
**SRS Reference:** SRS-<ID>
**Team velocity:** <N> points/sprint  **Sprint duration:** <N> weeks

---

## Backlog Summary
| Epic | Stories | Total Points | Priority |
|---|---|---|---|

## Epics
<EPIC-NNN entries from Step 3>

## User Stories
<US-NNN entries from Step 4, grouped by Epic>

## Assumptions
| ID | Assumption | Impact if Wrong |
|---|---|---|

## Open Questions
| ID | Question | Owner | Status |
|---|---|---|---|
```

---

## Step 7 — Write SPRINT-PLAN file

File: `delivery-documentation/SPRINT-PLAN-<feature>.md`

Structure:
```
# Sprint Plan: <Feature Name>
**SRS Reference:** SRS-<ID>
**Backlog Reference:** BACKLOG-<feature>.md
**Total stories:** N  **Total points:** N  **Sprints required:** N

---

## Sprint Overview
| Sprint | Goal summary | Points | Stories |
|---|---|---|---|

## Sprint Details
<Sprint N entries from Step 5>

## Backlog Items Not Yet Scheduled
Stories deferred to future sprints or pending prioritisation.
| US-NNN | Title | Points | Reason deferred |
|---|---|---|---|
```

---

## Step 8 — Validate completeness

- [ ] Every REQ-F from the SRS is covered by at least one story
- [ ] No story is estimated at 13+ (all have been split)
- [ ] Every story passes the INVEST check (all six criteria answered)
- [ ] Every story has at least 2 acceptance criteria
- [ ] Every story is a vertical slice (touches 2+ layers)
- [ ] Every sprint has a single, testable Sprint Goal
- [ ] Sub-task hours sum is consistent with story point estimate
- [ ] No sprint is over-committed (committed <= velocity)
- [ ] Dependencies are respected in sprint ordering

---

## Step 9 — Report to user

State:
- Both output file paths
- Total Epics, Stories, story points
- Number of sprints required
- Stories split due to 13+ estimate (list them)
- Any stories not yet scheduled and why
- Any open questions requiring human input
