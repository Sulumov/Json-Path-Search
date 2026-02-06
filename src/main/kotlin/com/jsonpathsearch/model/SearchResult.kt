package com.jsonpathsearch.model

import com.intellij.json.psi.JsonProperty
import com.intellij.openapi.vfs.VirtualFile

/**
 * Represents a search result for a JSON property path.
 *
 * @property file The virtual file containing the JSON property
 * @property property The PSI element representing the JSON property
 * @property fullPath The full dot-notation path to the property (e.g., "balance.main.title")
 * @property value The string representation of the property value (truncated if too long)
 */
data class SearchResult(
    val file: VirtualFile,
    val property: JsonProperty,
    val fullPath: String,
    val value: String?
) {
    /**
     * Returns a display string for the popup list.
     */
    fun toDisplayString(): String {
        val fileName = file.name
        val displayValue = value?.take(50)?.let { 
            if (value.length > 50) "$it..." else it 
        } ?: "null"
        return "$fileName: $fullPath = $displayValue"
    }
    
    /**
     * Returns the text offset of the property in the file.
     */
    fun getTextOffset(): Int = property.textOffset
}
