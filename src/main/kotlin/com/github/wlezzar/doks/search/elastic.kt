package com.github.wlezzar.doks.search

import com.github.wlezzar.doks.Document
import com.github.wlezzar.doks.SearchEngine
import com.github.wlezzar.doks.SearchResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import org.apache.http.HttpHost
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.index.IndexResponse
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.action.support.master.AcknowledgedResponse
import org.elasticsearch.client.IndicesClient
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.common.text.Text
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder


class ElasticEngine(
    hosts: List<HttpHost> = listOf(HttpHost("localhost", 9200, "http")),
    private val indexName: String = "documentation",
    private val typeName: String = "docs"
) : SearchEngine {

    private val client = RestHighLevelClient(RestClient.builder(*hosts.toTypedArray()))

    override suspend fun index(documents: Flow<Document>) = documents.collect { document ->
        client.indexSuspending(
            IndexRequest(indexName)
                .type(typeName)
                .id(document.id)
                .source(
                    mapOf(
                        "id" to document.id,
                        "title" to document.title,
                        "link" to document.link,
                        "source" to document.source,
                        "content" to document.content,
                        "metadata" to document.metadata
                    )
                ),
            RequestOptions.DEFAULT
        )
    }

    override suspend fun search(query: String): List<SearchResult> {
        val response = client.searchSuspending(
            SearchRequest(indexName).source(
                SearchSourceBuilder()
                    .query(QueryBuilders.simpleQueryStringQuery(query))
                    .highlighter(HighlightBuilder().field("content"))
            ),
            RequestOptions.DEFAULT
        )

        return response.hits.hits
            .asSequence()
            .map { hit ->
                val source = hit.sourceAsMap
                SearchResult(
                    document = Document(
                        id = source.getValue("id") as String,
                        source = source.getValue("source") as String,
                        title = source["title"]?.let { it as String } ?: "undefined",
                        link = source.getValue("link") as String,
                        content = source.getValue("content") as String,
                        metadata = source.getValue("metadata") as Map<String, String>
                    ),
                    score = hit.score,
                    matches = hit.highlightFields.mapValues { it.value.fragments.map(Text::string) }
                )
            }.toList()
    }

    override suspend fun purge() {
        client.indices().deleteSuspending(DeleteIndexRequest(indexName), RequestOptions.DEFAULT)
    }

    override fun close() = client.close()

}

private suspend fun IndicesClient.deleteSuspending(
    request: DeleteIndexRequest,
    options: RequestOptions
): AcknowledgedResponse =
    CompletableDeferred<AcknowledgedResponse>()
        .apply { deleteAsync(request, options, CompletableDeferredCompleter(this)) }
        .await()

private suspend fun RestHighLevelClient.searchSuspending(
    request: SearchRequest,
    options: RequestOptions
): SearchResponse =
    CompletableDeferred<SearchResponse>()
        .apply { searchAsync(request, options, CompletableDeferredCompleter(this)) }
        .await()

private suspend fun RestHighLevelClient.indexSuspending(request: IndexRequest, options: RequestOptions): IndexResponse =
    CompletableDeferred<IndexResponse>()
        .apply { indexAsync(request, options, CompletableDeferredCompleter(this)) }
        .await()

class CompletableDeferredCompleter<T>(private val future: CompletableDeferred<T>) : ActionListener<T> {
    override fun onResponse(response: T) {
        future.complete(response)
    }

    override fun onFailure(e: java.lang.Exception) {
        future.completeExceptionally(e)
    }

}