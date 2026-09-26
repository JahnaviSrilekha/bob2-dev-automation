---
name: code-review-and-merge
description: >-
  Use when the Coding Agent has completed all sub-tasks for a user story and
  needs to run the final review gate before merging to main -- runs the full
  test suite, verifies TC-IDs from test documentation pass, performs a final
  holistic code review against the SDD, checks for no leftover TODOs or debug
  statements, verifies coverage, and only proceeds to git commit and merge
  when all gates are green.
---

# Code Review and Merge Gate

This skill is the final quality gate before code reaches main. Every check
must pass. If any check fails, fix it and re-run from that check — do not
skip ahead.

---

## Step 1 — Pre-flight: confirm everything is saved

Before running any checks:
- Confirm all files edited during this story are saved (no pending writes).
- State the story being merged: US-NNN — title.
- State the complete list of files changed.
- State the test command for the full suite.

---

## Step 2 — Run the full test suite

Run the project's full test command (not scoped — the entire suite):

```
# Examples — use whatever matches this project:
pnpm test
mvn verify
pytest -v
go test ./...
./gradlew test
```

**Gate: ALL tests must pass — zero failures, zero errors.**

If any test fails:
1. Read the failure output carefully.
2. Determine whether the failure is in code you wrote (fix it) or a
   pre-existing failure (flag it to the user — do not mask it).
3. Fix, re-run, confirm green.
4. Do not proceed until the full suite is green.

---

## Step 3 — Verify TC-IDs from test documentation

Read `testing-documentation/TEST-CASES-<feature>.md`.
Find every TC-NNN linked to the story being merged (US-NNN).

For each TC-NNN:
- Confirm it has a corresponding test in the codebase (by test name, Gherkin
  scenario tag, or description match).
- Confirm that test passed in Step 2.

Produce a verification table:

| TC-ID | Test name / Gherkin scenario | Step 2 result |
|---|---|---|
| TC-001 | `test_user_can_login_with_valid_credentials` | ✅ passed |
| TC-002 | `test_login_rejects_invalid_password` | ✅ passed |

**Gate: Every TC-NNN linked to this story must be ✅ passed.**

If a TC-ID has no corresponding test: flag it, do not proceed. Either the
test is missing (write it) or the TC-ID mapping is wrong (update the RTM).

---

## Step 4 — Final holistic code review

Review every file changed in this story against the following checklist.
State pass/fail for each item explicitly.

### 4a — SDD contract compliance
- [ ] Every API endpoint matches the contract in the SDD (method, path,
      request shape, response shape, status codes).
- [ ] Every data model change matches the SDD table definition.
- [ ] Every RabbitMQ event uses the exchange, routing key, and payload schema
      from the SDD integration catalogue.
- [ ] Security: auth checks are applied on every protected endpoint.
- [ ] Input validation matches the SDD specification.

### 4b — Code quality
- [ ] No TODO, FIXME, HACK, or XXX markers in any changed file.
- [ ] No commented-out code blocks.
- [ ] No debug statements (console.log, print, System.out.println, fmt.Print)
      in production code.
- [ ] No hardcoded secrets, credentials, or tokens.
- [ ] No file exceeds 500 lines (flag for split if so).
- [ ] No function exceeds 60 lines (flag for refactor if so).

### 4c — Test quality
- [ ] Every new public method/function has at least one unit test.
- [ ] Every new API endpoint has at least one integration test.
- [ ] Tests have clear names that describe the behaviour under test.
- [ ] No test contains `skip`, `xit`, `@Ignore`, `pytest.mark.skip` without
      a linked issue comment.

### 4d — Consistency
- [ ] Naming conventions match the existing codebase.
- [ ] Error handling follows the project's established pattern.
- [ ] No new dependencies introduced without a justification comment.

**Gate: All checklist items must be checked ✅. Any ❌ must be fixed before
proceeding.**

---

## Step 5 — Coverage check

Run the project's coverage command if available:

```
# Examples:
pnpm test -- --coverage
mvn verify -Pcoverage
pytest --cov=src --cov-report=term-missing
go test ./... -cover
```

Report the coverage percentage for the files changed in this story.

**Gate: Coverage on changed files must not decrease from the baseline.**
If coverage decreased, add the missing tests before proceeding.

If no coverage tooling is configured, skip this step and note it.

---

## Step 6 — Git commit

All gates are green. Create a single, atomic commit for this story:

```bash
git add <all changed files>
git commit -m "<type>(<scope>): <short description>

Story: US-NNN — <story title>
TC-IDs verified: TC-NNN, TC-NNN, ...

<optional: 1–2 lines of context about what was done and why>"
```

Commit message rules:
- Type: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`
- Scope: the module, service, or component name
- Subject: imperative mood, ≤ 72 chars, no period
- Body: reference the US-NNN and all TC-IDs verified in Step 3

Show the full commit message to the user for review before running `git commit`.

---

## Step 7 — Merge to main

After the commit is confirmed:

```bash
git checkout main
git pull origin main
git merge --no-ff <feature-branch> -m "merge: US-NNN <story title>"
git push origin main
```

If there are merge conflicts: resolve them, re-run the full test suite
(Step 2), confirm green, then push.

---

## Step 8 — Post-merge report

State:
- Story merged: US-NNN — title
- Commit SHA
- Files changed (count + list)
- TC-IDs verified: list
- Test suite result: N passed, 0 failed
- Coverage delta (if measured)
- Any follow-up items (deferred warnings, missing coverage areas)

Update `delivery-documentation/BACKLOG-<feature>.md` — mark the story's
Definition of Done checkboxes as complete.

---

## If any gate fails

Do not skip a failing gate. Do not merge with known failures.
State clearly:
- Which gate failed
- What the exact failure is
- What needs to be fixed

Fix it, then re-run from that gate. If the fix requires a design decision
(e.g. the SDD contract is wrong), invoke the `human-escalation` skill.
