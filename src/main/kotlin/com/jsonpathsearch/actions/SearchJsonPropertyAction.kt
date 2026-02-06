package com.jsonpathsearch.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.jsonpathsearch.model.SearchResult
import com.jsonpathsearch.services.JsonSearchService
import com.jsonpathsearch.ui.SearchResultsPopup

/**
 * Action that searches for JSON property paths based on selected text.
 *
 * When triggered, this action:
 * 1. Gets the selected text from the editor
 * 2. Searches all JSON files in the project for matching property paths
 * 3. Shows results in a popup where user can click to navigate
 */
class SearchJsonPropertyAction : DumbAwareAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return

        val selectedText = getSelectedText(editor)
        if (selectedText.isNullOrBlank()) {
            SearchResultsPopup.showError(editor, "Please select a property path to search (e.g., 'balance.main.title')")
            return
        }

        // Clean up the selected text
        val searchQuery = selectedText.trim()
            .removeSurrounding("'")
            .removeSurrounding("\"")
            .trim()

        if (searchQuery.isEmpty()) {
            SearchResultsPopup.showError(editor, "Please select a valid property path")
            return
        }

        // Run search in background with progress indicator
        searchWithProgress(project, editor, searchQuery)
    }

    /**
     * Gets the selected text from the editor.
     */
    private fun getSelectedText(editor: Editor): String? {
        return editor.selectionModel.selectedText
    }

    /**
     * Runs the search in a background task with progress indicator.
     */
    private fun searchWithProgress(project: Project, editor: Editor, searchQuery: String) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Searching JSON files for '$searchQuery'...",
            true
        ) {
            private val results = mutableListOf<SearchResult>()

            override fun run(indicator: ProgressIndicator) {
                val service = JsonSearchService.getInstance(project)
                
                // FileTypeIndex requires read action
                val jsonFiles = ApplicationManager.getApplication().runReadAction<Collection<com.intellij.openapi.vfs.VirtualFile>> {
                    service.findAllJsonFiles()
                }

                if (jsonFiles.isEmpty()) {
                    return
                }

                indicator.isIndeterminate = false
                var processed = 0

                for (file in jsonFiles) {
                    if (indicator.isCanceled) break

                    indicator.fraction = processed.toDouble() / jsonFiles.size
                    indicator.text2 = "Scanning: ${file.name}"

                    // PSI access requires read action
                    ApplicationManager.getApplication().runReadAction {
                        service.searchInFile(file, searchQuery, results)
                    }

                    processed++
                }

                // Remove duplicates based on file + offset
                val uniqueResults = results.distinctBy { 
                    "${it.file.path}:${it.getTextOffset()}" 
                }
                results.clear()
                results.addAll(uniqueResults)
            }

            override fun onSuccess() {
                // Show results in popup on EDT
                SearchResultsPopup.show(project, editor, results, searchQuery)
            }

            override fun onCancel() {
                // User cancelled the search
            }

            override fun onThrowable(error: Throwable) {
                SearchResultsPopup.showError(editor, "Search failed: ${error.message}")
            }
        })
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val project = e.project

        // Enable action only when there's an editor with selected text
        val hasSelection = editor?.selectionModel?.hasSelection() == true
        
        e.presentation.isEnabledAndVisible = project != null && editor != null
        e.presentation.isEnabled = hasSelection
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
