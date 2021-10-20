package com.github.wlezzar.doks.search

import com.github.wlezzar.doks.Document
import com.github.wlezzar.doks.SearchEngine
import com.github.wlezzar.doks.SearchResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.en.EnglishAnalyzer
import org.apache.lucene.document.Field
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.IndexableField
import org.apache.lucene.index.Term
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.Query
import org.apache.lucene.search.highlight.Highlighter
import org.apache.lucene.search.highlight.QueryScorer
import org.apache.lucene.search.highlight.SimpleSpanFragmenter
import org.apache.lucene.store.FSDirectory
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.concurrent.thread
import org.apache.lucene.document.Document as LuceneDocument

private val logger = LoggerFactory.getLogger(LuceneSearchEngine::class.java)

class LuceneSearchEngine(path: Path, private val analyzer: Analyzer = EnglishAnalyzer()) : SearchEngine {

    private val executor = Executors.newCachedThreadPool()

    private val indexWriter = IndexWriter(FSDirectory.open(path), IndexWriterConfig(analyzer))
    private val indexReader = DirectoryReader.open(indexWriter)
    private val indexSearcher = IndexSearcher(indexReader)

    override suspend fun index(documents: Flow<Document>) {
        indexWriter.launchThread().use {
            documents.collect { document ->
                it.index(
                    document.id,
                    listOf(
                        StringField("id", document.id, Field.Store.YES),
                        StringField("link", document.link, Field.Store.YES),
                        StringField("source", document.source, Field.Store.YES),
                        TextField("title", document.title, Field.Store.YES),
                        TextField("content", document.content, Field.Store.YES),
                    ) + document.metadata.map { StringField("metadata.${it.key}", it.value, Field.Store.YES) }
                )
            }
        }
    }

    override suspend fun search(query: String): List<SearchResult> = executor.submitSuspending {
        val parsedQuery = QueryParser("content", analyzer).parse(query)
        val result = indexSearcher.search(parsedQuery, 100).scoreDocs

        result.map { doc ->
            val retrieved = indexSearcher.doc(doc.doc)

            SearchResult(
                document = Document(
                    id = retrieved.getRequiredField("id").stringValue(),
                    title = retrieved.getRequiredField("title").stringValue(),
                    source = retrieved.getRequiredField("source").stringValue(),
                    link = retrieved.getRequiredField("link").stringValue(),
                    content = retrieved.getRequiredField("content").stringValue(),
                    metadata = retrieved.fields
                        .filter { it.name().startsWith("metadata.") }
                        .associate { it.name().replace("metadata.", "") to it.stringValue() }
                ),
                score = doc.score,
                matches = listOf("content").associateWith {
                    retrieved.fragments(
                        fieldName = it,
                        query = parsedQuery,
                        analyzer = analyzer,
                        numberOfFragments = 3
                    )
                }
            )
        }
    }


    override suspend fun purge() {
        executor.submitSuspending {
            indexWriter.deleteAll()
        }
    }

    override fun close() {
        indexWriter.close()
        executor.shutdown()
    }
}

private fun LuceneDocument.fragments(fieldName: String, query: Query, analyzer: Analyzer, numberOfFragments: Int): List<String> {
    val field = getRequiredField(fieldName)
    val queryScorer = QueryScorer(query, fieldName)
    val tokenStream = field.tokenStream(analyzer, null)  // think about reuse here?
    return Highlighter(queryScorer)
        .apply { textFragmenter = SimpleSpanFragmenter(queryScorer, 400) }
        .getBestFragments(tokenStream, field.stringValue(), numberOfFragments).toList()
}

private fun LuceneDocument.getRequiredField(name: String) =
    getField(name) ?: throw IllegalArgumentException("field not found: $name")

data class IndexSuspendingRequest(val id: String, val doc: Iterable<IndexableField>, val completer: CompletableDeferred<Long>)

fun IndexWriter.launchThread(): SendChannel<IndexSuspendingRequest> {
    val channel = Channel<IndexSuspendingRequest>()

    @Suppress("BlockingMethodInNonBlockingContext")
    thread(name = "indexer") {
        runBlocking {
            // committer coroutine
            launch {
                do {
                    delay(5000)
                    if (hasUncommittedChanges()) {
                        logger.info("committing")
                        commit()
                    }
                } while (!channel.isClosedForReceive)
            }

            // receiver coroutine
            launch {
                for (req in channel) {
                    runCatching {
                        updateDocument(
                            Term("_id", req.id),
                            req.doc + StringField("_id", req.id, Field.Store.YES)
                        )
                    }.fold(
                        onSuccess = req.completer::complete,
                        onFailure = req.completer::completeExceptionally
                    )
                }
            }
        }
    }

    return channel
}

suspend fun SendChannel<IndexSuspendingRequest>.index(id: String, doc: Iterable<IndexableField>): Long =
    CompletableDeferred<Long>()
        .apply { send(IndexSuspendingRequest(id, doc, this)) }
        .await()

private suspend fun <T> ExecutorService.submitSuspending(action: () -> T): T =
    CompletableFuture.supplyAsync(action, this).await()

private inline fun <T, R> SendChannel<T>.use(block: (SendChannel<T>) -> R): R =
    Closeable { close() }.use { block(this) }

