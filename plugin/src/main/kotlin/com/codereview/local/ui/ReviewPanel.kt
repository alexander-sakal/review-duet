package com.codereview.local.ui

import com.codereview.local.model.CommentStatus
import com.codereview.local.services.ReviewService
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.nio.file.Path
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.SwingConstants

class ReviewPanel(private val project: Project) : JBPanel<ReviewPanel>(BorderLayout()) {

    private val reviewService: ReviewService by lazy {
        val basePath = project.basePath ?: throw IllegalStateException("No project base path")
        ReviewService(Path.of(basePath))
    }

    init {
        border = JBUI.Borders.empty(10)
        refresh()
    }

    fun refresh() {
        removeAll()

        if (reviewService.hasActiveReview()) {
            showActiveReviewPanel()
        } else {
            showNoReviewPanel()
        }

        revalidate()
        repaint()
    }

    private fun showNoReviewPanel() {
        val centerPanel = JBPanel<JBPanel<*>>().apply {
            layout = GridBagLayout()

            val label = JBLabel("No active review").apply {
                horizontalAlignment = SwingConstants.CENTER
            }

            val button = JButton("Start Feature Development").apply {
                addActionListener { startFeatureDevelopment() }
            }

            val description = JBLabel("<html><center>Creates baseline tag and initializes<br/>review folder</center></html>").apply {
                foreground = java.awt.Color.GRAY
            }

            val gbc = GridBagConstraints().apply {
                gridx = 0
                gridy = 0
                insets = JBUI.insets(5)
            }
            add(label, gbc)

            gbc.gridy = 1
            add(button, gbc)

            gbc.gridy = 2
            add(description, gbc)
        }

        add(centerPanel, BorderLayout.CENTER)
    }

    private fun showActiveReviewPanel() {
        val data = reviewService.loadReviewData() ?: return

        // Header
        val headerPanel = JBPanel<JBPanel<*>>().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(0, 0, 10, 0)

            add(JBLabel("Round: ${data.currentRound} (vs ${data.baseRef})"))

            val resolvedCount = data.comments.count { it.status == CommentStatus.RESOLVED }
            val totalCount = data.comments.size
            add(JBLabel("Progress: $resolvedCount/$totalCount resolved"))
        }

        // Placeholder for comment list (will be added in Task 16)
        val placeholderLabel = JBLabel("Comments list coming soon...")

        // Action buttons
        val buttonPanel = JBPanel<JBPanel<*>>().apply {
            layout = java.awt.FlowLayout(java.awt.FlowLayout.LEFT)
            add(JButton("New Round").apply {
                addActionListener { startNewRound() }
            })
            add(JButton("Refresh").apply {
                addActionListener { refresh() }
            })
        }

        add(headerPanel, BorderLayout.NORTH)
        add(placeholderLabel, BorderLayout.CENTER)
        add(buttonPanel, BorderLayout.SOUTH)
    }

    private fun startFeatureDevelopment() {
        // Simple initialization without git for now (git integration in Task 20)
        reviewService.initializeReview("review-r0")
        refresh()
    }

    private fun startNewRound() {
        val data = reviewService.loadReviewData() ?: return
        val currentNum = data.currentRound.substringAfter("review-r").toIntOrNull() ?: 0
        val newRound = "review-r${currentNum + 1}"
        reviewService.startNewRound(newRound)
        refresh()
    }
}
