package com.github.wlezzar.doks

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.github.wlezzar.doks.search.ElasticEngine
import com.github.wlezzar.doks.search.LuceneSearchEngine
import com.github.wlezzar.doks.sources.FileSystemSource
import com.github.wlezzar.doks.sources.GSuite
import com.github.wlezzar.doks.sources.GithubSource
import com.github.wlezzar.doks.sources.GoogleDocs
import com.github.wlezzar.doks.utils.json
import com.github.wlezzar.doks.utils.toValue
import com.github.wlezzar.doks.utils.yaml
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.apache.http.HttpHost
import java.io.File
import java.nio.file.Paths

/**
 * Main application config model
 */
data class Config(
    val sources: List<SourceConfig>,
    val engine: SearchEngineConfig
) {
    companion object
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "source")
@JsonSubTypes(
    JsonSubTypes.Type(value = SourceConfig.Github::class, name = "github"),
    JsonSubTypes.Type(value = SourceConfig.FileSystem::class, name = "fs"),
    JsonSubTypes.Type(value = SourceConfig.GoogleDrive::class, name = "googleDrive"),
)
sealed class SourceConfig {
    data class Github(
        val repositories: List<GithubRepo>,
        val server: String = "github.com",
        val transport: String = "git",
    ) : SourceConfig()

    data class FileSystem(val paths: List<String>) : SourceConfig()

    data class GoogleDrive(val secretFile: String, val folders: List<String> = emptyList()) : SourceConfig()
}

data class GithubRepo(val name: String, val folder: String? = null, val branch: String = "master")

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = SearchEngineConfig.ElasticSearch::class, name = "elasticsearch"),
    JsonSubTypes.Type(value = SearchEngineConfig.Lucene::class, name = "lucene"),
)
sealed class SearchEngineConfig {
    data class ElasticSearch(
        val index: String = "documentation",
        val type: String = "docs",
        val host: String = "localhost",
        val port: Int = 9092,
        val scheme: String = "http"
    ) : SearchEngineConfig()

    data class Lucene(val path: String) : SearchEngineConfig()
}

fun Config.Companion.fromFile(file: File): Config {
    val mapper = when (file.extension) {
        "json" -> json
        in listOf("yaml", "yml") -> yaml
        else -> throw IllegalArgumentException("unsupported file format: $file")
    }

    return mapper.readTree(file).toValue()
}

@ExperimentalCoroutinesApi
fun SourceConfig.resolve(): List<DocumentSource> = when (this) {
    is SourceConfig.Github -> repositories.map { repository ->
        GithubSource(
            transport = transport,
            server = server,
            branch = repository.branch,
            folder = repository.folder,
            repository = repository.name
        )
    }
    is SourceConfig.FileSystem -> paths.map { path -> FileSystemSource(path = Paths.get(path)) }
    is SourceConfig.GoogleDrive -> listOf(
        GoogleDocs(
            gSuite = GSuite(secretFile = File(secretFile), userId = "doks"),
            folders = folders
        )
    )
}

fun SearchEngineConfig.resolve(): SearchEngine = when (this) {
    is SearchEngineConfig.ElasticSearch -> ElasticEngine(
        hosts = listOf(HttpHost(host, port, scheme)),
        indexName = index,
        typeName = type,
    )
    is SearchEngineConfig.Lucene -> LuceneSearchEngine(
        path = Paths.get(path)
    )
}