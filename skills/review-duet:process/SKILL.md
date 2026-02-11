---
name: review-duet:process
description: "Process all open review comments autonomously. Triages by complexity, batches simple fixes, dispatches focused agents."
---

# Process Review Comments

Autonomously process all open review comments using a triage-and-dispatch approach.

## review-duet CLI Reference

Commands (always use `--review=<path>` to specify the review file):

| Command | Purpose |
|---------|---------|
| `review-duet accept --review=<path>` | Accept previous changes, move baseline to HEAD |
| `review-duet list --review=<path> --status=open` | List open comments with all details |
| `review-duet fix <id> --commit <sha> --review=<path> --message "text"` | Mark comment as fixed |

### list output format

```
#<id> [<status>] <file_path>:<line> @ <original_commit_sha>
   "<comment text, may span
   multiple lines>"
```

Example:
```
#14 [open] includes/api/emailgateway.sendemail.inc.php:92 @ f76370488
   "Simplify this nested conditions, we can combine this one and below."

#15 [open] includes/api/shared-domain-connect.register.inc.php:181 @ f76370488
   "Return SenderDomain instead of prefix."
```

Parse each comment to extract: **id**, **file**, **line**, **original commit**, **comment text**. These are passed to sub-agents.

---

## Phase 1: Find Review Files

Check if `.idea/vcs.xml` exists in the current directory:

```bash
cat .idea/vcs.xml 2>/dev/null
```

**If it exists**, parse VCS mappings to get repo paths:
- `$PROJECT_DIR$` = current directory
- `$PROJECT_DIR$/subdir` = `./subdir`

**If it doesn't exist**, use the current directory as the only repo.

For each repo path:
1. Get the branch: `cd <repo_path> && git branch --show-current`
2. Check if `<repo_path>/.review-duet/<branch>.json` exists
3. Only keep repos where the review file exists

If no review files found anywhere, tell user: "No active review found. Start one in the IDE first."

---

## Phase 2: List Comments & Check for Work

For each repo with a review file:

1. Run `review-duet accept --review=<path>` to accept previous changes
2. Run `review-duet list --review=<path> --status=open` to get all open comments

The `list` output includes everything: comment ID, file path, line number, original commit SHA (`@ <sha>`), and full comment text.

**If 0 open comments in a repo, skip it.** If 0 comments across all repos, tell user: "No open comments found." and stop.

---

## Phase 3: Gather Context for Agents

Only run this phase for repos that have open comments.

**If multiple repos have comments, run these steps in parallel across repos.**

### 3a. Feature context

```bash
cd <repo_path>
BASE=$(git merge-base develop HEAD 2>/dev/null || git merge-base main HEAD 2>/dev/null)
if [ -n "$BASE" ]; then
  git log "$BASE"..HEAD --oneline -10
  git diff "$BASE"..HEAD --stat
else
  git log HEAD --oneline -10
fi
```

### 3b. Project conventions

Read CLAUDE.md from the repo root or parent directories. Extract rules that affect code changes:
- Language version constraints (e.g., PHP 5.6 — no `[]`, no type hints)
- Naming conventions (camelCase, snake_case, PascalCase)
- API response patterns

These MUST be passed to sub-agents — **sub-agents do NOT inherit CLAUDE.md**.

---

## Phase 4: Triage & Group

### Classify each comment as Simple or Complex

- **Simple**: The fix is obvious from the comment alone. Renames, removals, string changes, reordering parameters, simplifying conditions, changing return values. May touch multiple files if the comment explicitly says so (e.g., "also fix in X").
- **Complex**: Requires understanding architecture or making design decisions. Restructuring logic, adding new functions/classes/features, moving code between modules, anything where the "how" isn't obvious.

### Group for dispatch

1. **Batch simple fixes** — group into batches based on unique file count (up to ~6 files per batch). If all simple comments share few files, batch them all.
2. **Keep complex fixes individual** — one agent per comment.
3. **Preserve ID order** — comments on the same file must be processed in ascending ID order.

### Show triage summary

```
Found N comments in <repo>:
- X simple (Y batches) → Sonnet
- Z complex (individual) → Opus
Proceeding...
```

---

## Phase 5: Dispatch Agents

**Within a repo**: dispatch agents SEQUENTIALLY (one at a time) to avoid git conflicts.

**Across repos**: dispatch in PARALLEL (one agent per repo simultaneously).

After each agent completes, check its result. Note failures but continue with remaining agents.

### Simple Batch Agent

