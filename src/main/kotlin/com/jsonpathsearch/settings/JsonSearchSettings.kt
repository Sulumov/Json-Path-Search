package com.jsonpathsearch.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

/**
 * Persistent settings for JSON Path Search plugin.
 * Stores included directories and excluded glob patterns.
 */
@State(
    name = "JsonSearchSettings",
    storages = [Storage("jsonSearchSettings.xml")]
)
@Service(Service.Level.PROJECT)
class JsonSearchSettings : PersistentStateComponent<JsonSearchSettings.State> {

    /**
     * State class holding the actual settings data.
     */
    data class State(
        /**
         * List of directories to search in (relative to project root).
         * If empty, searches in the entire project.
         */
        var includedDirectories: MutableList<String> = mutableListOf(),

        // List of glob patterns to exclude from search.
        // Examples: node_modules, build, .idea
        var excludedPatterns: MutableList<String> = mutableListOf()
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    /**
     * Returns a copy of included directories list.
     */
    fun getIncludedDirectories(): List<String> = myState.includedDirectories.toList()

    /**
     * Sets the included directories list.
     */
    fun setIncludedDirectories(directories: List<String>) {
        myState.includedDirectories = directories.toMutableList()
    }

    /**
     * Returns a copy of excluded patterns list.
     */
    fun getExcludedPatterns(): List<String> = myState.excludedPatterns.toList()

    /**
     * Sets the excluded patterns list.
     */
    fun setExcludedPatterns(patterns: List<String>) {
        myState.excludedPatterns = patterns.toMutableList()
    }

    companion object {
        fun getInstance(project: Project): JsonSearchSettings =
            project.getService(JsonSearchSettings::class.java)
    }
}
