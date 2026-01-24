---
name: review-duet:process
description: "Process all open review comments autonomously. Spawns an agent that reads comments, fixes issues (best guess if unclear), and marks them as fixed."
---

# Process Review Comments

Spawn an agent to autonomously process all open review comments.

## Before Spawning

1. Check if `.review/comments.json` exists
2. If not, tell user: "No active review found. Start one in the IDE first."

## Spawn the Agent

Use the Task tool with `subagent_type: general-purpose`:

```
Task tool:
  description: "Process review comments"
  subagent_type: general-purpose
  prompt: |
    You are processing code review comments for this project.

    ## Your Tools

    The review-duet CLI is available. Commands:
    - `review-duet list` - List all comments (add `--status=open` to filter)
    - `review-duet show <id>` - Show full comment details with thread
    - `review-duet reply <id> "<message>"` - Reply to a comment
    - `review-duet fix <id> --commit <sha>` - Mark comment as fixed with commit reference

    ## Your Process

    1. Run `review-duet list --status=open,pending-agent` to get actionable comments

    2. For each comment:
       a. Run `review-duet show <id>` to see full context
       b. Read the file mentioned in the comment
       c. Understand what's being asked
       d. Make the fix:
          - If clear: just fix it
          - If unclear: make your best guess
       e. Commit the fix with a descriptive message
       f. If you made assumptions, reply noting them:
          `review-duet reply <id> "Fixed. Note: I assumed X because Y."`
       g. Mark as fixed:
          `review-duet fix <id> --commit <sha>`

    3. Group related fixes into logical commits when possible

    ## Important Rules

    - DO read the file context before fixing
    - DO make your best guess when unclear (note assumptions in reply)
    - DO commit after fixing (or batch related fixes)
    - DO NOT ask questions - fix everything, note assumptions
    - DO NOT run tests - user will verify
    - DO NOT skip comments - process all of them

    ## When Done

    Report summary:
    - How many comments fixed
    - Any assumptions made
    - Any comments you couldn't fix (and why)
```

## After Agent Completes

Report the agent's summary to the user. They can now:
- Review fixes in IDE
- Check the Changes tab for diffs
- Resolve or reopen comments as needed
