package com.jsonpathsearch.services

import com.intellij.json.JsonFileType
import com.intellij.json.psi.JsonArray
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.jsonpathsearch.model.SearchResult
import com.jsonpathsearch.settings.JsonSearchSettings
import java.nio.file.FileSystems
import java.nio.file.PathMatcher
import java.nio.file.Paths

/**
 * Service for searching JSON property paths across project files.
 */
@Service(Service.Level.PROJECT)
class JsonSearchService(private val project: Project) {

    companion object {
        fun getInstance(project: Project): JsonSearchService =
            project.getService(JsonSearchService::class.java)
    }

    /**
     * Finds all JSON files in the project, filtered by settings.
     * - If included directories are specified, only searches in those directories.
     * - Excludes files matching any of the excluded glob patterns.
     */
    fun findAllJsonFiles(): Collection<VirtualFile> {
        val settings = JsonSearchSettings.getInstance(project)
        val includedDirs = settings.getIncludedDirectories()
        val excludedPatterns = settings.getExcludedPatterns()
        val projectBasePath = project.basePath ?: return emptyList()

        // Get all JSON files from scope
        val scope = if (includedDirs.isNotEmpty()) {
            createScopeForDirectories(includedDirs)
        } else {
            GlobalSearchScope.projectScope(project)
        }

        val allJsonFiles = FileTypeIndex.getFiles(JsonFileType.INSTANCE, scope)

        // Apply exclusion patterns
        if (excludedPatterns.isEmpty()) {
            return allJsonFiles
        }

        val matchers = excludedPatterns.mapNotNull { pattern ->
            try {
                FileSystems.getDefault().getPathMatcher("glob:$pattern")
            } catch (e: Exception) {
                null // Skip invalid patterns
            }
        }

        return allJsonFiles.filter { file ->
            !matchesAnyExcludePattern(file, projectBasePath, matchers)
        }
    }

    /**
     * Creates a search scope limited to specified directories.
     */
    private fun createScopeForDirectories(directories: List<String>): GlobalSearchScope {
        val projectBasePath = project.basePath ?: return GlobalSearchScope.projectScope(project)
        val projectScope = GlobalSearchScope.projectScope(project)

        return object : GlobalSearchScope(project) {
            override fun contains(file: VirtualFile): Boolean {
                if (!projectScope.contains(file)) return false
                
                val filePath = file.path
                return directories.any { dir ->
                    val fullDirPath = "$projectBasePath/$dir"
                    filePath.startsWith(fullDirPath) || filePath.startsWith("$fullDirPath/")
                }
            }

            override fun isSearchInModuleContent(aModule: com.intellij.openapi.module.Module): Boolean = true
            override fun isSearchInLibraries(): Boolean = false
        }
    }

    /**
     * Checks if a file matches any of the exclude patterns.
     */
    private fun matchesAnyExcludePattern(
        file: VirtualFile,
        projectBasePath: String,
        matchers: List<PathMatcher>
    ): Boolean {
        val relativePath = file.path.removePrefix(projectBasePath).removePrefix("/")
        val path = Paths.get(relativePath)

        return matchers.any { matcher ->
            matcher.matches(path)
        }
    }

    /**
     * Searches for a property path in all JSON files.
     *
     * @param propertyPath The dot-notation path to search for (e.g., "balance.main.title")
     * @return List of search results
     */
    fun searchPropertyPath(propertyPath: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val jsonFiles = findAllJsonFiles()

        for (file in jsonFiles) {
            searchInFile(file, propertyPath, results)
        }

        return results
    }

    /**
     * Searches for a property path in a single JSON file.
     */
    fun searchInFile(
        file: VirtualFile,
        propertyPath: String,
        results: MutableList<SearchResult>
    ) {
        val psiFile = PsiManager.getInstance(project).findFile(file) as? JsonFile ?: return
        val topLevelValue = psiFile.topLevelValue

        val pathParts = propertyPath.split(".")
        if (pathParts.isEmpty()) return

        when (topLevelValue) {
            is JsonObject -> searchInObject(topLevelValue, pathParts, 0, file, results)
            is JsonArray -> searchInArray(topLevelValue, pathParts, file, results)
        }
    }

    /**
     * Recursively searches for the property path in a JSON object.
     */
    private fun searchInObject(
        jsonObject: JsonObject,
        pathParts: List<String>,
        currentIndex: Int,
        file: VirtualFile,
        results: MutableList<SearchResult>
    ) {
        if (currentIndex >= pathParts.size) return

        val targetKey = pathParts[currentIndex]
        val property = jsonObject.findProperty(targetKey)

        if (property != null) {
            if (currentIndex == pathParts.size - 1) {
                // Found the target property
                results.add(
                    SearchResult(
                        file = file,
                        property = property,
                        fullPath = pathParts.joinToString("."),
                        value = formatValue(property)
                    )
                )
            } else {
                // Continue searching deeper
                when (val value = property.value) {
                    is JsonObject -> searchInObject(value, pathParts, currentIndex + 1, file, results)
                    is JsonArray -> {
                        // Search in array elements
                        for (element in value.valueList) {
                            if (element is JsonObject) {
                                searchInObject(element, pathParts, currentIndex + 1, file, results)
                            }
                        }
                    }
                }
            }
        }

        // Also search in nested objects (for cases where the path might start at any level)
        for (prop in jsonObject.propertyList) {
            when (val value = prop.value) {
                is JsonObject -> searchInObject(value, pathParts, 0, file, results)
                is JsonArray -> searchInArray(value, pathParts, file, results)
            }
        }
    }

    /**
     * Searches in JSON arrays for objects containing the property path.
     */
    private fun searchInArray(
        jsonArray: JsonArray,
        pathParts: List<String>,
        file: VirtualFile,
        results: MutableList<SearchResult>
    ) {
        for (element in jsonArray.valueList) {
            when (element) {
                is JsonObject -> searchInObject(element, pathParts, 0, file, results)
                is JsonArray -> searchInArray(element, pathParts, file, results)
            }
        }
    }

    /**
     * Formats the property value for display.
     */
    private fun formatValue(property: JsonProperty): String? {
        val value = property.value ?: return null
        val text = value.text

        // Clean up the value for display
        return when {
            text.startsWith("\"") && text.endsWith("\"") -> {
                // Remove quotes from string values
                text.substring(1, text.length - 1)
            }
            text.length > 100 -> {
                // Truncate long values
                text.take(100) + "..."
            }
            else -> text
        }
    }
}
