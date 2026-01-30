# Code Review CLI

CLI tool for local code review workflows with Claude Code.

## Installation

```bash
npm install -g @codereview/cli
```

## Usage

```bash
# List all comments
review list

# Filter by status
review list --status=open
review list --status=pending-agent

# Reply to a comment (as Claude)
review reply 1 "Should I use approach A or B?"

# Mark as fixed after committing
review fix 1 --commit abc1234

# Show full comment details
review show 1
```

## Statuses

- `open` - New comment, needs work
- `pending-user` - Waiting for user response
- `pending-agent` - Waiting for Claude to process
- `fixed` - Claude marked as fixed, needs verification
- `resolved` - User verified, done

## Integration

This CLI is designed to work with the Code Review Local IntelliJ plugin.
The shared state is stored in `.review/comments.json` (gitignored).
