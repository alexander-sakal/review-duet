# Agent Provider Abstraction — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Decouple the plugin from Claude Code so any CLI-based AI agent can process review comments.

**Architecture:** Introduce an `AgentProvider` interface + `AgentRegistry` object. Each agent (Claude, Codex, etc.) implements the interface with its own command building and result parsing. `ClaudeProcessService` becomes `AgentProcessService` that delegates to the selected provider. UI gets an agent dropdown next to the Process button.

**Tech Stack:** Kotlin, IntelliJ Platform SDK, Gson (already a dependency)

---

### Task 1: Create AgentProvider Interface

**Files:**
- Create: `plugin/src/main/kotlin/com/codereview/local/agents/AgentProvider.kt`

**Step 1: Create the interface file**

```kotlin
package com.codereview.local.agents

import com.intellij.execution.configurations.GeneralCommandLine

interface AgentProvider {
    val id: String
    val displayName: String
    fun buildCommand(workingDir: String, reviewFilePath: String): GeneralCommandLine
    fun parseResult(outputLines: List<String>): String?
    fun shouldCloseStdin(): Boolean
}
```

**Step 2: Commit**

```bash
cd plugin && git add src/main/kotlin/com/codereview/local/agents/AgentProvider.kt && git commit -m "$(cat <<'EOF'
feat: add AgentProvider interface for agent-agnostic processing

Defines the contract for AI agent integrations: command building,
result parsing, and stdin behavior.

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Create AgentRegistry

**Files:**
- Create: `plugin/src/main/kotlin/com/codereview/local/agents/AgentRegistry.kt`

**Step 1: Create the registry**

```kotlin
package com.codereview.local.agents

object AgentRegistry {
    private val providers = mutableListOf<AgentProvider>()

    fun register(provider: AgentProvider) {
        providers.removeAll { it.id == provider.id }
        providers.add(provider)
    }

    fun getAll(): List<AgentProvider> = providers.toList()

    fun getById(id: String): AgentProvider? = providers.find { it.id == id }

    fun getDefault(): AgentProvider = providers.first()
}
```

**Step 2: Commit**

```bash
cd plugin && git add src/main/kotlin/com/codereview/local/agents/AgentRegistry.kt && git commit -m "$(cat <<'EOF'
feat: add AgentRegistry for managing available agent providers

Simple singleton registry. Providers register at init time.

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Create ClaudeAgentProvider

**Files:**
- Create: `plugin/src/main/kotlin/com/codereview/local/agents/ClaudeAgentProvider.kt`

Extract the command building and result parsing logic from `ClaudeProcessService` into this provider.

**Step 1: Create the provider**

```kotlin
package com.codereview.local.agents

import com.google.gson.JsonParser
import com.intellij.execution.configurations.GeneralCommandLine
import java.io.File

class ClaudeAgentProvider : AgentProvider {
    override val id = "claude"
    override val displayName = "Claude Code"

    override fun buildCommand(workingDir: String, reviewFilePath: String): GeneralCommandLine {
        return GeneralCommandLine(
            "claude", "-p",
            "/review-duet:process --review=$reviewFilePath",
            "--output-format", "stream-json",
            "--verbose"
        )
            .withWorkDirectory(File(workingDir))
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
    }

    override fun parseResult(outputLines: List<String>): String? {
        val fullOutput = outputLines.joinToString("")
        for (line in fullOutput.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            try {
                val json = JsonParser.parseString(trimmed).asJsonObject
                if (json.get("type")?.asString == "result") {
                    return json.get("result")?.asString
                }
            } catch (_: Exception) {
                // Skip malformed lines
            }
        }
        return null
    }

    override fun shouldCloseStdin(): Boolean = true
}
```

**Step 2: Commit**

```bash
cd plugin && git add src/main/kotlin/com/codereview/local/agents/ClaudeAgentProvider.kt && git commit -m "$(cat <<'EOF'
feat: extract Claude-specific logic into ClaudeAgentProvider

Moves command building (claude -p with stream-json) and result parsing
from ClaudeProcessService into a dedicated AgentProvider implementation.

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Create CodexAgentProvider (example)

**Files:**
- Create: `plugin/src/main/kotlin/com/codereview/local/agents/CodexAgentProvider.kt`

This is an example provider to validate the abstraction works. The command structure is based on the Codex CLI interface.

**Step 1: Create the provider**

```kotlin
package com.codereview.local.agents

