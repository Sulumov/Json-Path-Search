package com.jsonpathsearch.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.jsonpathsearch.model.SearchResult
import javax.swing.Icon

/**
 * Popup step for displaying search results.
 */
class SearchResultsPopupStep(
    private val results: List<SearchResult>,
    private val project: Project
) : BaseListPopupStep<SearchResult>("Found ${results.size} match${if (results.size != 1) "es" else ""}", results) {

    override fun getTextFor(value: SearchResult): String {
        return value.toDisplayString()
    }

    override fun getIconFor(value: SearchResult): Icon {
        return AllIcons.FileTypes.Json
    }

    override fun onChosen(selectedValue: SearchResult, finalChoice: Boolean): PopupStep<*>? {
        if (finalChoice) {
            navigateToResult(selectedValue)
        }
        return PopupStep.FINAL_CHOICE
    }

    override fun isSpeedSearchEnabled(): Boolean = true

    override fun isAutoSelectionEnabled(): Boolean = false

    private fun navigateToResult(result: SearchResult) {
        val descriptor = OpenFileDescriptor(
            project,
            result.file,
            result.getTextOffset()
        )
        descriptor.navigate(true)
    }
}

/**
 * Helper object for showing search results popup.
 */
object SearchResultsPopup {

    /**
     * Shows a popup with search results at the editor's caret position.
     *
     * @param project The current project
     * @param editor The editor where the search was initiated
     * @param results List of search results to display
     * @param searchQuery The original search query (for display purposes)
     */
    fun show(
        project: Project,
        editor: Editor,
        results: List<SearchResult>,
        searchQuery: String
    ) {
        if (results.isEmpty()) {
            showNoResultsMessage(editor, searchQuery)
            return
        }

        val popup = JBPopupFactory.getInstance().createListPopup(
            SearchResultsPopupStep(results, project)
        )

        popup.showInBestPositionFor(editor)
    }

    /**
     * Shows a message when no results are found.
     */
    private fun showNoResultsMessage(editor: Editor, searchQuery: String) {
        JBPopupFactory.getInstance()
            .createMessage("No matches found for '$searchQuery'")
            .showInBestPositionFor(editor)
    }

    /**
     * Shows an error message.
     */
    fun showError(editor: Editor, message: String) {
        JBPopupFactory.getInstance()
            .createMessage(message)
            .showInBestPositionFor(editor)
    }
}
