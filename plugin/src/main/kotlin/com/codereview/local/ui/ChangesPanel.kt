package com.codereview.local.ui

import com.codereview.local.model.ChangeType
import com.codereview.local.model.ChangedFile
import com.codereview.local.services.GitService
import com.codereview.local.services.ReviewService
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManager
import com.intellij.diff.chains.DiffRequestChain
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Rectangle
import java.awt.Window
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Path
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class ChangesPanel(
    private val project: Project,
    private val gitService: GitService
) : JPanel(BorderLayout()) {

    companion object {
        val IS_COMMENT_REVIEW_KEY = com.intellij.openapi.util.Key.create<Boolean>("CodeReview.IsCommentReview")
        val COMMENT_ID_KEY = com.intellij.openapi.util.Key.create<Int>("CodeReview.CommentId")

        /**
         * Open diff view showing changes in a single commit (commit^ to commit)
         */
        fun openDiffForSingleCommit(project: Project, commitSha: String, filePath: String? = null, commentId: Int? = null, windowBounds: Rectangle? = null) {
            val basePath = Path.of(project.basePath ?: return)
            val gitService = GitService(basePath)

            val parentCommit = gitService.getParentCommitSha(commitSha) ?: return
            openDiffBetweenCommits(project, parentCommit, commitSha, filePath, isCommentReview = true, commentId = commentId, windowBounds = windowBounds)
        }

        /**
         * Open diff view showing changes between two commits
         */
        fun openDiffBetweenCommits(project: Project, fromCommit: String, toCommit: String, filePath: String? = null, isCommentReview: Boolean = false, commentId: Int? = null, windowBounds: Rectangle? = null) {
            val basePath = Path.of(project.basePath ?: return)
            val gitService = GitService(basePath)

            // Get files changed between commits
            val changedFiles = gitService.getChangedFiles(fromCommit, toCommit)
            if (changedFiles.isEmpty()) return

            // If filePath specified, start with that file
            val startIndex = if (filePath != null) {
                changedFiles.indexOfFirst { it.path == filePath }.coerceAtLeast(0)
            } else 0

            val fromShort = fromCommit.take(7)
            val toShort = toCommit.take(7)
            val producers = changedFiles.map { file ->
                object : DiffRequestProducer {
                    override fun getName(): String = file.path

                    override fun process(context: UserDataHolder, indicator: ProgressIndicator): DiffRequest {
                        val fromContent = when (file.changeType) {
                            ChangeType.ADDED -> ""
                            else -> gitService.getFileAtRef(fromCommit, file.path) ?: ""
                        }

                        val toContent = when (file.changeType) {
                            ChangeType.DELETED -> ""
                            else -> gitService.getFileAtRef(toCommit, file.path) ?: ""
                        }

                        val fileName = file.path.substringAfterLast('/')
                        val fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName)

                        val contentFactory = DiffContentFactory.getInstance()
                        val request = SimpleDiffRequest(
                            "${file.path} ($fromShort → $toShort)",
                            contentFactory.create(project, fromContent, fileType),
                            contentFactory.create(project, toContent, fileType),
                            fromShort,
                            toShort
                        )

                        // Mark as comment review if applicable
                        if (isCommentReview) {
                            request.putUserData(IS_COMMENT_REVIEW_KEY, true)
                            commentId?.let { request.putUserData(COMMENT_ID_KEY, it) }
                        }

                        return request
                    }
                }
            }

            @Suppress("OVERRIDE_DEPRECATION")
            val chain = object : UserDataHolderBase(), DiffRequestChain {
                private var currentIndex = startIndex
                override fun getRequests(): List<DiffRequestProducer> = producers
                override fun getIndex(): Int = currentIndex
                override fun setIndex(index: Int) { currentIndex = index }
            }

            DiffManager.getInstance().showDiff(project, chain, DiffDialogHints.FRAME)

            // Restore window position if provided
            if (windowBounds != null) {
                SwingUtilities.invokeLater {
                    // Find the most recently opened diff window
                    Window.getWindows()
                        .filterIsInstance<JFrame>()
                        .lastOrNull { it.isVisible && it.title?.contains(toCommit.take(7)) == true }
                        ?.bounds = windowBounds
                }
            }
        }
    }

    private val basePath: Path by lazy {
        Path.of(project.basePath ?: throw IllegalStateException("No project base path"))
    }
    private val reviewService: ReviewService by lazy { ReviewService(basePath) }

    private val rootNode = DefaultMutableTreeNode("Changes")
    private val treeModel = DefaultTreeModel(rootNode)
    private val fileTree = Tree(treeModel)
    private var currentChanges: List<ChangedFile> = emptyList()
    private var baseCommit: String? = null

    init {
        // Toolbar with actions
        val actionGroup = DefaultActionGroup().apply {
            add(object : AnAction("Review All Files", "Open diff view for all changed files", AllIcons.Actions.Diff) {
                override fun actionPerformed(e: AnActionEvent) {
                    openAllDiffs()
                }
            })
            addSeparator()
            add(object : AnAction("Expand All", "Expand all directories", AllIcons.Actions.Expandall) {
                override fun actionPerformed(e: AnActionEvent) {
                    expandAllNodes()
                }
            })
            add(object : AnAction("Collapse All", "Collapse all directories", AllIcons.Actions.Collapseall) {
                override fun actionPerformed(e: AnActionEvent) {
                    collapseAllNodes()
                }
            })
        }

        val toolbar = ActionManager.getInstance().createActionToolbar("ChangesPanel", actionGroup, true)
        toolbar.targetComponent = this

        val toolbarPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(0, 0, 4, 0)
            add(toolbar.component, BorderLayout.WEST)
            add(JSeparator(), BorderLayout.SOUTH)
        }

        // Setup file tree
        fileTree.cellRenderer = ChangedFileTreeRenderer()
        fileTree.isRootVisible = false
        fileTree.showsRootHandles = true
        fileTree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val path = fileTree.getPathForLocation(e.x, e.y) ?: return
                    val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                    val file = node.userObject as? ChangedFile ?: return
                    openDiffAtFile(file)
                }
            }
        })

        val scrollPane = JBScrollPane(fileTree).apply {
            border = JBUI.Borders.empty()
        }

        add(toolbarPanel, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)

        refreshFileList()
    }

    fun refresh() {
        border = JBUI.Borders.empty()
        refreshFileList()
        revalidate()
        repaint()
    }

    private fun refreshFileList() {
        val data = reviewService.loadReviewData()
        baseCommit = data?.baseCommit
        rootNode.removeAllChildren()

        if (baseCommit == null) {
            currentChanges = emptyList()
            treeModel.reload()
            return
        }

        currentChanges = gitService.getChangedFiles(baseCommit!!, "HEAD")
        buildTree(currentChanges)
        treeModel.reload()
        expandAllNodes()
    }

    private fun buildTree(changes: List<ChangedFile>) {
        // Group files by directory
        val dirMap = mutableMapOf<String, MutableList<ChangedFile>>()

        for (file in changes) {
            val lastSlash = file.path.lastIndexOf('/')
            val dir = if (lastSlash > 0) file.path.substring(0, lastSlash) else ""
            dirMap.getOrPut(dir) { mutableListOf() }.add(file)
        }

        // Sort directories and build tree
        val sortedDirs = dirMap.keys.sorted()

        for (dir in sortedDirs) {
            val files = dirMap[dir] ?: continue

            if (dir.isEmpty()) {
                // Root-level files
                for (file in files.sortedBy { it.path }) {
                    rootNode.add(DefaultMutableTreeNode(file))
                }
            } else {
                // Find or create directory node path
                val dirNode = getOrCreateDirNode(dir)
                for (file in files.sortedBy { it.path.substringAfterLast('/') }) {
                    dirNode.add(DefaultMutableTreeNode(file))
                }
            }
        }
    }

    private fun getOrCreateDirNode(path: String): DefaultMutableTreeNode {
        val parts = path.split('/')
        var currentNode = rootNode

        for (part in parts) {
            var found: DefaultMutableTreeNode? = null
            for (i in 0 until currentNode.childCount) {
                val child = currentNode.getChildAt(i) as DefaultMutableTreeNode
                if (child.userObject is String && child.userObject == part) {
                    found = child
                    break
                }
            }
            currentNode = found ?: DefaultMutableTreeNode(part).also { currentNode.add(it) }
        }

        return currentNode
    }

    private fun expandAllNodes() {
        var row = 0
        while (row < fileTree.rowCount) {
            fileTree.expandRow(row)
            row++
        }
    }

    private fun collapseAllNodes() {
        var row = fileTree.rowCount - 1
        while (row >= 0) {
            fileTree.collapseRow(row)
            row--
        }
    }

    private fun openDiffAtFile(file: ChangedFile) {
        if (currentChanges.isEmpty() || baseCommit == null) return

        val startIndex = currentChanges.indexOf(file).coerceAtLeast(0)
        val chain = createDiffChain(startIndex)

        DiffManager.getInstance().showDiff(project, chain, DiffDialogHints.FRAME)
    }

    private fun openAllDiffs() {
        if (currentChanges.isEmpty() || baseCommit == null) return

        val chain = createDiffChain(0)
        DiffManager.getInstance().showDiff(project, chain, DiffDialogHints.FRAME)
    }

    private fun createDiffChain(startIndex: Int): DiffRequestChain {
        val fromRef = baseCommit ?: return EmptyDiffChain()
        val shortRef = fromRef.take(7)

        val producers = currentChanges.map { file ->
            object : DiffRequestProducer {
                override fun getName(): String = file.path

                override fun process(context: UserDataHolder, indicator: ProgressIndicator): DiffRequest {
                    val fromContent = when (file.changeType) {
                        ChangeType.ADDED -> ""
                        else -> gitService.getFileAtRef(fromRef, file.path) ?: ""
                    }

                    val toContent = when (file.changeType) {
                        ChangeType.DELETED -> ""
                        else -> gitService.getFileAtRef("HEAD", file.path) ?: ""
                    }

                    // Get file type for syntax highlighting
                    val fileName = file.path.substringAfterLast('/')
                    val fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName)

                    val contentFactory = DiffContentFactory.getInstance()
                    return SimpleDiffRequest(
                        "${file.path} ($shortRef → Working Copy)",
                        contentFactory.create(project, fromContent, fileType),
                        contentFactory.create(project, toContent, fileType),
                        shortRef,
                        "Working Copy"
                    )
                }
            }
        }

        @Suppress("OVERRIDE_DEPRECATION")
        return object : UserDataHolderBase(), DiffRequestChain {
            private var currentIndex = startIndex

            override fun getRequests(): List<DiffRequestProducer> = producers
            override fun getIndex(): Int = currentIndex
            override fun setIndex(index: Int) { currentIndex = index }
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    private class EmptyDiffChain : UserDataHolderBase(), DiffRequestChain {
        override fun getRequests(): List<DiffRequestProducer> = emptyList()
        override fun getIndex(): Int = 0
        override fun setIndex(index: Int) {}
    }

    private inner class ChangedFileTreeRenderer : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(
            tree: JTree,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean
        ) {
            val node = value as? DefaultMutableTreeNode ?: return
            val userObject = node.userObject

            when (userObject) {
                is ChangedFile -> {
                    // File node
                    val isReviewed = reviewService.isFileReviewed(userObject.path)
                    val fileName = userObject.path.substringAfterLast('/')

                    // Get file type icon
                    val fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName)
                    icon = fileType.icon

                    // Color based on change type (like IntelliJ's VCS)
                    val color = when (userObject.changeType) {
                        ChangeType.ADDED -> JBColor(0x007F00, 0x629755)    // Green
                        ChangeType.MODIFIED -> JBColor(0x0032A0, 0x6897BB) // Blue
                        ChangeType.DELETED -> JBColor(0x787878, 0x787878) // Gray
                    }

                    val attrs = SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, color)
                    append(fileName, attrs)

                    // Green checkmark for reviewed files
                    if (isReviewed) {
                        append("  ✓", SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, JBColor(0x007F00, 0x629755)))
                    }
                }
                is String -> {
                    // Directory node
                    icon = AllIcons.Nodes.Folder
                    append(userObject, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                }
            }
        }
    }
}
