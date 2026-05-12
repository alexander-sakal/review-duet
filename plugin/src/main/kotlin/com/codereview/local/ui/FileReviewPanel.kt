package com.codereview.local.ui

import com.codereview.local.agents.AgentRegistry
import com.codereview.local.model.FileReview
import com.codereview.local.model.FileReviewData
import com.codereview.local.model.FileComment
import com.codereview.local.model.FileCommentStatus
import com.codereview.local.services.AgentProcessService
import com.codereview.local.services.FileReviewService
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.nio.file.Path
import javax.swing.DefaultListModel
import javax.swing.JButton

class FileReviewPanel(private val project: Project) : JBPanel<FileReviewPanel>(BorderLayout()) {

    private val reviewsListModel = DefaultListModel<Path>()
    private val reviewsList = JBList(reviewsListModel)
    private val commentsListModel = DefaultListModel<FileComment>()
    private val commentsList = JBList(commentsListModel)
    private val headerLabel = JBLabel("No file review selected")
    private var current: Pair<FileReview, FileReviewData>? = null

    init {
        layout = BorderLayout()
        add(buildHeader(), BorderLayout.NORTH)
        add(buildBody(), BorderLayout.CENTER)
        add(buildFooter(), BorderLayout.SOUTH)

        reviewsList.addListSelectionListener {
            val selected = reviewsList.selectedValue ?: return@addListSelectionListener
            showReview(selected)
        }
    }

    private fun buildHeader(): JBPanel<*> = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        add(headerLabel, BorderLayout.CENTER)
    }

    private fun buildBody(): JBPanel<*> = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        val split = javax.swing.JSplitPane(
            javax.swing.JSplitPane.HORIZONTAL_SPLIT,
            JBScrollPane(reviewsList),
            JBScrollPane(commentsList)
        )
        split.dividerLocation = 200
        add(split, BorderLayout.CENTER)
    }

    private fun buildFooter(): JBPanel<*> = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT)).apply {
        val process = JButton("Process with agent").also { btn ->
            btn.addActionListener { onProcess() }
        }
        val accept = JButton("Accept & close").also { btn ->
            btn.addActionListener { onAcceptAndClose() }
        }
        add(process)
        add(accept)
    }

    fun openReview(targetFile: Path) {
        if (!reviewsListModel.contains(targetFile)) reviewsListModel.addElement(targetFile)
        reviewsList.setSelectedValue(targetFile, true)
        showReview(targetFile)
    }

    private fun showReview(targetFile: Path) {
        val review = FileReviewService.getInstance(project).openOrCreate(targetFile)
        val data = try {
            review.refresh(review.loadOrInit())
        } catch (e: Exception) {
            renderError("Could not load review for ${targetFile.fileName}: ${e.message}")
            return
        }
        if (!targetFile.toFile().exists()) {
            renderError("File no longer exists: ${targetFile.fileName}. Use Accept & close to discard.")
            current = review to data
            return
        }
        current = review to data
        headerLabel.text = "${targetFile.fileName}  (${data.comments.count { it.status == FileCommentStatus.OPEN }} open)"
        commentsListModel.clear()
        data.comments.forEach { commentsListModel.addElement(it) }
    }

    private fun renderError(message: String) {
        headerLabel.text = message
        commentsListModel.clear()
    }

    private fun onProcess() {
        val (review, data) = current ?: return
        val provider = AgentRegistry.getAll().firstOrNull() ?: return
        AgentProcessService.getInstance(project).runProcess(
            workingDir = project.basePath ?: return,
            reviewFilePath = review.storageFile.toString(),
            provider = provider,
            skillFolder = "review-duet:process-file",
            onFinished = { showReview(Path.of(data.filePath)) }
        )
    }

    private fun onAcceptAndClose() {
        val (review, data) = current ?: return
        FileReviewService.getInstance(project).close(Path.of(data.filePath))
        val target = Path.of(data.filePath)
        reviewsListModel.removeElement(target)
        commentsListModel.clear()
        headerLabel.text = "No file review selected"
        current = null
    }
}