import com.intellij.execution.configurations.GeneralCommandLine
import java.io.File

class CodexAgentProvider : AgentProvider {
    override val id = "codex"
    override val displayName = "Codex CLI"

    override fun buildCommand(workingDir: String, reviewFilePath: String): GeneralCommandLine {
        return GeneralCommandLine(
            "codex", "--quiet",
            "Process review comments from $reviewFilePath. " +
                "Use the review-duet CLI to list comments (review-duet list --review=$reviewFilePath --status=open), " +
                "fix them, and mark as done (review-duet fix <id> --commit <sha> --review=$reviewFilePath --message \"...\")."
        )
            .withWorkDirectory(File(workingDir))
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
    }

    override fun parseResult(outputLines: List<String>): String? {
        // Codex outputs plain text — return last non-empty lines as the summary
        val text = outputLines.joinToString("").trim()
        return text.ifEmpty { null }
    }

    override fun shouldCloseStdin(): Boolean = false
}
```

**Step 2: Commit**

```bash
cd plugin && git add src/main/kotlin/com/codereview/local/agents/CodexAgentProvider.kt && git commit -m "$(cat <<'EOF'
feat: add CodexAgentProvider as example agent implementation

Demonstrates the AgentProvider interface with Codex CLI integration.

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: Rename ClaudeProcessService to AgentProcessService

**Files:**
- Modify: `plugin/src/main/kotlin/com/codereview/local/services/ClaudeProcessService.kt` (rename to `AgentProcessService.kt`)

Refactor the service to use `AgentProvider` instead of hardcoded Claude logic.

**Step 1: Rename the file**

```bash
cd plugin && git mv src/main/kotlin/com/codereview/local/services/ClaudeProcessService.kt src/main/kotlin/com/codereview/local/services/AgentProcessService.kt
```

**Step 2: Rewrite the service**

Replace the full contents of `AgentProcessService.kt` with:

```kotlin
package com.codereview.local.services

import com.codereview.local.agents.AgentProvider
import com.codereview.local.agents.AgentRegistry
import com.codereview.local.agents.ClaudeAgentProvider
import com.codereview.local.agents.CodexAgentProvider
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ColoredProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindowManager
import java.io.File

@Service(Service.Level.PROJECT)
class AgentProcessService(private val project: Project) {

    companion object {
        fun getInstance(project: Project): AgentProcessService {
            return project.getService(AgentProcessService::class.java)
        }

        // Register built-in providers once
        init {
            AgentRegistry.register(ClaudeAgentProvider())
            AgentRegistry.register(CodexAgentProvider())
        }
    }

    private data class RunEntry(
        val processHandler: ColoredProcessHandler,
        val descriptor: RunContentDescriptor,
        val onFinished: (() -> Unit)?
    )

    private val runs = mutableMapOf<String, RunEntry>()

    fun isRunning(workingDir: String): Boolean {
        val entry = runs[workingDir] ?: return false
        if (entry.processHandler.isProcessTerminated) {
            runs.remove(workingDir)
            return false
        }
        return true
    }

    fun runProcess(
        workingDir: String,
        reviewFilePath: String,
        provider: AgentProvider,
        onFinished: (() -> Unit)? = null
    ) {
        if (isRunning(workingDir)) {
            focusRunTab(workingDir)
            return
        }

        val repoName = File(workingDir).name
        val tabName = "Review Duet — $repoName"

        val commandLine = provider.buildCommand(workingDir, reviewFilePath)
        val handler = ColoredProcessHandler(commandLine)
        val console = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
        console.attachToProcess(handler)

        val outputLines = mutableListOf<String>()

        handler.addProcessListener(object : ProcessAdapter() {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                if (ProcessOutputType.isStdout(outputType)) {
                    outputLines.add(event.text)
                }
            }

            override fun processTerminated(event: ProcessEvent) {
                val result = provider.parseResult(outputLines)
                if (result != null) {
                    ApplicationManager.getApplication().invokeLater {
                        console.print("\n\n--- Result ---\n", ConsoleViewContentType.SYSTEM_OUTPUT)
                        console.print(result, ConsoleViewContentType.NORMAL_OUTPUT)
                        console.print("\n", ConsoleViewContentType.NORMAL_OUTPUT)
                    }
                }

                ApplicationManager.getApplication().invokeLater {
                    onFinished?.invoke()
                }
            }
        })

        val descriptor = RunContentDescriptor(console, handler, console.component, tabName).apply {
            isActivateToolWindowWhenAdded = false
        }

        runs[workingDir] = RunEntry(handler, descriptor, onFinished)

        val executor = DefaultRunExecutor.getRunExecutorInstance()
        RunContentManager.getInstance(project).showRunContent(executor, descriptor)

        handler.startNotify()

        if (provider.shouldCloseStdin()) {
            handler.processInput?.close()
        }
    }

    fun focusRunTab(workingDir: String) {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Run")
        toolWindow?.show()

        val entry = runs[workingDir] ?: return
        val executor = DefaultRunExecutor.getRunExecutorInstance()
        RunContentManager.getInstance(project).selectRunContent(entry.descriptor)
    }
}
```

