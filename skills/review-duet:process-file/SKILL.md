---
name: review-duet:process-file
description: "Process all open file-review comments in a single pass. Edit the target file in-place and mark each comment as fixed via the CLI."
---

# Process File Review Comments

You have been handed a **file review** — comments on a single file (typically a spec or doc). Your job is to apply all open comments in one pass, edit the file in-place, and mark each comment fixed via the CLI.

## Constraints

- **One file in scope.** The review JSON lists exactly one `filePath`. Do not edit other files.
- **No commits.** Leave the file dirty; the user reviews edits in their IDE.
- **No subagents.** You handle all comments yourself in a single pass.

## review-duet CLI Reference

| Command | Purpose |
|---------|---------|
| `review-duet list --review=<path> --status=open` | List open comments |
| `review-duet fix <id> --review=<path> --message "<explanation>"` | Mark comment as fixed |

The `--commit` flag is **not** used in file mode.

## Procedure

1. Run `review-duet list --review=<path> --status=open` to see all open comments. Each prints with `#<id> <file>:<line>` and the comment text.
2. Read the target file. Note that line numbers in comments may have shifted slightly — the `lineText` field in the JSON anchors the intended location.
3. Apply each comment's suggested change directly in the file.
4. For each addressed comment, run `review-duet fix <id> --review=<path> --message "<one-line explanation of what you changed>"`.
5. Comments marked `outdated` (where the original line could not be re-located) should be **skipped** — flag them in your final summary so the user knows.

## Output

After processing, print a short summary: how many comments fixed, how many skipped as outdated, and the file path edited. The plugin parses this for the completion notification.
