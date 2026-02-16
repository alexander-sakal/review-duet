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

@Service(Service.Level.PROJECT)
class AgentProcessService(private val project: Project) {

    companion object {
        fun getInstance(project: Project): AgentProcessService {
            return project.getService(AgentProcessService::class.java)
        }

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

        val repoName = java.io.File(workingDir).name
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
