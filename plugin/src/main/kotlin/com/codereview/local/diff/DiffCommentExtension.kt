package com.codereview.local.diff

import com.codereview.local.actions.AddCommentDialog
import com.codereview.local.services.ReviewService
import com.intellij.diff.DiffContext
import com.intellij.diff.DiffExtension
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.ui.Messages
import java.nio.file.Path
import javax.swing.Icon

class DiffCommentExtension : DiffExtension() {

    override fun onViewerCreated(viewer: FrameDiffTool.DiffViewer, context: DiffContext, request: DiffRequest) {
        if (viewer !is TwosideTextDiffViewer) return

        val project = context.project ?: return
        val basePath = project.basePath ?: return

        // Get the "new" side editor (right side - the changes)
        val editor = viewer.editor2
        val title = request.title ?: return

        // Extract file path from title (format: "path/to/file.kt (review-r0 → review-r1)")
        val filePath = title.substringBefore(" (")

        setupGutterComments(editor, filePath, basePath, project)
    }

    private fun setupGutterComments(editor: Editor, filePath: String, basePath: String, project: com.intellij.openapi.project.Project) {
        var currentHighlighter: RangeHighlighter? = null
        var currentLine: Int = -1

        // Show "+" icon on hover
        editor.addEditorMouseMotionListener(object : EditorMouseMotionListener {
            override fun mouseMoved(e: EditorMouseEvent) {
                val line = e.logicalPosition.line

                // Remove old highlighter if line changed
                if (line != currentLine) {
                    currentHighlighter?.let {
                        editor.markupModel.removeHighlighter(it)
                    }
                    currentHighlighter = null
                    currentLine = -1
                }

                // Show icon when hovering on any line
                if (line != currentLine && line >= 0 && line < editor.document.lineCount) {
                    currentLine = line
                    val startOffset = editor.document.getLineStartOffset(line)
                    val endOffset = editor.document.getLineEndOffset(line)

                    currentHighlighter = editor.markupModel.addRangeHighlighter(
                        startOffset,
                        endOffset,
                        HighlighterLayer.LAST,
                        null,
                        HighlighterTargetArea.LINES_IN_RANGE
                    ).apply {
                        gutterIconRenderer = AddCommentGutterIcon(project, filePath, line + 1, basePath)
                    }
                }
            }
        })

        // Remove highlighter when mouse leaves
        editor.addEditorMouseListener(object : EditorMouseListener {
            override fun mouseExited(e: EditorMouseEvent) {
                currentHighlighter?.let {
                    editor.markupModel.removeHighlighter(it)
                }
                currentHighlighter = null
                currentLine = -1
            }
        })
    }

    private class AddCommentGutterIcon(
        private val project: com.intellij.openapi.project.Project,
        private val filePath: String,
        private val line: Int,
        private val basePath: String
    ) : GutterIconRenderer() {

        override fun getIcon(): Icon = AllIcons.General.Add

        override fun getTooltipText(): String = "Add review comment"

        override fun getClickAction(): AnAction = object : AnAction() {
            override fun actionPerformed(e: AnActionEvent) {
                val reviewService = ReviewService(Path.of(basePath))

                if (!reviewService.hasActiveReview()) {
                    Messages.showWarningDialog(
                        project,
                        "No active review. Start feature development first from the Code Review panel.",
                        "No Active Review"
                    )
                    return
                }

                val dialog = AddCommentDialog(project, filePath, line)
                if (dialog.showAndGet()) {
                    val commentText = dialog.getCommentText()
                    if (commentText.isNotBlank()) {
                        reviewService.addComment(filePath, line, commentText)
                        Messages.showInfoMessage(
                            project,
                            "Comment added to $filePath:$line",
                            "Comment Added"
                        )
                    }
                }
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AddCommentGutterIcon) return false
            return filePath == other.filePath && line == other.line
        }

        override fun hashCode(): Int {
            return 31 * filePath.hashCode() + line
        }
    }
}
