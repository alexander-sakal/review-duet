# Review Duet

Local code review workflow tool for AI-assisted development.

## Architecture

- **Plugin** (`plugin/`) - JetBrains/IntelliJ plugin in Kotlin
  - `agents/` - Agent provider interface and implementations (Claude, Codex, etc.)
  - `services/` - Agent process management, git operations
  - `ui/` - Review panel, comment list, diff integration
- **CLI** (`cli/`) - TypeScript CLI for review file operations
- **Skill** (`skills/`) - Claude Code skill for processing comments

## Data Flow

Plugin writes comments to `.review-duet/{branch}.json` → CLI reads/updates → Selected agent processes via its own mechanism

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

## Plugin: Agent Processing

The sidebar "Process" button launches a configured AI agent as a subprocess to process review comments.

Key implementation details:
- **Agent Interface**: `AgentProvider` — defines `buildCommand()`, `parseResult()`, `shouldCloseStdin()` per agent
- **Registry**: `AgentRegistry` — singleton holding all registered providers (Claude Code, Codex CLI, etc.)
- **Service**: `AgentProcessService` — project-level `@Service`, tracks running processes per repo in a `Map<String, RunEntry>`
- **Agent selection**: toolbar dropdown persisted via `PropertiesComponent`
- **Result parsing**: each agent provider parses its own output format
- **Notification**: balloon with "Show Output" action that opens Run tool window via `ToolWindowManager`
- **Lazy cleanup**: `runs` entries are not removed on process exit (needed for `focusRunTab`); `isRunning()` cleans up terminated entries on next check
- **Multi-repo**: each repo gets its own Run tab named "Review Duet — {repoName}"

### Adding a new agent

1. Create a class implementing `AgentProvider` in `plugin/src/main/kotlin/com/codereview/local/agents/`
2. Register it in `AgentProcessService.companion.init`
3. It appears automatically in the toolbar dropdown

## Testing

- CLI: `npm test` (vitest)
- Plugin: `./gradlew test`
