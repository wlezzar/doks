package com.github.wlezzar.doks

import kotlinx.coroutines.flow.Flow
import java.io.Closeable

data class Document(
    val id: String,
    val source: String,
    val title: String,
    val link: String,
    val content: String,
    val metadata: Map<String, String>
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

sealed class Filter {
    data class Source(val value: String): Filter()
    data class Title(val value: String): Filter()
}
