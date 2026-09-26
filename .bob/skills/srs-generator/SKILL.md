---
name: srs-generator
description: >-
  Use when the user wants to produce, write, or refine a Software Requirements
  Specification (SRS) -- generates ISO/IEC/IEEE 29148-compliant SRS documents
  including business context, stakeholders, REQ-IDs, user stories with
  Given/When/Then acceptance criteria, NFRs classified by ISO/IEC 25010,
  assumptions, constraints, and open questions.
---

# SRS Generator

Follow this procedure to produce a complete, ISO/IEC/IEEE 29148-compliant
Software Requirements Specification. Never skip a step.

---

## Step 1 — Elicit Context (ask_followup_question)

Before writing anything, gather the following from the user. Ask one focused
question at a time if answers are missing:

1. **Product/feature name** — What is being built?
2. **Business problem** — What pain or opportunity does it address?
3. **Primary stakeholders** — Who commissions it, uses it, operates it?
4. **Scope boundary** — What is explicitly in scope vs. out of scope?
5. **Key functional capabilities** — What must the system do? (bullet list is fine)
6. **Quality attributes** — Any known performance, security, reliability, or
   usability targets?
7. **Constraints** — Technology stack, regulatory, budget, timeline?
8. **Assumptions** — What is presumed true but not verified?
9. **Open questions** — What is still unknown?

Do not invent answers. If the user cannot answer a question, mark it as an open
question in the SRS.

---

## Step 2 — Plan the output file

Determine the output path. Default: `product-documentation/SRS-<feature-name>.md`
Create the `product-documentation/` directory if it does not exist.
Confirm the path with the user before writing.

---

## Step 3 — Write the SRS

Use `write_file` to produce the document. Structure it exactly as follows.
Every section heading must be numbered. Every requirement must have a unique ID.

### Document Structure

```
# SRS: <Product/Feature Name>
**Document ID:** SRS-<YYYYMMDD>-001
**Version:** 1.0
**Status:** Draft
**Standard:** ISO/IEC/IEEE 29148:2018

---

## 1. Business Context
### 1.1 Background
### 1.2 Business Opportunity / Problem Statement
### 1.3 Business Objectives (BO-001, BO-002, ...)

## 2. Stakeholders
| Stakeholder | Role | Interest | Influence |
|---|---|---|---|

## 3. Scope
### 3.1 In Scope
### 3.2 Out of Scope
### 3.3 System Context Diagram (text-based ASCII or Mermaid)

## 4. Functional Requirements
Each requirement:
- Unique ID: REQ-F-NNN (three-digit, zero-padded)
- Priority: Must Have / Should Have / Could Have / Won't Have (MoSCoW)
- Format: "The system SHALL <observable behaviour> [when/given <condition>]."

### 4.1 <Capability Area 1>
| REQ-ID | Description | Priority | Source |
|---|---|---|---|
| REQ-F-001 | ... | Must Have | <stakeholder> |

### 4.N <Capability Area N>

## 5. User Stories and Acceptance Criteria
Each story:
- ID: US-NNN
- Format: "As a <role>, I want <goal> so that <benefit>."
- Acceptance Criteria in Given/When/Then (at least 2 scenarios per story: happy path + failure)

### US-001: <Story Title>
**Story:** As a ...
**Linked REQ-IDs:** REQ-F-001, ...

#### Scenario 1: <Happy Path Name>
**Given** ...
**When** ...
**Then** ...

#### Scenario 2: <Failure/Edge Case Name>
**Given** ...
**When** ...
**Then** ...

## 6. Non-Functional Requirements (ISO/IEC 25010)
Classify each NFR under its quality characteristic.
ID format: REQ-NF-NNN

| REQ-ID | Quality Characteristic | Sub-characteristic | Description | Measure/Target |
|---|---|---|---|---|
| REQ-NF-001 | Performance Efficiency | Time Behaviour | API p95 response ... | <= 200ms |

Quality characteristics to cover (use only what applies):
- Functional Suitability
- Performance Efficiency (Time Behaviour, Resource Utilisation, Capacity)
- Compatibility (Co-existence, Interoperability)
- Usability (Learnability, Operability, Accessibility)
- Reliability (Maturity, Fault Tolerance, Recoverability)
- Security (Confidentiality, Integrity, Non-repudiation, Authenticity)
- Maintainability (Modularity, Reusability, Analysability, Modifiability, Testability)
- Portability (Adaptability, Installability)

## 7. Assumptions
| ID | Assumption | Impact if Wrong |
|---|---|---|
| ASM-001 | ... | ... |

## 8. Constraints
| ID | Constraint | Type | Rationale |
|---|---|---|---|
| CON-001 | ... | Technical/Regulatory/Business | ... |

## 9. Open Questions
| ID | Question | Owner | Target Date |
|---|---|---|---|
| OQ-001 | ... | ... | ... |

## 10. Revision History
| Version | Date | Author | Changes |
|---|---|---|---|
| 1.0 | <date> | Product Agent | Initial draft |
```

---

## Step 4 — Validate completeness

After writing, check these gates before declaring the document complete:

- [ ] Every BO has at least one linked REQ-F
- [ ] Every REQ-F has at least one US linked to it
- [ ] Every US has at least 2 acceptance criteria scenarios
- [ ] Every NFR has a measurable target (not vague)
- [ ] No section is empty or marked "TBD" without a corresponding Open Question

If any gate fails, fix it before finishing.

---

## Step 5 — Report to user

State:
- The output file path
- The count of: REQ-F IDs, US IDs, REQ-NF IDs, Open Questions
- Any Open Questions that need stakeholder input before the SRS can be baselined
