# Code Review Local - Design Document

A PhpStorm/IntelliJ plugin + CLI for local code review workflows when developing with Claude Code.

## Overview

**Purpose:** Enable structured review rounds with comment tracking, threaded discussions, and status management for AI-assisted development workflows.

**Integration Model:**
- Plugin runs in PhpStorm (review UI)
- Claude Code runs in terminal (implementation)
- `.review/comments.json` is the shared state (gitignored)
- `review` CLI tool for Claude to safely mutate comments

**Key Principles:**
- Non-blocking: Claude processes all comments, questions are batched
- Git-anchored: Comments reference git refs, not fragile line anchors
- Minimal friction: Plugin handles tagging, round management, status transitions

## Core Concepts

| Concept | Description |
|---------|-------------|
| Review Round | A tagged snapshot in git history. `review-r0` is the baseline (feature start), `review-r1+` are review checkpoints |
| Comment | A threaded discussion attached to a file:line at a specific git ref |
| Status | Comment lifecycle: `open` → `pending-user` / `pending-agent` → `fixed` → `resolved` / `wontfix` |

## Comment Statuses

| Status | Meaning |
|--------|---------|
| `open` | New, needs work |
| `pending-user` | Waiting for user (Claude asked a question) |
| `pending-agent` | Waiting for Claude (user answered or provided context) |
| `fixed` | Claude claims fixed, needs user verification |
| `resolved` | User verified, done |
| `wontfix` | Not addressing |

## Data Structure

**File location:** `.review/comments.json` (gitignored)

```json
{
  "version": 1,
  "currentRound": "review-r1",
  "baseRef": "review-r0",
  "comments": [
    {
      "id": 1,
      "file": "src/Auth/LoginController.php",
      "line": 42,
      "ref": "review-r1",
      "status": "fixed",
      "resolveCommit": "abc1234",
      "thread": [
        {
          "author": "user",
          "text": "Extract validation to a form request",
          "at": "2024-01-23T10:00:00Z"
        },
        {
          "author": "agent",
          "text": "Fixed in abc1234",
          "at": "2024-01-23T10:30:00Z"
        }
      ]
    },
    {
      "id": 2,
      "file": "src/Auth/LoginController.php",
      "line": 87,
      "ref": "review-r1",
      "status": "pending-user",
      "resolveCommit": null,
      "thread": [
        {
          "author": "user",
          "text": "Handle the error case",
          "at": "2024-01-23T10:01:00Z"
        },
        {
          "author": "agent",
          "text": "Should this throw an exception or return null?",
          "at": "2024-01-23T10:31:00Z"
        }
      ]
    }
  ]
}
```

**Field notes:**
- `ref`: Git ref when comment was created (for viewing original context)
- `resolveCommit`: Set when Claude marks `fixed`
- `author`: Either `user` or `agent`

## CLI Tool

**Command:** `review` (Node.js, installed globally via npm)

### Commands

```bash
# List comments
review list
review list --status=open
review list --status=pending-agent

# Reply to a comment
review reply <id> "message"

# Mark as fixed (after committing the fix)
review fix <id> --commit <sha>

# View original context
review show <id>
```

### Output format for `review list`

```
#1 [fixed] src/Auth/LoginController.php:42 @review-r1
   "Extract validation to a form request"

#2 [pending-agent] src/Auth/LoginController.php:87 @review-r1
   "Handle the error case"
   └─ You answered: "Throw an exception"
```

## Plugin UI

### Tool Window Panel

```
┌─────────────────────────────────────────┐
│ Code Review                    [⟳] [+]  │
├─────────────────────────────────────────┤
│ Round: review-r1 (vs review-r0)         │
│ Progress: 3/5 resolved                  │
├─────────────────────────────────────────┤
│ ▼ Open (1)                              │
│   └─ LoginController.php:42             │
│      "Extract validation..."            │
│                                          │
│ ▼ Pending - You (1)                     │
│   └─ LoginController.php:87             │
│      "Should this throw...?"            │
│                                          │
│ ▼ Fixed - Verify (1)                    │
│   └─ UserService.php:23                 │
│      "Add null check" → abc123          │
│                                          │
│ ▶ Resolved (2)                          │
├─────────────────────────────────────────┤
│ [New Round]  [Complete Round]           │
└─────────────────────────────────────────┘
```

### Initial State (No Active Review)

