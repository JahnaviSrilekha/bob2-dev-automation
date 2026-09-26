---
name: test-doc-generator
description: >-
  Use when the user wants to produce, write, or refine test documentation --
  generates ISO/IEC/IEEE 29119-3-compliant test plans, test design
  specifications, test case specifications with TC-IDs, BDD Gherkin feature
  files, and a Requirements Traceability Matrix (RTM) mapping every REQ-ID to
  its TC-IDs. Applies equivalence partitioning, boundary value analysis,
  decision tables, and state transition testing.
---

# Test Documentation Generator

Follow this procedure to produce a complete, ISO/IEC/IEEE 29119-3-compliant
test documentation suite. Never skip a step.

---

## Step 1 — Gather Inputs (ask_followup_question)

Before writing anything, establish:

1. **SRS reference** — Is there an SRS file? Use `read_file` to load it.
   Extract every REQ-F-NNN and REQ-NF-NNN. This list drives the RTM.
2. **Backlog reference** — Is there a BACKLOG file in
   `delivery-documentation/`? If yes, read it. Scope test cases to
   stories in the current sprint first. For each story, map every acceptance
   criterion to a TC-ID. Use the story's Sub-tasks as a guide for unit and
   integration test granularity.
3. **SDD reference** — Is there an SDD file? Read it for API contracts,
   data models, and integration flows. These ground the test data.
4. **Test levels required** — Which of these apply?
   Unit / Integration / System / Acceptance / Performance / Security
5. **Existing test framework** — JUnit, pytest, Playwright, Cucumber, etc.?
6. **Test environment constraints** — Stubbed dependencies, test data strategy,
   anonymisation requirements?
7. **Entry/exit criteria preferences** — Any project-specific gates?

Read every referenced file before writing. Do not invent requirement content.

---

## Step 2 — Plan output files

Default output layout:
```
testing-documentation/
  TEST-PLAN-<feature>.md
  TEST-DESIGN-<feature>.md
  TEST-CASES-<feature>.md
  RTM-<feature>.md
  features/
    <feature-area>.feature     (Gherkin, one file per feature area)
```

Confirm paths with the user.

---

## Step 3 — Write the Test Plan

File: `docs/testing/TEST-PLAN-<feature>.md`

```
# Test Plan: <Feature Name>
**Document ID:** TP-<YYYYMMDD>-001
**Version:** 1.0
**Status:** Draft
**Standard:** ISO/IEC/IEEE 29119-3:2021
**SRS Reference:** SRS-<ID>

## 1. Introduction
### 1.1 Scope
### 1.2 Out of Scope
### 1.3 References

## 2. Test Items
List the software components, APIs, and user stories under test.
Reference REQ-IDs.

## 3. Features to Be Tested
| Feature | Test Level | Technique | Priority |
|---|---|---|---|

## 4. Features Not to Be Tested
State what is deferred and why.

## 5. Test Approach
### 5.1 Test Levels
For each level (Unit, Integration, System, Acceptance):
- Objective
- Technique(s) to apply
- Tools

### 5.2 Test Design Techniques
State which techniques apply:
- Equivalence Partitioning (EP): partition input domains
- Boundary Value Analysis (BVA): test at and adjacent to boundaries
- Decision Tables (DT): test combinations of conditions
- State Transition Testing (STT): test state machines

## 6. Entry and Exit Criteria
| Level | Entry Criteria | Exit Criteria |
|---|---|---|
| Unit | ... | >= 80% line coverage, 0 failing tests |
| Integration | ... | All API contracts verified |
| System | ... | All TC-IDs executed, <= 0 open Critical/High defects |
| Acceptance | ... | All US acceptance criteria passed |

## 7. Test Environment
| Component | Version | Notes |
|---|---|---|

## 8. Test Data Strategy
Describe sources, generation approach, and anonymisation.

## 9. Risks and Mitigations
| Risk | Impact | Mitigation |
|---|---|---|

## 10. Revision History
| Version | Date | Author | Changes |
|---|---|---|---|
| 1.0 | <date> | Testing Agent | Initial draft |
```

---

## Step 4 — Write the Test Design Specification

File: `docs/testing/TEST-DESIGN-<feature>.md`

For each REQ-F and REQ-NF, document the test design:

### Equivalence Partitioning
For each input field or condition, define:
| Field | Valid Partitions | Invalid Partitions |
|---|---|---|

