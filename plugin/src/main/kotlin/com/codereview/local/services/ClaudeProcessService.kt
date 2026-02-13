package com.codereview.local.services

import com.google.gson.JsonParser
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.configurations.GeneralCommandLine
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
class ClaudeProcessService(private val project: Project) {

    companion object {
        fun getInstance(project: Project): ClaudeProcessService {
            return project.getService(ClaudeProcessService::class.java)
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

    fun runProcess(workingDir: String, reviewFilePath: String, onFinished: (() -> Unit)? = null) {
        if (isRunning(workingDir)) {
            focusRunTab(workingDir)
            return
        }

        val repoName = File(workingDir).name
        val tabName = "Review Duet — $repoName"

        val commandLine = GeneralCommandLine(
            "claude", "-p",
            "/review-duet:process --review=$reviewFilePath",
            "--output-format", "stream-json",
            "--verbose"
        )
            .withWorkDirectory(File(workingDir))
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)

        val handler = ColoredProcessHandler(commandLine)
        val console = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
        console.attachToProcess(handler)

        // Collect stdout to parse the result event at the end
        val outputLines = mutableListOf<String>()

        handler.addProcessListener(object : ProcessAdapter() {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                if (ProcessOutputType.isStdout(outputType)) {
                    outputLines.add(event.text)
                }
            }

            override fun processTerminated(event: ProcessEvent) {
                // Parse the result and append human-readable summary to console
                val result = parseResult(outputLines)
                if (result != null) {
                    ApplicationManager.getApplication().invokeLater {
                        console.print("\n\n--- Result ---\n", ConsoleViewContentType.SYSTEM_OUTPUT)
                        console.print(result, ConsoleViewContentType.NORMAL_OUTPUT)
                        console.print("\n", ConsoleViewContentType.NORMAL_OUTPUT)
                    }
                }

                // Don't remove from runs here — focusRunTab needs the descriptor.
                // isRunning() handles cleanup lazily.
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

        // Close stdin so claude -p doesn't wait for piped input
        handler.processInput?.close()
    }

    private fun parseResult(outputLines: List<String>): String? {
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

    fun focusRunTab(workingDir: String) {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Run")
        toolWindow?.show()

        // Try to select the specific tab
        val entry = runs[workingDir] ?: return
        val executor = DefaultRunExecutor.getRunExecutorInstance()
        RunContentManager.getInstance(project).selectRunContent(entry.descriptor)
    }
}
