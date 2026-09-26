---
name: coding-agent-workflow
description: >-
  Use when the Coding Agent needs to implement a user story -- reads the SDD
  and backlog sub-tasks, implements each sub-task vertically (API + logic + DB
  together), runs tests after each sub-task, and invokes the
  code-review-and-merge skill once all sub-tasks are complete. Automatic
  per-file inline review fires on every file write via the PostToolUse hook.
---

# Coding Agent Workflow

Follow this procedure to implement a user story from sub-tasks to merge.
Never skip a step. Never commit untested or unreviewed code.

---

## Step 1 — Load context (read before writing any code)

1. **Read the assigned story** from `delivery-documentation/BACKLOG-<feature>.md`.
   Locate the US-NNN entry. Extract:
   - Story title and description
   - Acceptance criteria (Given/When/Then)
   - Sub-task list (ST-NNN-NN, description, layer, hour estimate)
   - Dependencies (any US-NNN that must already be merged)

2. **Read the SDD** from `technical-documentation/SDD-<feature>.md`.
   Extract the sections relevant to this story:
   - API contract (endpoint signatures, GraphQL types, request/response shapes)
   - Data model changes (table columns, types, constraints)
   - Business logic description (LLD section)
   - Integration events (RabbitMQ exchanges/routing keys if applicable)
   - Security requirements (auth, input validation rules)

3. **Read the test cases** from `testing-documentation/TEST-CASES-<feature>.md`.
   Find every TC-NNN linked to this US-NNN. These are the acceptance criteria
   expressed as executable tests — they define "done".

4. **Inspect the existing codebase** — read the files you will modify. Understand
   the existing patterns: naming conventions, error handling style, test framework
   in use, folder structure. Match these exactly. Do not invent new patterns.

If any of these inputs are missing or contradictory, invoke the
`human-escalation` skill before writing any code.

---

## Step 2 — Confirm understanding before coding

Before writing any code, state:
- The story being implemented (US-NNN: title)
- The sub-tasks to complete, in order (ST-NNN-01, ST-NNN-02, ...)
- The files you expect to create or modify
- The test command you will run after each sub-task

If anything is unclear, ask the user now — not after writing code.

---

## Step 3 — Implement sub-tasks one at a time

For each sub-task in the story:

### 3a — Implement the sub-task
- Write the minimal code that fulfils this sub-task per the SDD contract.
- One sub-task = one coherent change. Do not mix sub-tasks.
- Match the existing code style exactly.
- For API sub-tasks: implement the endpoint, route registration, and input
  validation together.
- For logic sub-tasks: implement the service method and its unit test together.
- For DB sub-tasks: implement the repository method, Liquibase migration, and
  the integration test together.
- Never leave a TODO, commented-out code, or debug statement (console.log,
  print, System.out.println) in the file.

> **Note:** After every file write, the inline-review hook fires automatically.
> Read the findings it adds to your context. Fix every ❌ ERROR immediately
> before moving to the next file. Address ⚠️ WARNINGs unless there is a
> specific justified reason to defer them — state the reason inline in a
> code comment if deferring.

### 3b — Run tests after each sub-task
Run the project's test command (from `AGENTS.md` or the SDD) scoped to the
files just changed. Examples:
- `pnpm test -- --testPathPattern=<changed-file>`
- `mvn test -pl <module> -Dtest=<TestClass>`
- `pytest <test-file> -v`
- `go test ./path/to/package/...`

If tests fail: fix the failure before moving to the next sub-task. Do not
accumulate failures.

### 3c — Mark sub-task complete
After tests pass for this sub-task, update your todo list to mark it done.
State: "ST-NNN-NN complete — tests pass."

---

## Step 4 — Invoke the final review and merge gate

Once ALL sub-tasks are marked complete and all sub-task-level tests pass,
invoke the `code-review-and-merge` skill.

Do not attempt to commit or merge manually — the skill handles the gate.

---

## Reminders

- Never implement more than one sub-task at a time.
- Never move past a failing test.
- Never ignore an ❌ inline review finding.
- Never commit without invoking `code-review-and-merge`.
- If the SDD and the existing code conflict, flag it — do not guess.
