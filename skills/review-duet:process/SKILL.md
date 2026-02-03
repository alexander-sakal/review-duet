---
name: review-duet:process
description: "Process all open review comments autonomously. Spawns an agent that reads comments, fixes issues (best guess if unclear), and marks them as fixed."
---

# Process Review Comments

Spawn an agent to autonomously process all open review comments.

## Before Spawning - Detect Context

### Flow 1: Project Root (Priority)

Check if `.idea/vcs.xml` exists in the current directory:

```bash
cat .idea/vcs.xml 2>/dev/null
```

If it exists, parse the VCS mappings to find all repo paths:
- `$PROJECT_DIR$` means current directory
- `$PROJECT_DIR$/subdir` means `./subdir`

For each repo path, check if `.review-duet/` folder exists with any `.json` files.

### Flow 2: Git Repo Root (Fallback)

If `.idea/vcs.xml` doesn't exist:
1. Check if `.review-duet/` folder exists with `.json` files
2. If not, tell user: "No active review found. Start one in the IDE first."

## Determine Review Files

For each repo found, get the current branch and construct the review path:

```bash
# Get current branch for a repo
cd <repo_path> && git branch --show-current
```

Review file path: `<repo_path>/.review-duet/<branch>.json`

Only process repos that have an existing review file.

## Spawn the Agent

Use the Task tool with `subagent_type: general-purpose`.

**Important:** Pass the list of review file paths to the agent so it knows which files to process.

```
Task tool:
  description: "Process review comments"
  subagent_type: general-purpose
  prompt: |
    You are processing code review comments for this project.

    ## Review Files to Process

    Process these review files in order:
    <INSERT_REVIEW_FILES_LIST_HERE>

    ## Your Tools

    The review-duet CLI is available. Use `--review` flag to specify which file:
    - `review-duet list --review=<path> --status=open` - List open comments
    - `review-duet show <id> --review=<path>` - Show full comment details
    - `review-duet fix <id> --commit <sha> --review=<path> [--message "<explanation>"]` - Mark as fixed
    - `review-duet accept --review=<path>` - Accept changes and move baseline

    ## Your Process

    For each review file:

    1. Run `review-duet accept --review=<path>` first to accept previous changes

    2. Run `review-duet list --review=<path> --status=open` to get actionable comments

    3. For each comment:
       a. Run `review-duet show <id> --review=<path>` to see full context
       b. Read the file mentioned in the comment (path is relative to the repo root)
       c. Understand what's being asked
       d. **Check for similar issues elsewhere:**
          - If the issue could exist in similar files/classes, search for them
          - Example: Asked to move parameters in an Action class? Check ALL Action classes
          - Fix all occurrences, not just the one mentioned
       e. Make the fix:
          - If clear: just fix it
          - If unclear: make your best guess
       f. **Commit this fix SEPARATELY** with a descriptive message
       g. Mark as fixed with explanation of what you did:
          `review-duet fix <id> --commit <sha> --review=<path> --message "Explanation of fix."`

    4. **One commit per comment** - Each comment gets its own commit. Do NOT combine multiple comments into one commit.

    ## Important Rules

    - DO read the file context before fixing
    - DO make your best guess when unclear (note assumptions in fix message)
    - DO commit EACH comment separately (one commit per fix)
    - DO search for similar issues in related files/classes and fix them all
    - DO include explanation in the --message when marking as fixed
    - DO always use --review=<path> with every CLI command
    - DO NOT batch multiple comments into one commit
    - DO NOT ask questions - fix everything, note assumptions
    - DO NOT run tests - user will verify
    - DO NOT skip comments - process all of them
    - DO NOT fix only the specific location - if the issue could exist elsewhere, find and fix all instances

    ## When Done

    Report summary:
    - How many comments fixed per repo
    - Any assumptions made
    - Any comments you couldn't fix (and why)
```

## Example: Building the Review Files List

If `.idea/vcs.xml` contains:
```xml
<mapping directory="$PROJECT_DIR$" vcs="Git" />
<mapping directory="$PROJECT_DIR$/packages/submodule" vcs="Git" />
```

And current directory is `/code/myproject`, check:
1. `/code/myproject/.review-duet/<branch>.json`
2. `/code/myproject/packages/submodule/.review-duet/<branch>.json`

Only include paths where the file actually exists.

## After Agent Completes

Report the agent's summary to the user. They can now:
- Review fixes in IDE
- Check the Changes tab for diffs
- Resolve or reopen comments as needed