**Step 3: Verify it compiles**

```bash
cd plugin && ./gradlew compileKotlin
```

Expected: FAIL — `ReviewPanel.kt` still references `ClaudeProcessService`. That's fixed in Task 6.

**Step 4: Commit**

```bash
cd plugin && git add -A && git commit -m "$(cat <<'EOF'
refactor: rename ClaudeProcessService to AgentProcessService

Service now delegates to AgentProvider for command building, result
parsing, and stdin behavior. Process lifecycle management unchanged.

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: Update ReviewPanel for Agent Selection

**Files:**
- Modify: `plugin/src/main/kotlin/com/codereview/local/ui/ReviewPanel.kt`

**Step 1: Update imports**

Replace:
```kotlin
import com.codereview.local.services.ClaudeProcessService
```

With:
```kotlin
import com.codereview.local.agents.AgentProvider
import com.codereview.local.agents.AgentRegistry
import com.codereview.local.services.AgentProcessService
import com.intellij.ide.util.PropertiesComponent
```

**Step 2: Add agent combo box field**

After `private var processButton: JButton? = null` (line 44), add:

```kotlin
private var agentComboBox: JComboBox<AgentProvider>? = null
```

**Step 3: Add helper to get selected agent**

Add a method to the class:

```kotlin
private fun getSelectedAgent(): AgentProvider {
    return agentComboBox?.selectedItem as? AgentProvider ?: AgentRegistry.getDefault()
}

private fun saveSelectedAgent(provider: AgentProvider) {
    PropertiesComponent.getInstance(project).setValue("reviewDuet.selectedAgentId", provider.id)
}

