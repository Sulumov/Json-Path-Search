package com.jsonpathsearch.settings

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.Messages
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import javax.swing.DefaultListModel
import javax.swing.JComponent

/**
 * Settings page for JSON Path Search plugin.
 * Allows configuring included directories and excluded glob patterns.
 */
class JsonSearchConfigurable(private val project: Project) : Configurable {

    private val includedDirsModel = DefaultListModel<String>()
    private val excludedPatternsModel = DefaultListModel<String>()

    private lateinit var includedDirsList: JBList<String>
    private lateinit var excludedPatternsList: JBList<String>

    override fun getDisplayName(): String = "JSON Path Search"

    override fun createComponent(): JComponent {
        includedDirsList = JBList(includedDirsModel)
        excludedPatternsList = JBList(excludedPatternsModel)

        // Load current settings
        reset()

        return panel {
            group("Include Directories") {
                row {
                    comment("Specify directories to search in. If empty, searches in the entire project.")
                }
                row {
                    val decorator = ToolbarDecorator.createDecorator(includedDirsList)
                        .setAddAction { addIncludedDirectory() }
                        .setRemoveAction { removeSelectedItem(includedDirsModel, includedDirsList) }
                        .disableUpDownActions()

                    cell(decorator.createPanel())
                        .align(Align.FILL)
                        .resizableColumn()
                }.resizableRow()
            }

            group("Exclude Patterns") {
                row {
                    comment("Glob patterns to exclude from search (e.g., **/node_modules/**, **/build/**).")
                }
                row {
                    val decorator = ToolbarDecorator.createDecorator(excludedPatternsList)
                        .setAddAction { addExcludedPattern() }
                        .setRemoveAction { removeSelectedItem(excludedPatternsModel, excludedPatternsList) }
                        .disableUpDownActions()

                    cell(decorator.createPanel())
                        .align(Align.FILL)
                        .resizableColumn()
                }.resizableRow()

                row {
                    comment(
                        """
                        <b>Pattern examples:</b><br>
                        • <code>**/node_modules/**</code> — exclude all node_modules directories<br>
                        • <code>**/build/**</code> — exclude build output directories<br>
                        • <code>**/.idea/**</code> — exclude IDE configuration<br>
                        • <code>**/test-data/**</code> — exclude test data directories
                        """.trimIndent()
                    )
                }
            }
        }
    }

    private fun addIncludedDirectory() {
        val projectDir = project.guessProjectDir()
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
            .withTitle("Select Directory")
            .withDescription("Choose a directory to include in JSON search")
        
        if (projectDir != null) {
            descriptor.withRoots(projectDir)
        }

        val selectedDir = FileChooser.chooseFile(descriptor, project, projectDir)
        if (selectedDir != null) {
            val projectBasePath = project.basePath ?: return
            val relativePath = selectedDir.path.removePrefix(projectBasePath).removePrefix("/")

            if (relativePath.isNotEmpty() && !includedDirsModel.contains(relativePath)) {
                includedDirsModel.addElement(relativePath)
            } else if (relativePath.isEmpty()) {
                // Project root selected
                Messages.showInfoMessage(
                    project,
                    "Project root is selected by default when no directories are specified.",
                    "Information"
                )
            }
        }
    }

    private fun addExcludedPattern() {
        val pattern = Messages.showInputDialog(
            project,
            "Enter a glob pattern to exclude (e.g., **/node_modules/**):",
            "Add Exclude Pattern",
            null,
            "**/",
            null
        )

        if (!pattern.isNullOrBlank() && !excludedPatternsModel.contains(pattern)) {
            excludedPatternsModel.addElement(pattern)
        }
    }

    private fun <T> removeSelectedItem(model: DefaultListModel<T>, list: JBList<T>) {
        val selectedIndex = list.selectedIndex
        if (selectedIndex >= 0) {
            model.remove(selectedIndex)
        }
    }

    override fun isModified(): Boolean {
        val settings = JsonSearchSettings.getInstance(project)

        val currentIncluded = (0 until includedDirsModel.size()).map { includedDirsModel.getElementAt(it) }
        val currentExcluded = (0 until excludedPatternsModel.size()).map { excludedPatternsModel.getElementAt(it) }

        return currentIncluded != settings.getIncludedDirectories() ||
                currentExcluded != settings.getExcludedPatterns()
    }

    override fun apply() {
        val settings = JsonSearchSettings.getInstance(project)

        val includedDirs = (0 until includedDirsModel.size()).map { includedDirsModel.getElementAt(it) }
        val excludedPatterns = (0 until excludedPatternsModel.size()).map { excludedPatternsModel.getElementAt(it) }

        settings.setIncludedDirectories(includedDirs)
        settings.setExcludedPatterns(excludedPatterns)
    }

    override fun reset() {
        val settings = JsonSearchSettings.getInstance(project)

        includedDirsModel.clear()
        for (dir in settings.getIncludedDirectories()) {
            includedDirsModel.addElement(dir)
        }

        excludedPatternsModel.clear()
        for (pattern in settings.getExcludedPatterns()) {
            excludedPatternsModel.addElement(pattern)
        }
    }
}
