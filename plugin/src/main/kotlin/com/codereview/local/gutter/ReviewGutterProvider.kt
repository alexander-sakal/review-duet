package com.codereview.local.gutter

import com.codereview.local.model.CommentStatus
import com.codereview.local.model.Review
import com.codereview.local.services.GitService
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import javax.swing.Icon

class ReviewGutterProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        // Only process at file level for the first element on each line
        if (element.parent !is PsiFile) return null

        val project = element.project
        val file = element.containingFile?.virtualFile ?: return null

        // Find the repo that contains this file
        val (repoRoot, relativePath) = GitService.getRelativePath(project, file.path) ?: return null

        val review = Review.forCurrentBranch(repoRoot)
        if (!review.hasActiveReview()) return null

        val data = review.loadData() ?: return null

        // Find comments for this file and line
        val document = com.intellij.psi.PsiDocumentManager.getInstance(project)
            .getDocument(element.containingFile) ?: return null
        val lineNumber = document.getLineNumber(element.textRange.startOffset) + 1

        val comment = data.comments.find {
            it.file == relativePath && it.line == lineNumber
        } ?: return null

        val icon = CommentIcon(comment.status)
        val tooltipText = "#${comment.id}: ${comment.firstUserMessage?.take(50) ?: "Comment"}"

        return LineMarkerInfo(
            element,
            element.textRange,
            icon,
            { tooltipText },
            null,
            GutterIconRenderer.Alignment.LEFT,
            { tooltipText }
        )
    }

    private class CommentIcon(private val status: CommentStatus) : Icon {
        private val color: Color = when (status) {
            CommentStatus.OPEN -> JBColor.YELLOW
            CommentStatus.FIXED -> JBColor.GREEN
            CommentStatus.RESOLVED -> JBColor.GRAY
        }

        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            g.color = color
            g.fillOval(x + 2, y + 2, iconWidth - 4, iconHeight - 4)
            g.color = color.darker()
            g.drawOval(x + 2, y + 2, iconWidth - 4, iconHeight - 4)
        }

        override fun getIconWidth(): Int = 12
        override fun getIconHeight(): Int = 12
    }
}
