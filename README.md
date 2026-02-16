# Review Duet

A JetBrains plugin + CLI for local code review workflows with AI-assisted development.

## Overview

Enable structured review rounds with comment tracking and status management for AI-assisted development workflows. Supports multi-repo projects.

## Components

- **Plugin** (`plugin/`) - IntelliJ/PhpStorm plugin for review UI
- **CLI** (`cli/`) - Command-line interface for agent communication
- **Skill** (`skills/`) - Claude Code skill for processing comments (optional, for Claude Code users)

## How It Works

**Plugin** writes comments to `.review-duet/{branch}.json` and displays review state.

**CLI** (`review-duet`) provides commands for AI agents to read comments, mark them fixed, and update the baseline commit.

**Agent Processing** - The plugin supports multiple AI agents (Claude Code, Codex CLI, etc.) via a dropdown in the toolbar. Each agent processes comments using the CLI.

**Skill** (`/review-duet:process`) is a Claude Code skill that instructs Claude to autonomously process open comments. Other agents receive equivalent instructions via their command-line interface.

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

### Processing Comments

Select your AI agent from the dropdown in the Review Duet toolbar and click "Process". The agent will:
1. Read each open comment
2. Understand the requested change
3. Make the fix (checking for similar issues elsewhere)
4. Commit the change
5. Mark the comment as fixed with an explanation

**Claude Code users** can also run `/review-duet:process` directly in the terminal.

## Workflow

1. **Start Review** - Select base commit in plugin
2. **Develop** - Claude implements features
3. **Review** - Add comments in IDE on changed lines
4. **Process** - Click "Process" in the plugin (or run `/review-duet:process` in Claude Code)
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
