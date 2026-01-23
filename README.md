# Code Review Local

A PhpStorm/IntelliJ plugin + CLI for local code review workflows when developing with Claude Code.

## Overview

Enable structured review rounds with comment tracking, threaded discussions, and status management for AI-assisted development workflows.

## Components

- **Plugin** (`plugin/`) - IntelliJ/PhpStorm plugin for review UI
- **CLI** (`cli/`) - Command-line tool for Claude to interact with reviews

## Quick Start

### Install CLI

```bash
cd cli && npm install && npm link
```

### Install Plugin

1. Build: `cd plugin && ./gradlew build`
2. Install from disk in IDE: Settings → Plugins → Install from disk
3. Select `plugin/build/distributions/code-review-local-*.zip`

## Development

### Docker (CLI only)

```bash
docker compose up cli
```

### Local

```bash
# CLI
cd cli && npm install && npm run dev

# Plugin
cd plugin && ./gradlew runIde
```

## Workflow

1. **Start Feature** - Click "Start Feature Development" in plugin
2. **Develop** - Claude implements features
3. **Review** - Click "New Round", add comments in IDE
4. **Fix** - Claude runs `review list` and processes comments
5. **Verify** - Review fixes, mark resolved or reopen

## License

MIT
