# Token Budget Enforcement — Plan

## Overview

Set a global, hard-enforcing token budget guard so that every Bob conversation is stopped before it exceeds
~80,000 tokens. The guard fires on the `UserPromptSubmit` event — before any prompt reaches the model.
When the budget is at risk, the prompt is blocked and Bob tells the user exactly how many tokens to shed
before continuing.

**Scope:** global (`~/.bob/`) — applies to every workspace.  
**Budget target:** ≤ 80,000 tokens used in a conversation (quality cliff is ~100k; 80k is the safe ceiling).  
**Block thresholds (proxy signals, since the hook cannot read the UI token meter directly):**
- Prompt character length > 5,000 chars → blocked immediately (oversized single prompt).
- Turn count (messages sent in this session) > 15 → blocked (heuristic for accumulated context).

When blocked, the message tells the user:
- What limit was hit.
- An estimate of how many tokens to reduce (characters ÷ 4 as a rough token estimate).
- Concrete actions: trim the prompt, remove `@` mentions, or start a new conversation.

---

## Sub-Task 1 — Create the hook script

**Intent:** Write the Node.js `UserPromptSubmit` hook script that enforces the budget.  
**Status:** `[ ] pending`

### Expected Outcomes
- File `~/.bob/hooks/token-guard.mjs` exists and is executable.
- When piped a payload with a prompt > 5,000 chars, the script exits 2 and prints a remediation message to stderr.
- When piped a payload whose session has > 15 prior turns (tracked in `/tmp/bob-turns-<session_id>.txt`), the script exits 2.
- All other payloads: script exits 0, no output.

### Todo List
1. Create `~/.bob/hooks/` directory.
2. Write `~/.bob/hooks/token-guard.mjs` with the following logic:
   - Read JSON payload from stdin.
   - Extract `session_id` and `prompt` from payload.
   - Maintain a per-session turn counter at `/tmp/bob-turns-<session_id>.txt`.
   - Increment turn counter on every invocation.
   - **Check 1 — Prompt size:** if `prompt.length > 5000`, calculate estimated tokens (`Math.ceil(prompt.length / 4)`), compute overage vs 80k budget, exit 2 with a message:
     > "🚫 PROMPT TOO LARGE — This prompt is ~{N} tokens. That risks exceeding the 80,000-token conversation budget. Reduce by ~{overage} tokens before sending. Actions: shorten your prompt, remove large @-mentions, or paste smaller file excerpts."
   - **Check 2 — Turn count:** if turn counter > 15, exit 2 with message:
     > "🚫 TURN LIMIT REACHED — This conversation has had {N} turns. The context window is likely approaching 80,000 tokens. Start a new conversation (+), reference your plan file with @, and continue there."
   - Exit 0 if both checks pass.
3. Make the script executable (`chmod +x`).

### Relevant Context
- Hook script receives JSON on stdin; `prompt` field contains the full prompt text.
- Exit code 2 blocks the prompt; stderr content is shown to the user as the reason.
- Turn file lives in `/tmp/` — automatically cleaned on system restart; no persistent state needed.
- Token estimate formula: `chars ÷ 4` (standard rough approximation; 1 token ≈ 4 English chars).

---

## Sub-Task 2 — Register the hook in global settings

**Intent:** Wire the hook script into `~/.bob/settings/settings.json` under the `UserPromptSubmit` event.  
**Status:** `[ ] pending`

### Expected Outcomes
- `~/.bob/settings/settings.json` contains a `hooks.UserPromptSubmit` entry pointing to `~/.bob/hooks/token-guard.mjs`.
- All existing settings (`migrations`, `approval`) are preserved exactly.
- No matcher is set (UserPromptSubmit does not use one).

### Todo List
1. Read current `~/.bob/settings/settings.json` content.
2. Merge in the hook entry — do not overwrite any existing keys:
   ```json
   "hooks": {
     "UserPromptSubmit": [
       {
         "hooks": [
           {
             "type": "command",
             "command": "node /Users/<username>/.bob/hooks/token-guard.mjs",
             "timeout": 10
           }
         ]
       }
     ]
   }
   ```
3. Use the **absolute path** to the script (not a project-relative path) since this is a global hook.
4. Read the file back and verify the JSON is valid.

### Relevant Context
- Global settings path: `~/.bob/settings/settings.json`
- `UserPromptSubmit` has no `matcher` field — omit it.
- The command must use `node` and the absolute path to the `.mjs` script.
- Existing keys: `migrations`, `approval` — must be preserved unchanged.

---

## Sub-Task 3 — Create the global token-budget rule

**Intent:** Instruct Bob itself to self-monitor and proactively warn when the conversation approaches 80k tokens, providing a second layer of enforcement as a natural part of Bob's replies.  
**Status:** `[ ] pending`

### Expected Outcomes
- File `~/.bob/rules/token-budget.md` exists.
- On every new task, Bob reads this rule and applies it automatically.
- Bob will include a token-budget status note in its replies when it estimates the conversation is getting large.

### Todo List
1. Create `~/.bob/rules/` directory if it does not exist.
2. Write `~/.bob/rules/token-budget.md` with the following instructions to Bob:
   - Monitor conversation size. When the Messages category appears to be approaching 60,000–80,000 tokens, proactively warn in your reply.
   - Warning format: include a brief `⚠️ Token Budget` callout at the top of the reply with the estimated usage and recommended action.
   - Recommend the user start a new conversation and reference the plan file.
   - Keep the rule file itself short (under 200 words) to minimise overhead.

### Relevant Context
- Global rules path: `~/.bob/rules/` (applies to all workspaces and all modes).
- Rules are loaded at task start and count toward the fixed overhead (Rules category).
- Keep the rule concise — every word in the rule file costs tokens on every turn.
