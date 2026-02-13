# Review Duet

Local code review workflow tool for AI-assisted development with Claude Code.

## Architecture

- **Plugin** (`plugin/`) - JetBrains/IntelliJ plugin in Kotlin
- **CLI** (`cli/`) - TypeScript CLI for Claude Code communication
- **Skill** (`skills/`) - Claude Code skill for processing comments

## Data Flow

Plugin writes comments to `.review-duet/{branch}.json` → CLI reads/updates → Claude processes via skill

## Development

```bash
# CLI
cd cli && npm install && npm run build && npm link

# Run tests
cd cli && npm test

# Plugin
cd plugin && ./gradlew runIde
```

## CLI Commands

- `review-duet list` - List open comments
- `review-duet show <id>` - Show comment details
- `review-duet fix <id> <explanation>` - Mark comment as fixed
- `review-duet accept` - Accept current review state

## Plugin: Process with Claude

The sidebar "Process with Claude" button launches `claude -p` as a subprocess to process review comments.

Key implementation details:
- **Service**: `ClaudeProcessService` — project-level `@Service`, tracks running processes per repo in a `Map<String, RunEntry>`
- **Command**: `claude -p "/review-duet:process --review=<path>" --output-format stream-json --verbose`
- **stdin must be closed** after `handler.startNotify()` — otherwise `claude -p` hangs waiting for piped input
- **Result parsing**: collects stdout, finds `{"type":"result","result":"..."}` JSON line, appends parsed text to console as `--- Result ---`
- **Notification**: balloon with "Show Output" action that opens Run tool window via `ToolWindowManager`
- **Lazy cleanup**: `runs` entries are not removed on process exit (needed for `focusRunTab`); `isRunning()` cleans up terminated entries on next check
- **Multi-repo**: each repo gets its own Run tab named "Review Duet — {repoName}"

## Testing

- CLI: `npm test` (vitest)
- Plugin: `./gradlew test`
