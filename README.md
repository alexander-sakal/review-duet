# Review Duet

A JetBrains plugin + CLI for local code review workflows when developing with Claude Code.

## Overview

Enable structured review rounds with comment tracking and status management for AI-assisted development workflows. Supports multi-repo projects.

## Components

- **Plugin** (`plugin/`) - IntelliJ/PhpStorm plugin for review UI
- **CLI** (`cli/`) - Command-line interface for agent communication
- **Skill** (`skills/`) - Claude Code skill for processing comments

## How It Works

**Plugin** writes comments to `.review-duet/{branch}.json` and displays review state.

**CLI** (`review-duet`) provides commands for Claude Code to read comments, mark them fixed, and update the baseline commit.

**Skill** (`/review-duet:process`) instructs Claude Code to autonomously process open comments - reading each one, making fixes, committing changes, and marking comments as fixed.

## Quick Start

### Install CLI

```bash
cd cli && npm install && npm run build && npm link
```

### Install Plugin

1. Build: `cd plugin && ./gradlew build`
2. Install from disk in IDE: Settings → Plugins → Install from disk
3. Select `plugin/build/distributions/code-review-local-*.zip`

### Install Claude Code Skill

```bash
ln -s $(pwd)/skills/review-duet:process ~/.claude/skills/review-duet:process
```

## Usage

### In the IDE

1. Open the "Code Review" tool window
2. Select repository (if multi-repo project)
3. Choose base commit to review changes from
4. Click "Start Review"
5. Add comments via gutter icons or right-click in editor
6. View changes and comments in the panel

### In Claude Code

Run `/review-duet:process` to automatically process all open comments. Claude will:
1. Read each open comment
2. Understand the requested change
3. Make the fix (checking for similar issues elsewhere)
4. Commit the change
5. Mark the comment as fixed with an explanation

## Workflow

1. **Start Review** - Select base commit in plugin
2. **Develop** - Claude implements features
3. **Review** - Add comments in IDE on changed lines
4. **Process** - Run `/review-duet:process` in Claude Code
5. **Verify** - Review fixes in IDE, resolve or reopen comments
6. **Repeat** - Continue until satisfied

## Development

```bash
# CLI
cd cli && npm install && npm test

# Plugin
cd plugin && ./gradlew runIde
```

## License

MIT
