---
name: human-escalation
description: >-
  Use when a specialized agent (Product Owner Agent, Technical Architect Agent,
  or Testing Engineer Agent) lacks sufficient context to continue and needs to
  ask a human for more information via Mattermost -- posts a structured question
  to the appropriate channel and either waits for a response or terminates the
  task until the human replies.
---

# Human Escalation

Follow this procedure whenever you (as a specialized agent) cannot proceed
because critical information is missing, ambiguous, or contradictory. Never
guess or invent requirements. Stop and ask.

---

## Step 1 — Decide whether to escalate

Escalate when ANY of the following are true:
- A required input (scope, stakeholder, technology choice, constraint) was not
  provided and cannot be reasonably inferred.
- The SRS, SDD, or test inputs contain a direct contradiction you cannot resolve.
- An open question in the source document has no answer and blocks further work.
- A design or test decision requires a trade-off that only a human can authorise.

Do NOT escalate for:
- Minor formatting or style choices you can decide yourself.
- Information you can read from an existing file in the workspace — read it first.
- Questions that are clearly answered in the SRS, SDD, or prior conversation.

---

## Step 2 — Identify the target channel

| Your current mode | Mattermost channel |
|---|---|
| Product Owner Agent | `product` |
| Technical Architect Agent | `engineering` |
| Testing Engineer Agent | `qa` |

---

## Step 3 — Compose the escalation message

Write a clear, structured message. Use this exact format:

```
🤖 **Agent Escalation — [Agent Name]**
**Task:** <one sentence describing what you were working on>
**Blocked on:** <one sentence stating exactly what information is missing>

**Questions:**
1. <Specific question 1 — one unknown per question>
2. <Specific question 2> (if needed)
3. <Specific question 3> (if needed)

**Context:**
<2–4 sentences of relevant background so the human can answer without needing
to re-read the whole document. Include REQ-IDs, file names, or section numbers
where relevant.>

**To resume:** Start a new Bob conversation, switch to [mode name], and reply
with the answers above. Reference the plan file: @<plan-file-path>
```

Keep the message under 400 words. One question per numbered item — do not bundle
multiple unknowns into one question.

---

## Step 4 — Post to Mattermost

Use the `post_message` MCP tool from the `mattermost` server:

1. First call `get_channel_by_name` with the channel name from Step 2 to retrieve
   the channel ID. Use your team name if required by the tool.
2. Then call `post_message` with:
   - `channel_id`: the ID returned in step 1
   - `message`: the formatted message from Step 3

If `get_channel_by_name` fails (channel not found), report the error clearly —
do NOT guess a channel ID or post to a different channel.

---

## Step 5 — Terminate or pause

After posting successfully, choose one of two behaviours:

### Option A — Hard stop (default)
Use this when the blocked information is so fundamental that no further work
is possible without it.

Tell the user:
```
⏸️ Task paused. I've posted an escalation message to Mattermost (#<channel>).
No further work can proceed until the following is answered:
<restate the blocking question(s) in one line each>

To resume: start a new conversation, switch to [mode name], and provide the
answers. Reference @<plan-file-path> for context.
```

Then stop. Do not attempt to continue or make assumptions.

### Option B — Partial continue
Use this when only part of the work is blocked and the rest can proceed
independently.

Tell the user what you will do vs. what is blocked, complete the unblocked
sections, and clearly mark every blocked section with:

```
> ⚠️ BLOCKED — Awaiting answer to: <question>. See Mattermost #<channel>.
```

---

## Step 6 — Record the open question

If you were writing a document (SRS, SDD, test plan), add the unanswered item
to the document's **Open Questions** section before stopping:

| ID | Question | Owner | Mattermost Channel | Status |
|---|---|---|---|---|
| OQ-NNN | <question text> | Human | #<channel> | Awaiting reply |

---

## Reminders

- Never fabricate an answer to unblock yourself. An incorrect assumption in an
  SRS or SDD propagates through every downstream artefact.
- Always post to the correct channel for your agent role.
- Always include "To resume" instructions so the human knows exactly what to do.
- If the Mattermost MCP tool is unavailable or fails, fall back to printing the
  escalation message as a clearly formatted block in the chat and applying
  Option A (hard stop).