```
Task tool:
  description: "Fix review comments (batch)"
  subagent_type: general-purpose
  model: sonnet
  prompt: |
    Fix these review comments. One commit per comment.

    ## Setup
    Review file: `<REVIEW_FILE_PATH>`
    Repo root: `<REPO_ROOT>`
    **Always run commands from the repo root.**

    ## Project Conventions
    <EXTRACTED_CONVENTIONS>

    ## Feature Context
    Branch: <BRANCH_NAME>
    Recent commits:
    <COMMITS>
    Files changed:
    <STAT>

    ## Comments to Fix (process in order)

    ### Comment #<ID>
    File: <file_path>:<line>
    Original commit: <commit_sha>
    Comment: <comment_text>

    (repeat for each comment in batch)

    ## Process for Each Comment

    1. Run `git show <original_commit>:<file_path>` to see the code at the time of the comment — focus on ~20 lines around the target line to understand the surrounding code structure, not just the single line
    2. Read the current file to find where that code is now (it may have shifted)
    3. Understand the reviewer's INTENT, not just literal words — comments may have typos or implicit references (e.g., "this one and below" means the current block and the next one)
    4. Make the fix using Edit (if editing multiple files, call Edit in parallel). After editing, review the Edit tool's output to verify the result looks correct — check for syntax errors, missing semicolons, wrong naming conventions
    5. Commit and mark fixed:
       ```
       cd <REPO_ROOT> && git add <files> && git commit -m "$(cat <<'EOF'
       Short summary of the change

       Review comment #<id>: <brief quote of what was asked>
       EOF
       )" && review-duet fix <id> --commit $(git rev-parse HEAD) --review=<REVIEW_FILE_PATH> --message "Explanation of what was changed and why"
       ```

    If multiple comments target the same file+commit, run `git show` once and reuse. Re-read the current file after each commit if the next comment touches the same file.

    ## Performance Rules
    - Chain Bash commands with `&&` — every tool call adds ~7s latency
    - Call multiple Edit/Read tools in parallel when independent
    - Do NOT run git status, git diff, or other verification commands
    - Do NOT search for similar issues — these are pre-triaged simple fixes
    - Do NOT ask questions — fix everything, note assumptions
    - Do NOT run tests

    ## Output Format

    ```
    ## Results
    - #<id>: FIXED (commit <sha>) — <one-line summary of what changed>
    - #<id>: FAILED — <reason>

    ## Assumptions (if any)
    - #<id>: <what was assumed and why>
    ```
```

### Complex Comment Agent

```
Task tool:
  description: "Fix review comment #<ID>"
  subagent_type: general-purpose
  model: opus
  prompt: |
    Fix this review comment.

    ## Setup
    Review file: `<REVIEW_FILE_PATH>`
    Repo root: `<REPO_ROOT>`
    **Always run commands from the repo root.**

    ## Project Conventions
    <EXTRACTED_CONVENTIONS>

    ## Feature Context
    Branch: <BRANCH_NAME>
    Recent commits:
    <COMMITS>
    Files changed:
    <STAT>

    ## Comment #<ID>
    File: <file_path>:<line>
    Original commit: <commit_sha>
    Comment: <comment_text>

    ## Process

    1. Run `git show <original_commit>:<file_path>` to see the code at the time of the comment — focus on ~20 lines around the target line to understand the surrounding code structure
    2. Read the current file to find where that code is now (it may have shifted)
    3. Understand the reviewer's INTENT, not just literal words — comments may have typos or implicit references. Read enough surrounding code to grasp the full picture
    4. Check for similar issues in related files/classes — fix ALL instances, not just the one mentioned
    5. Make the fix (call multiple Edit tools in parallel for independent files). After editing, review the Edit tool's output to verify the result — check syntax, naming conventions, logical correctness
    6. Commit and mark fixed:
       ```
       cd <REPO_ROOT> && git add <files> && git commit -m "$(cat <<'EOF'
       Short summary of the change

       Review comment #<id>: <brief quote of what was asked>
       EOF
       )" && review-duet fix <id> --commit $(git rev-parse HEAD) --review=<REVIEW_FILE_PATH> --message "Explanation of what was changed and why"
       ```

    ## Performance Rules
    - Chain Bash commands with `&&` — every tool call adds ~7s latency
    - Call multiple Edit/Read tools in parallel when independent
    - Do NOT run git status, git diff, or other verification commands
    - Do NOT ask questions — fix everything, note assumptions
    - Do NOT run tests

    ## Output Format

    ```
    ## Results
    - #<id>: FIXED (commit <sha>) — <one-line summary>

    ## Similar Issues Found
    - <file>:<line> — <what was fixed>

    ## Assumptions (if any)
    - <what was assumed and why>
    ```
```

---

## Phase 6: Report

After all agents complete, report to the user:

- Comments fixed per repo
- Batched (simple) vs individual (complex) breakdown
- Assumptions made
- Failures (if any)
- Processing summary (e.g., "6 simple in 2 batches via Sonnet + 2 complex via Opus")
