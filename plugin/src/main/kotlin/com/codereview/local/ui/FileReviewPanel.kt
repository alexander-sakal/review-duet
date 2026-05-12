package com.codereview.local.ui

import com.codereview.local.agents.AgentRegistry
import com.codereview.local.diff.FileReviewEditorCommentExtension
import com.codereview.local.model.FileReview
import com.codereview.local.model.FileReviewData
import com.codereview.local.model.FileComment
import com.codereview.local.model.FileCommentStatus
import com.codereview.local.services.AgentProcessService
import com.codereview.local.services.FileReviewService
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.messages.MessageBusConnection
import com.intellij.ui.PopupHandler
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.nio.file.Path
import javax.swing.JButton
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class FileReviewPanel(private val project: Project) : JBPanel<FileReviewPanel>(BorderLayout()) {

    private data class FileNode(val targetFile: Path, val review: FileReview, val data: FileReviewData) {
        override fun toString(): String {
            val open = data.comments.count { it.status == FileCommentStatus.OPEN }
            return "${targetFile.fileName}  ($open open)"
        }
    }

    private data class CommentNode(val parentFile: Path, val comment: FileComment) {
        override fun toString(): String {
            val statusPrefix = when (comment.status) {
                FileCommentStatus.OPEN -> ""
                FileCommentStatus.FIXED -> "[fixed] "
                FileCommentStatus.OUTDATED -> "[outdated] "
            }
            val snippet = if (comment.text.length > 40) comment.text.take(40) + "..." else comment.text
            return "${statusPrefix}#${comment.id} line ${comment.line}: $snippet"
        }
    }

    private val treeRoot = DefaultMutableTreeNode("root")
    private val treeModel = DefaultTreeModel(treeRoot)
    private val reviewTree = JTree(treeModel).apply {
        isRootVisible = false
        showsRootHandles = true
        cellRenderer = object : com.intellij.ui.ColoredTreeCellRenderer() {
            override fun customizeCellRenderer(
                tree: javax.swing.JTree,
                value: Any?,
                selected: Boolean,
                expanded: Boolean,
                leaf: Boolean,
                row: Int,
                hasFocus: Boolean
            ) {
                val node = value as? DefaultMutableTreeNode ?: return
                when (val obj = node.userObject) {
                    is FileNode -> {
                        val vfile = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                            .findFileByPath(obj.targetFile.toString())
                        icon = vfile?.let {
                            com.intellij.openapi.fileTypes.FileTypeRegistry.getInstance()
                                .getFileTypeByFile(it).icon
                        } ?: com.intellij.icons.AllIcons.FileTypes.Any_type
                        append(obj.toString())
                    }
                    is CommentNode -> {
                        icon = com.intellij.icons.AllIcons.General.Note
                        append(obj.toString())
                    }
                    else -> append(value?.toString() ?: "")
                }
            }
        }
    }

    private var current: Pair<FileReview, FileReviewData>? = null
    private var busConnection: MessageBusConnection? = null
    private val markersByCommentId = mutableMapOf<Int, RangeMarker>()
    private var trackedDocument: Document? = null
    private var documentListener: DocumentListener? = null

    init {
        layout = BorderLayout()
        add(buildBody(), BorderLayout.CENTER)
        add(buildFooter(), BorderLayout.SOUTH)

        reviewTree.addTreeSelectionListener {
            val path = reviewTree.selectionPath ?: return@addTreeSelectionListener
            val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return@addTreeSelectionListener
            val targetFile = when (val obj = node.userObject) {
                is FileNode -> obj.targetFile
                is CommentNode -> obj.parentFile
                else -> return@addTreeSelectionListener
            }
            focusReview(targetFile)
        }

        val filePopupGroup = DefaultActionGroup().apply {
            add(CopyReviewFilePathAction())
            addSeparator()
            add(CloseReviewAction())
        }
        PopupHandler.installFollowingSelectionTreePopup(reviewTree, filePopupGroup, "ReviewDuetFilePopup")

        busConnection = project.messageBus.connect().also { conn ->
            conn.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    val cur = current ?: return
                    val watchedPath = Path.of(cur.second.filePath).toAbsolutePath().toString()
                    if (events.any { it.path == watchedPath }) {
                        ApplicationManager.getApplication().invokeLater {
                            openReview(Path.of(cur.second.filePath))
                        }
                    }
                }
            })
        }
    }

    private fun buildBody(): JBPanel<*> = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        add(JBScrollPane(reviewTree), BorderLayout.CENTER)
    }

    private fun buildFooter(): JBPanel<*> = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT)).apply {
        val process = JButton("Process with agent").also { btn ->
            btn.addActionListener { onProcess() }
        }
        add(process)
    }

    fun addComment(targetFile: Path, line: Int, text: String) {
        val review = FileReviewService.getInstance(project).openOrCreate(targetFile)
        val data = review.loadOrInit()
        val vfile = LocalFileSystem.getInstance().findFileByPath(targetFile.toString())
        val doc = vfile?.let { FileDocumentManager.getInstance().getDocument(it) }
        val lineText = doc?.let {
            val idx = (line - 1).coerceIn(0, (it.lineCount - 1).coerceAtLeast(0))
            it.getText(com.intellij.openapi.util.TextRange(it.getLineStartOffset(idx), it.getLineEndOffset(idx)))
        } ?: ""
        val contextBefore = doc?.let { sliceLines(it, line - 2, line - 1) } ?: emptyList()
        val contextAfter = doc?.let { sliceLines(it, line, line + 1) } ?: emptyList()
        data.comments.add(
            FileComment(
                id = data.getNextCommentId(),
                line = line,
                lineText = lineText,
                contextBefore = contextBefore,
                contextAfter = contextAfter,
                text = text,
                status = FileCommentStatus.OPEN,
                createdAt = java.time.Instant.now().toString()
            )
        )
        review.save(data)
        FileReviewEditorCommentExtension.refreshInlaysForFile(project, targetFile)
        upsertFileNode(targetFile, review, data)
        selectFileNode(targetFile)
        current = review to data
    }

    fun editComment(targetFile: Path, commentId: Int, newText: String) {
        val review = FileReviewService.getInstance(project).openOrCreate(targetFile)
        val data = review.loadOrInit()
        val idx = data.comments.indexOfFirst { it.id == commentId }
        if (idx < 0) return
        data.comments[idx] = data.comments[idx].copy(text = newText)
        review.save(data)
        FileReviewEditorCommentExtension.refreshInlaysForFile(project, targetFile)
        upsertFileNode(targetFile, review, data)
        selectFileNode(targetFile)
    }

    fun deleteComment(targetFile: Path, commentId: Int) {
        val review = FileReviewService.getInstance(project).openOrCreate(targetFile)
        val data = review.loadOrInit()
        data.comments.removeAll { it.id == commentId }
        review.save(data)
        FileReviewEditorCommentExtension.refreshInlaysForFile(project, targetFile)
        upsertFileNode(targetFile, review, data)
        selectFileNode(targetFile)
    }

    private fun sliceLines(doc: com.intellij.openapi.editor.Document, fromLine: Int, toLine: Int): List<String> {
        val result = mutableListOf<String>()
        for (i in fromLine..toLine) {
            if (i in 0 until doc.lineCount) {
                result.add(doc.getText(com.intellij.openapi.util.TextRange(doc.getLineStartOffset(i), doc.getLineEndOffset(i))))
            }
        }
        return result
    }

    fun openReview(targetFile: Path) {
        if (!targetFile.toFile().exists()) {
            val review = FileReviewService.getInstance(project).openOrCreate(targetFile)
            val data = try { review.loadOrInit() } catch (_: Exception) { null }
            if (data != null) {
                upsertFileNode(targetFile, review, data)
                current = review to data
            }
            renderError("File no longer exists: ${targetFile.fileName}. Use Accept & close to discard.")
            return
        }
        detachMarkers()
        val review = FileReviewService.getInstance(project).openOrCreate(targetFile)
        val data = try {
            review.refresh(review.loadOrInit())
        } catch (e: Exception) {
            renderError("Could not load review for ${targetFile.fileName}: ${e.message}")
            return
        }
        upsertFileNode(targetFile, review, data)
        current = review to data
        selectFileNode(targetFile)
        attachMarkers(data)
    }

    private fun upsertFileNode(targetFile: Path, review: FileReview, data: FileReviewData) {
        val existingIdx = (0 until treeRoot.childCount).firstOrNull { i ->
            val n = treeRoot.getChildAt(i) as DefaultMutableTreeNode
            (n.userObject as? FileNode)?.targetFile == targetFile
        }
        val node = DefaultMutableTreeNode(FileNode(targetFile, review, data))
        data.comments.forEach { c ->
            node.add(DefaultMutableTreeNode(CommentNode(targetFile, c)))
        }
        if (existingIdx != null) {
            treeModel.removeNodeFromParent(treeRoot.getChildAt(existingIdx) as DefaultMutableTreeNode)
            treeModel.insertNodeInto(node, treeRoot, existingIdx)
        } else {
            treeModel.insertNodeInto(node, treeRoot, treeRoot.childCount)
        }
        reviewTree.expandPath(TreePath(node.path))
    }

    private fun selectFileNode(targetFile: Path) {
        for (i in 0 until treeRoot.childCount) {
            val n = treeRoot.getChildAt(i) as DefaultMutableTreeNode
            if ((n.userObject as? FileNode)?.targetFile == targetFile) {
                reviewTree.selectionPath = TreePath(n.path)
                return
            }
        }
    }

    private fun focusReview(targetFile: Path) {
        val node = (0 until treeRoot.childCount).asSequence()
            .map { treeRoot.getChildAt(it) as DefaultMutableTreeNode }
            .firstOrNull { (it.userObject as? FileNode)?.targetFile == targetFile } ?: return
        val fn = node.userObject as FileNode
        detachMarkers()
        current = fn.review to fn.data
        attachMarkers(fn.data)
    }

    private fun renderError(message: String) {
        com.intellij.notification.NotificationGroupManager.getInstance()
            .getNotificationGroup("Review Duet")
            .createNotification(message, com.intellij.notification.NotificationType.WARNING)
            .notify(project)
    }

    private fun resolveAgent() = run {
        val savedId = PropertiesComponent.getInstance(project).getValue("reviewDuet.selectedAgentId")
        if (savedId != null) AgentRegistry.getAll().find { it.id == savedId } else null
    } ?: AgentRegistry.getAll().firstOrNull()

    private fun onProcess() {
        val (review, data) = current ?: return
        val provider = resolveAgent()
        if (provider == null) {
            com.intellij.openapi.ui.Messages.showErrorDialog(
                project,
                "No agent configured. Pick one in the Review Duet toolbar.",
                "No Agent"
            )
            return
        }
        AgentProcessService.getInstance(project).runProcess(
            workingDir = project.basePath ?: return,
            reviewFilePath = review.storageFile.toString(),
            provider = provider,
            skillFolder = "review-duet:process-file",
            onFinished = {
                ApplicationManager.getApplication().invokeLater {
                    openReview(Path.of(data.filePath))
                    FileReviewEditorCommentExtension.refreshInlaysForFile(project, Path.of(data.filePath))
                }
            }
        )
    }

    internal fun closeReview(targetFile: Path) {
        detachMarkers()
        val current = this.current
        val (review, data) = if (current != null && Path.of(current.second.filePath) == targetFile) {
            current
        } else {
            val r = FileReviewService.getInstance(project).openOrCreate(targetFile)
            r to r.loadOrInit()
        }
        FileReviewService.getInstance(project).close(targetFile)
        // Editor-side cleanup (gutter + inlays):
        com.codereview.local.diff.FileReviewEditorCommentExtension.uninstallForFile(project, targetFile)
        // Remove tree node:
        for (i in 0 until treeRoot.childCount) {
            val n = treeRoot.getChildAt(i) as javax.swing.tree.DefaultMutableTreeNode
            if ((n.userObject as? FileNode)?.targetFile == targetFile) {
                treeModel.removeNodeFromParent(n)
                break
            }
        }
        if (this.current?.second?.filePath == data.filePath) this.current = null
        if (treeRoot.childCount > 0) {
            val first = treeRoot.getChildAt(0) as javax.swing.tree.DefaultMutableTreeNode
            val firstFile = (first.userObject as FileNode).targetFile
            focusReview(firstFile)
        }
    }

    private fun attachMarkers(data: FileReviewData) {
        detachMarkers()
        val vfile = LocalFileSystem.getInstance().findFileByPath(data.filePath) ?: return
        val doc = FileDocumentManager.getInstance().getDocument(vfile) ?: return
        trackedDocument = doc

        data.comments.forEach { c ->
            val lineIdx = (c.line - 1).coerceIn(0, (doc.lineCount - 1).coerceAtLeast(0))
            val start = doc.getLineStartOffset(lineIdx)
            val end = doc.getLineEndOffset(lineIdx)
            markersByCommentId[c.id] = doc.createRangeMarker(start, end)
        }

        val listener = object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                persistMarkers(data, doc)
            }
        }
        doc.addDocumentListener(listener)
        documentListener = listener
    }

    private fun detachMarkers() {
        val doc = trackedDocument
        val listener = documentListener
        if (doc != null && listener != null) doc.removeDocumentListener(listener)
        markersByCommentId.values.forEach { it.dispose() }
        markersByCommentId.clear()
        trackedDocument = null
        documentListener = null
    }

    private fun persistMarkers(data: FileReviewData, doc: Document) {
        var changed = false
        data.comments.forEachIndexed { idx, c ->
            val m = markersByCommentId[c.id] ?: return@forEachIndexed
            if (!m.isValid) return@forEachIndexed
            val newLine = doc.getLineNumber(m.startOffset) + 1
            if (newLine != c.line) {
                data.comments[idx] = c.copy(line = newLine)
                changed = true
            }
        }
        if (changed) {
            current?.first?.save(data)
            ApplicationManager.getApplication().invokeLater {
                refreshCommentsForCurrent()
                FileReviewEditorCommentExtension.refreshInlaysForFile(project, Path.of(data.filePath))
            }
        }
    }

    private fun refreshCommentsForCurrent() {
        val (review, data) = current ?: return
        val target = Path.of(data.filePath)
        upsertFileNode(target, review, data)
    }

    private inner class CopyReviewFilePathAction : AnAction("Copy review file path") {
        override fun getActionUpdateThread() = ActionUpdateThread.EDT

        override fun update(e: AnActionEvent) {
            val node = reviewTree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode
            e.presentation.isEnabledAndVisible = node?.userObject is FileNode
        }

        override fun actionPerformed(e: AnActionEvent) {
            val node = reviewTree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode ?: return
            val fileNode = node.userObject as? FileNode ?: return
            val path = fileNode.review.storageFile.toAbsolutePath().toString()
            val selection = java.awt.datatransfer.StringSelection(path)
            java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
        }
    }

    private inner class CloseReviewAction : com.intellij.openapi.actionSystem.AnAction("Close review", "Discard this file review", null) {
        override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.EDT
        override fun update(e: com.intellij.openapi.actionSystem.AnActionEvent) {
            val node = (reviewTree.selectionPath?.lastPathComponent as? javax.swing.tree.DefaultMutableTreeNode)
            e.presentation.isEnabledAndVisible = node?.userObject is FileNode
        }
        override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
            val node = reviewTree.selectionPath?.lastPathComponent as? javax.swing.tree.DefaultMutableTreeNode ?: return
            val fileNode = node.userObject as? FileNode ?: return
            closeReview(fileNode.targetFile)
        }
    }
}