### Boundary Value Analysis
For each numeric or range constraint:
| Parameter | Min-1 | Min | Min+1 | Nominal | Max-1 | Max | Max+1 |
|---|---|---|---|---|---|---|---|

### Decision Tables
For each combination of conditions:
| Condition 1 | Condition 2 | ... | Action |
|---|---|---|---|

### State Transition Diagrams
For any stateful behaviour, produce a Mermaid stateDiagram-v2.
Document: states, events/triggers, guard conditions, actions.

---

## Step 5 — Write the Test Case Specification

File: `docs/testing/TEST-CASES-<feature>.md`

Every test case must have all of these fields. No exceptions.

```
## TC-001: <Descriptive Title>
**REQ-ID(s):** REQ-F-NNN
**Test Level:** Unit | Integration | System | Acceptance
**Technique:** EP | BVA | DT | STT
**Priority:** Critical | High | Medium | Low

### Preconditions
- ...

### Test Data
| Parameter | Value | Notes |
|---|---|---|

### Steps
| # | Action | Expected Result |
|---|---|---|
| 1 | ... | ... |

### Expected Outcome
<Single, unambiguous observable result>

### Postconditions
- ...
```

ID format: TC-NNN (three-digit, zero-padded, globally unique per feature).
Produce test cases to cover:
- Every EP partition (one TC per partition — valid and invalid)
- Every BVA boundary (min-1, min, min+1, max-1, max, max+1)
- Every row of every decision table
- Every state transition in every STT diagram
- Every acceptance criterion scenario from every US in the SRS

---

## Step 6 — Write Gherkin Feature Files

File: `docs/testing/features/<area>.feature`

Rules:
- One `.feature` file per feature area (maps to SRS capability area)
- `Feature:` tag matches the capability area
- `@REQ-F-NNN` tag on every `Scenario` or `Scenario Outline`
- `@TC-NNN` tag on every `Scenario` or `Scenario Outline`
- Use `Scenario Outline` with `Examples:` for data-driven BVA/EP cases
- Background for shared preconditions within a feature file

```gherkin
@REQ-F-001 @TC-001
Scenario: <Descriptive title matching TC title>
  Given <precondition>
  When <action>
  Then <expected result>
  And <additional assertion>

@REQ-F-001 @TC-002
Scenario Outline: <Descriptive title for data-driven case>
  Given <precondition with <param>>
  When <action with <param>>
  Then <expected result with <outcome>>

  Examples:
    | param | outcome |
    | ...   | ...     |
```

---

## Step 7 — Write the Requirements Traceability Matrix

File: `docs/testing/RTM-<feature>.md`

```
# Requirements Traceability Matrix: <Feature Name>
**Standard:** ISO/IEC/IEEE 29119-3
**SRS Reference:** SRS-<ID>

## Forward Traceability (REQ -> TC)
| REQ-ID | Requirement Summary | TC-IDs | Feature File | Coverage Status |
|---|---|---|---|---|
| REQ-F-001 | ... | TC-001, TC-002 | area.feature | Covered |

## Reverse Traceability (TC -> REQ)
| TC-ID | Test Case Title | REQ-IDs Covered |
|---|---|---|
| TC-001 | ... | REQ-F-001 |

## Coverage Summary
| Total REQs | Covered | Not Covered | Coverage % |
|---|---|---|---|
```

Every REQ-F and REQ-NF from the SRS must appear in the forward traceability
table. Any REQ with no TC-ID must be flagged as "Not Covered" — do not silently
omit it.

---

## Step 8 — Validate completeness

- [ ] Every REQ-F has at least 2 TC-IDs (happy path + at least one negative)
- [ ] Every REQ-NF has at least 1 TC-ID (performance, security, etc.)
- [ ] Every US acceptance criterion scenario maps to exactly one TC-ID
- [ ] Every TC has a corresponding Gherkin scenario tagged with that TC-ID
- [ ] RTM forward table has 0 "Not Covered" rows (or each is explicitly
      justified as deferred with an open question)
- [ ] No TC has vague expected results ("works correctly", "succeeds") —
      every result must be observable and measurable

---

## Step 9 — Report to user

State:
- All files created and their paths
- Count of: TC-IDs created, Gherkin scenarios written, REQs covered
- RTM coverage percentage (covered REQs / total REQs)
- Any requirements with no test coverage and why
