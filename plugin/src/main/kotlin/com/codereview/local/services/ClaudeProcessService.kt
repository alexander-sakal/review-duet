package com.codereview.local.services

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ColoredProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
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

        handler.addProcessListener(object : ProcessAdapter() {
            override fun processTerminated(event: ProcessEvent) {
                runs.remove(workingDir)
                ApplicationManager.getApplication().invokeLater {
                    onFinished?.invoke()
                }
            }
        })

        val console = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
        console.attachToProcess(handler)

        val descriptor = RunContentDescriptor(console, handler, console.component, tabName)

        runs[workingDir] = RunEntry(handler, descriptor, onFinished)

        val executor = DefaultRunExecutor.getRunExecutorInstance()
        RunContentManager.getInstance(project).showRunContent(executor, descriptor)

        handler.startNotify()

        // Close stdin so claude -p doesn't wait for piped input
        handler.processInput?.close()
    }

    private fun focusRunTab(workingDir: String) {
        val entry = runs[workingDir] ?: return
        val executor = DefaultRunExecutor.getRunExecutorInstance()
        RunContentManager.getInstance(project).showRunContent(executor, entry.descriptor)
    }
}