```
┌─────────────────────────────────────────┐
│ Code Review                    [⟳] [+]  │
├─────────────────────────────────────────┤
│                                          │
│   No active review                       │
│                                          │
│   [Start Feature Development]            │
│                                          │
│   (Creates baseline tag, initializes     │
│    review folder)                        │
│                                          │
└─────────────────────────────────────────┘
```

### Editor Integration

- Gutter icons on lines with comments
- Click to open comment popup
- Colors: yellow (open), blue (pending), green (fixed), gray (resolved)

### Comment Popup

- Shows full thread
- Reply input at bottom
- Status buttons: [Resolve] [Won't Fix] [Reopen]
- [View Original] → opens diff at comment's ref

### Comment Actions

| Action | Who uses it | When |
|--------|-------------|------|
| Reply | Both | Add to thread |
| Resolve | User | Mark fixed after verifying |
| Reopen | User | If fix wasn't right |
| Jump to code | Both | Navigate to file:line |
| Delete | User | Remove accidentally added comment |

## Workflow

### Full Collaboration Flow

**Phase 1: Start Feature** *(PhpStorm)*
```
Click [Start Feature Development]
    ↓
Plugin: Creates .review/comments.json (empty)
        Creates tag review-r0
        Panel switches to active state
```

**Phase 2: Development** *(Terminal)*
```
User: "Implement user authentication"
    ↓
Claude: Implements feature, commits each task
    ↓
Claude: "Done. Ready for review."
```

**Phase 3: Start Review** *(PhpStorm)*
```
Click [New Review Round]
    ↓
Plugin: Creates tag review-r1
        Opens diff view (review-r0..review-r1)
    ↓
User: Browse files, click gutter to add comments
    ↓
Plugin: Saves comments to .review/comments.json
```

**Phase 4: Fixes** *(Terminal)*
```
User: "Fix my review comments"
    ↓
Claude: cat .review/comments.json
    ↓
Claude: Processes ALL comments in one pass:

    #1: Clear → fix, commit, review fix 1 --commit abc
    #2: Clear → fix, commit, review fix 2 --commit abc
    #3: Unclear → review reply 3 "Private method or service?"
    #4: Clear → fix, commit, review fix 4 --commit def
    #5: Unclear → review reply 5 "Should this validate nulls?"
    ↓
Claude: "Done. 3 fixed, 2 need your input. Check plugin when ready."
```

**Phase 5: Answer Questions** *(PhpStorm)*
```
User: See 2 pending-user items
    ↓
Reply to #3: "Private method"
Reply to #5: "Yes, validate"
```

**Phase 6: Second Pass** *(Terminal)*
```
User: "Answered your questions"
    ↓
Claude: Reads answers, fixes #3 and #5
    ↓
Claude: "All done. Ready for verification."
```

**Phase 7: Verification** *(PhpStorm)*
```
Click [New Round]
    ↓
Plugin: Creates tag review-r2
        Shows diff review-r1..review-r2 (only the fixes)
    ↓
User: Review each fix:
    ✓ Good → Click [Resolve]
    ✗ Not good → Add reply → status: pending-agent
```

**Phase 8: Complete**
```
All comments: resolved or wontfix
    ↓
Plugin: Shows "Review complete ✓"
    ↓
User: Merge to main
    ↓
Tags review-r0, review-r1, review-r2 remain as history
```

### Tag Timeline

```
main ────●─────────────────────────────────────►
          \
           ●──────●──●──●──●──●────────► feature branch
           ↑             ↑        ↑
        review-r0    review-r1  review-r2
        (start)      (review)   (verify)
```

## Technical Implementation

### Plugin Stack
- Kotlin
- IntelliJ Platform SDK
- Gradle with `org.jetbrains.intellij` plugin

### Key Components

| Component | Responsibility |
|-----------|----------------|
| `ReviewService` | Manages JSON state, round logic |
| `ReviewToolWindow` | Panel UI, comment list |
| `ReviewGutterProvider` | Editor gutter icons |
| `ReviewPopup` | Inline comment thread UI |
| `GitService` | Tag creation, diff generation |

### CLI Tool Stack
- Node.js
- Simple file-based - reads/writes `.review/comments.json`
- No server, no dependencies beyond standard npm packages

### File Structure

```
.review/
  comments.json     ← gitignored

.gitignore
  .review/
```

### IDE Compatibility
- PhpStorm, IntelliJ IDEA, WebStorm (all IntelliJ-based)
- Minimum version: 2023.1+

## Future Enhancements (Not in MVP)

- Worktree integration in [Start Feature Development]
- Plugin-triggered Claude commands
- Comment export/reporting
- Multiple reviewer support
