# Agent Provider Abstraction

Make the plugin agent-agnostic so it can work with any CLI-based AI agent, not just Claude Code.

## Problem

`ClaudeProcessService` hardcodes the Claude CLI command, output format, skill invocation, and stdin behavior. The UI says "Process with Claude". Adding a different agent requires rewriting the service.

## Design

### AgentProvider Interface

```kotlin
interface AgentProvider {
    val id: String                  // "claude", "codex"
    val displayName: String         // "Claude Code", "Codex CLI"
    fun buildCommand(workingDir: String, reviewFilePath: String): GeneralCommandLine
    fun parseResult(outputLines: List<String>): String?
    fun shouldCloseStdin(): Boolean
}
```

Each agent encapsulates its own:
- Command construction (binary name, flags, output format)
- Result parsing (stream-json for Claude, plain text for others)
- stdin behavior (Claude requires closing stdin)

### AgentRegistry

Simple object registry. Providers register at init time. No XML extension points (overkill for internal plugin).

```kotlin
object AgentRegistry {
    fun register(provider: AgentProvider)
    fun getAll(): List<AgentProvider>
    fun getById(id: String): AgentProvider?
    fun getDefault(): AgentProvider
}
```

### Concrete Providers

- `ClaudeAgentProvider` — extracts existing logic from `ClaudeProcessService`
- `CodexAgentProvider` — example implementation for OpenAI Codex CLI

### Service Refactoring

Rename `ClaudeProcessService` to `AgentProcessService`. The service becomes agent-agnostic:
- Accepts the selected `AgentProvider` via a property or parameter
- Delegates command building to `provider.buildCommand()`
- Delegates result parsing to `provider.parseResult()`
- Conditionally closes stdin based on `provider.shouldCloseStdin()`
- Process lifecycle, console integration, and notifications remain unchanged

### UI Changes

Replace "Process with Claude" button with:
```
[Agent dropdown: Claude Code v] [Process] [Refresh] [End Review]
```

- `JComboBox<AgentProvider>` in the button bar
- Selection persisted via `PropertiesComponent` across restarts
- Button label is just "Process" (agent shown in dropdown)

### File Structure

```
plugin/src/main/kotlin/com/codereview/local/
├── agents/
│   ├── AgentProvider.kt
│   ├── AgentRegistry.kt
│   ├── ClaudeAgentProvider.kt
│   └── CodexAgentProvider.kt
├── services/
│   ├── AgentProcessService.kt    (renamed from ClaudeProcessService)
│   └── GitService.kt
├── ui/
│   └── ReviewPanel.kt            (updated)
```

### Documentation Updates

- CLAUDE.md — update architecture, data flow, process section
- Skills — update if Claude-specific references exist
