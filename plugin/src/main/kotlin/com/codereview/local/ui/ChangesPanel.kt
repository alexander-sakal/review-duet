package com.codereview.local.ui

import com.codereview.local.model.ChangeType
import com.codereview.local.model.ChangedFile
import com.codereview.local.services.GitService
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.icons.AllIcons
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

class ChangesPanel(
    private val project: Project,
    private val gitService: GitService
) : JPanel(BorderLayout()) {

    private val fromCombo = JComboBox<String>()
    private val toCombo = JComboBox<String>()
    private val rootNode = DefaultMutableTreeNode("Changes")
    private val treeModel = DefaultTreeModel(rootNode)
    private val fileTree = Tree(treeModel)

    init {
        border = JBUI.Borders.empty(5)

        // Header with dropdowns
        val headerPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(JLabel("From: "))
            add(fromCombo)
            add(Box.createHorizontalStrut(10))
            add(JLabel("To: "))
            add(toCombo)
            add(Box.createHorizontalGlue())
        }

        // Setup combos
        fromCombo.addActionListener { refreshFileList() }
        toCombo.addActionListener { refreshFileList() }

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
                    openDiff(file)
                }
            }
        })

        add(headerPanel, BorderLayout.NORTH)
        add(JBScrollPane(fileTree), BorderLayout.CENTER)

        refreshTags()
    }

    fun refresh() {
        refreshTags()
    }

    private fun refreshTags() {
        val tags = gitService.getReviewTags()
        val selectedFrom = fromCombo.selectedItem as? String
        val selectedTo = toCombo.selectedItem as? String

        fromCombo.removeAllItems()
        toCombo.removeAllItems()

        tags.forEach { tag ->
            fromCombo.addItem(tag)
            toCombo.addItem(tag)
        }

        // Restore selection or set defaults
        if (tags.isNotEmpty()) {
            if (selectedFrom != null && tags.contains(selectedFrom)) {
                fromCombo.selectedItem = selectedFrom
            } else if (tags.size >= 2) {
                fromCombo.selectedIndex = tags.size - 2
            }

            if (selectedTo != null && tags.contains(selectedTo)) {
                toCombo.selectedItem = selectedTo
            } else {
                toCombo.selectedIndex = tags.size - 1
            }
        }

        refreshFileList()
    }

    private fun refreshFileList() {
        val fromRef = fromCombo.selectedItem as? String ?: return
        val toRef = toCombo.selectedItem as? String ?: return

        rootNode.removeAllChildren()

        if (fromRef == toRef) {
            treeModel.reload()
            return
        }

        val changes = gitService.getChangedFiles(fromRef, toRef)
        buildTree(changes)
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

    private fun openDiff(file: ChangedFile) {
        val fromRef = fromCombo.selectedItem as? String ?: return
        val toRef = toCombo.selectedItem as? String ?: return

        val fromContent = when (file.changeType) {
            ChangeType.ADDED -> ""
            else -> gitService.getFileAtRef(fromRef, file.path) ?: ""
        }

        val toContent = when (file.changeType) {
            ChangeType.DELETED -> ""
            else -> gitService.getFileAtRef(toRef, file.path) ?: ""
        }

        val contentFactory = DiffContentFactory.getInstance()
        val request = SimpleDiffRequest(
            "${file.path} ($fromRef → $toRef)",
            contentFactory.create(fromContent),
            contentFactory.create(toContent),
            fromRef,
            toRef
        )

        DiffManager.getInstance().showDiff(project, request)
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
