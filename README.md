# Review Duet

A JetBrains plugin + CLI for local code review workflows when developing with Claude Code.

## Overview

Enable structured review rounds with comment tracking and status management for AI-assisted development workflows. Supports multi-repo projects.

## Components

- **Plugin** (`plugin/`) - IntelliJ/PhpStorm plugin for review UI
- **CLI** (`cli/`) - Command-line tool (`review-duet`) for Claude to interact with reviews

## Quick Start

### Install CLI

```bash
cd cli && npm install && npm link
```

### Install Plugin

1. Build: `cd plugin && ./gradlew build`
2. Install from disk in IDE: Settings → Plugins → Install from disk
3. Select `plugin/build/distributions/code-review-local-*.zip`

## Usage

### Plugin

1. Open the "Code Review" tool window
2. Select repository (if multi-repo project)
3. Choose base commit to review changes from
4. Click "Start Review"
5. Add comments via gutter icons or right-click in editor
6. View changes and comments in the panel

### CLI

```bash
# List all comments
review-duet list

# List open comments only
review-duet list --status=open

# Show comment details
review-duet show <id>

# Mark comment as fixed
review-duet fix <id> --commit <sha> --message "Explanation"

# Accept changes and move baseline to HEAD
review-duet accept

# Use explicit review file (for multi-repo)
review-duet list --review=/path/to/.review-duet/branch.json
```

### Claude Code Skill

Use `/review-duet:process` in Claude Code to automatically process all open comments.

## File Structure

Reviews are stored in `.review-duet/{branch}.json` at the repository root.

## Development

### Local

```bash
# CLI
cd cli && npm install && npm test

# Plugin
cd plugin && ./gradlew runIde
```

## Workflow

1. **Start Review** - Select base commit in plugin
2. **Develop** - Claude implements features
3. **Review** - Add comments in IDE on changed lines
4. **Process** - Run `/review-duet:process` in Claude Code
5. **Verify** - Review fixes, resolve or reopen comments
6. **Repeat** - Continue until satisfied

## License

MIT
