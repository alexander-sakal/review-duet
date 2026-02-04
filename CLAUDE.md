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

## Testing

- CLI: `npm test` (vitest)
- Plugin: `./gradlew test`