private fun loadSelectedAgentId(): String? {
    return PropertiesComponent.getInstance(project).getValue("reviewDuet.selectedAgentId")
}
```

**Step 4: Update button panel in `showActiveReviewPanel`**

Replace the button panel section (lines 354-376) with:

```kotlin
val agentService = AgentProcessService.getInstance(project)
val buttonPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
    add(JSeparator(), BorderLayout.NORTH)
    val buttonsContainer = JBPanel<JBPanel<*>>().apply {
        layout = java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0)
        border = JBUI.Borders.empty(8, 0, 0, 0)

        // Agent dropdown
        val agents = AgentRegistry.getAll()
        val agentCombo = JComboBox(agents.toTypedArray()).apply {
            renderer = object : ListCellRenderer<AgentProvider> {
                private val defaultRenderer = javax.swing.DefaultListCellRenderer()
                override fun getListCellRendererComponent(
                    list: JList<out AgentProvider>?,
                    value: AgentProvider?,
                    index: Int,
                    isSelected: Boolean,
                    cellHasFocus: Boolean
                ): Component {
                    return defaultRenderer.getListCellRendererComponent(
                        list, value?.displayName ?: "", index, isSelected, cellHasFocus
                    )
                }
            }
            // Restore saved selection
            val savedId = loadSelectedAgentId()
            if (savedId != null) {
                val saved = agents.find { it.id == savedId }
                if (saved != null) selectedItem = saved
            }
            addActionListener {
                val selected = selectedItem as? AgentProvider ?: return@addActionListener
                saveSelectedAgent(selected)
            }
        }
        agentComboBox = agentCombo
        add(agentCombo)

        val running = agentService.isRunning(selectedRepoPath.toString())
        val processBtn = JButton(if (running) "Processing..." else "Process").apply {
            isEnabled = !running
            addActionListener { processWithAgent() }
        }
        processButton = processBtn
        add(processBtn)
        add(JButton("Refresh").apply {
            addActionListener { refresh() }
        })
        add(JButton("End Review").apply {
            addActionListener { endReview() }
        })
    }
    add(buttonsContainer, BorderLayout.CENTER)
}
```

**Step 5: Rename `processWithClaude` to `processWithAgent` and update**

Replace the entire `processWithClaude()` method with:

```kotlin
private fun processWithAgent() {
    val service = AgentProcessService.getInstance(project)
    if (service.isRunning(selectedRepoPath.toString())) {
        return
    }

    processButton?.text = "Processing..."
    processButton?.isEnabled = false

    val repoPath = selectedRepoPath.toString()
    val agent = getSelectedAgent()
    service.runProcess(
        workingDir = repoPath,
        reviewFilePath = review.filePath.toString(),
        provider = agent,
        onFinished = {
            refresh()
            com.intellij.notification.NotificationGroupManager.getInstance()
                .getNotificationGroup("Review Duet")
                .createNotification(
                    "Review Duet",
                    "Comments processing finished",
                    com.intellij.notification.NotificationType.INFORMATION
                )
                .addAction(object : com.intellij.notification.NotificationAction("Show Output") {
                    override fun actionPerformed(
                        e: com.intellij.openapi.actionSystem.AnActionEvent,
                        notification: com.intellij.notification.Notification
                    ) {
                        service.focusRunTab(repoPath)
                        notification.expire()
                    }
                })
                .notify(project)
        }
    )
}
```

**Step 6: Verify it compiles**

```bash
cd plugin && ./gradlew compileKotlin
```

Expected: SUCCESS

**Step 7: Commit**

```bash
cd plugin && git add -A && git commit -m "$(cat <<'EOF'
feat: add agent dropdown to review panel toolbar

Replaces "Process with Claude" button with agent selector dropdown
and generic "Process" button. Selection persisted via PropertiesComponent.

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 7: Update CLAUDE.md

**Files:**
- Modify: `CLAUDE.md`

**Step 1: Update the docs**

Replace the "## Plugin: Process with Claude" section with:

```markdown
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
```

Also update the Architecture section to mention agents:

```markdown
## Architecture

- **Plugin** (`plugin/`) - JetBrains/IntelliJ plugin in Kotlin
  - `agents/` - Agent provider interface and implementations (Claude, Codex, etc.)
  - `services/` - Agent process management, git operations
  - `ui/` - Review panel, comment list, diff integration
- **CLI** (`cli/`) - TypeScript CLI for review file operations
- **Skill** (`skills/`) - Claude Code skill for processing comments
```

Update the Data Flow section:

```markdown
## Data Flow

Plugin writes comments to `.review-duet/{branch}.json` → CLI reads/updates → Selected agent processes via its own mechanism
```

**Step 2: Commit**

```bash
git add CLAUDE.md && git commit -m "$(cat <<'EOF'
docs: update CLAUDE.md for agent-agnostic architecture

Documents AgentProvider interface, registry, and how to add new agents.

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 8: Build and Verify

**Step 1: Full build**

```bash
cd plugin && ./gradlew build
```

Expected: BUILD SUCCESSFUL

**Step 2: Run tests**

```bash
cd plugin && ./gradlew test
```

Expected: All existing tests pass (agent changes don't affect model/git tests)

**Step 3: Verify plugin loads in test IDE**

```bash
cd plugin && ./gradlew runIde
```

Manual check:
- Open a project with a `.review-duet/` review file
- Review Duet sidebar shows agent dropdown with "Claude Code" and "Codex CLI"
- Selecting an agent persists after closing/reopening the sidebar
- "Process" button works with Claude Code selected (if `claude` is in PATH)
