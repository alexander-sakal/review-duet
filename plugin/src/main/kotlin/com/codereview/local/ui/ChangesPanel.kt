package com.codereview.local.ui

import com.codereview.local.model.ChangeType
import com.codereview.local.model.ChangedFile
import com.codereview.local.services.GitService
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
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * Represents a review round with its comparison refs.
 */
data class ReviewRound(
    val number: Int,
    val fromRef: String,
    val toRef: String,
    val isLatest: Boolean
) {
    override fun toString(): String = if (isLatest) "Round $number (current)" else "Round $number"
}

class ChangesPanel(
    private val project: Project,
    private val gitService: GitService
) : JPanel(BorderLayout()) {

    private val roundCombo = JComboBox<ReviewRound>()
    private val rootNode = DefaultMutableTreeNode("Changes")
    private val treeModel = DefaultTreeModel(rootNode)
    private val fileTree = Tree(treeModel)
    private var currentChanges: List<ChangedFile> = emptyList()
    private var currentRound: ReviewRound? = null

    init {
        border = JBUI.Borders.empty()

        // Header with single round dropdown
        val headerPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(JLabel("Review Round: "))
            add(roundCombo)
            add(Box.createHorizontalGlue())
        }

        // Footer with Review All button
        val footerPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(JButton("Review All Files").apply {
                icon = AllIcons.Actions.Diff
                addActionListener { openAllDiffs() }
            })
            add(Box.createHorizontalGlue())
        }

        // Setup combo
        roundCombo.addActionListener {
            currentRound = roundCombo.selectedItem as? ReviewRound
            refreshFileList()
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

        add(headerPanel, BorderLayout.NORTH)
        add(JBScrollPane(fileTree), BorderLayout.CENTER)
        add(footerPanel, BorderLayout.SOUTH)

        refreshRounds()
    }

    fun refresh() {
        refreshRounds()
    }

    private fun refreshRounds() {
        val tags = gitService.getReviewTags().sorted()
        val previousSelection = roundCombo.selectedItem as? ReviewRound

        roundCombo.removeAllItems()

        if (tags.isEmpty()) {
            currentRound = null
            refreshFileList()
            return
        }

        // Build rounds: each round compares previous tag to current tag
        // Latest round compares last tag to HEAD
        val rounds = mutableListOf<ReviewRound>()

        for (i in 1 until tags.size) {
            rounds.add(ReviewRound(
                number = i,
                fromRef = tags[i - 1],
                toRef = tags[i],
                isLatest = false
            ))
        }

        // Add the "current" round that compares last tag to HEAD
        val lastTag = tags.last()
        val currentRoundNumber = tags.size
        rounds.add(ReviewRound(
            number = currentRoundNumber,
            fromRef = lastTag,
            toRef = "HEAD",
            isLatest = true
        ))

        rounds.forEach { roundCombo.addItem(it) }

        // Restore selection or select latest
        if (previousSelection != null) {
            val match = rounds.find { it.number == previousSelection.number }
            if (match != null) {
                roundCombo.selectedItem = match
            } else {
                roundCombo.selectedIndex = rounds.size - 1
            }
        } else {
            roundCombo.selectedIndex = rounds.size - 1
        }

        currentRound = roundCombo.selectedItem as? ReviewRound
        refreshFileList()
    }

    private fun refreshFileList() {
        val round = currentRound
        rootNode.removeAllChildren()

        if (round == null) {
            currentChanges = emptyList()
            treeModel.reload()
            return
        }

        currentChanges = gitService.getChangedFiles(round.fromRef, round.toRef)
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

    private fun openDiffAtFile(file: ChangedFile) {
        if (currentChanges.isEmpty()) return

        val startIndex = currentChanges.indexOf(file).coerceAtLeast(0)
        val chain = createDiffChain(startIndex)

        DiffManager.getInstance().showDiff(project, chain, DiffDialogHints.FRAME)
    }

    private fun openAllDiffs() {
        if (currentChanges.isEmpty()) return

        val chain = createDiffChain(0)
        DiffManager.getInstance().showDiff(project, chain, DiffDialogHints.FRAME)
    }

    private fun createDiffChain(startIndex: Int): DiffRequestChain {
        val round = currentRound ?: return EmptyDiffChain()
        val fromRef = round.fromRef
        val toRef = round.toRef

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
                        else -> if (toRef == "HEAD") {
                            gitService.getFileAtRef("HEAD", file.path) ?: ""
                        } else {
                            gitService.getFileAtRef(toRef, file.path) ?: ""
                        }
                    }

                    // Get file type for syntax highlighting
                    val fileName = file.path.substringAfterLast('/')
                    val fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName)

                    val toLabel = if (toRef == "HEAD") "Working Copy" else toRef

                    val contentFactory = DiffContentFactory.getInstance()
                    return SimpleDiffRequest(
                        "${file.path} ($fromRef → $toLabel)",
                        contentFactory.create(project, fromContent, fileType),
                        contentFactory.create(project, toContent, fileType),
                        fromRef,
                        toLabel
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

    private class ChangedFileTreeRenderer : ColoredTreeCellRenderer() {
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
                    val (color, attrs) = when (userObject.changeType) {
                        ChangeType.ADDED -> JBColor.GREEN to SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
                        ChangeType.MODIFIED -> JBColor.BLUE to SimpleTextAttributes.REGULAR_ATTRIBUTES
                        ChangeType.DELETED -> JBColor.RED to SimpleTextAttributes.REGULAR_ATTRIBUTES
                    }

                    icon = AllIcons.FileTypes.Any_type
                    append("${userObject.changeType.symbol}  ", SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, color))
                    append(userObject.path.substringAfterLast('/'), attrs)
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
