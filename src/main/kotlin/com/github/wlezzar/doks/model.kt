package com.github.wlezzar.doks

import kotlinx.coroutines.flow.Flow
import java.io.Closeable

enum class DocumentType {
    GoogleDoc, GoogleSlide, Markdown, Unknown
}

data class Document(
    val id: String,
    val type: DocumentType,
    val title: String,
    val link: String,
    val content: String
)

interface DocumentSource {
    fun fetch(): Flow<Document>
}

data class SearchResult(val document: Document, val score: Float, val matches: Map<String, List<String>>)

interface SearchEngine : Closeable {
    suspend fun index(documents: Flow<Document>)
    suspend fun search(query: String): List<SearchResult>
    suspend fun purge()
}