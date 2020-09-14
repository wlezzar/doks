package com.github.wlezzar.doks

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.github.wlezzar.doks.search.ElasticEngine
import com.github.wlezzar.doks.search.LuceneSearchEngine
import com.github.wlezzar.doks.sources.FileSystemSource
import com.github.wlezzar.doks.sources.GSuite
import com.github.wlezzar.doks.sources.GithubRepoListSource
import com.github.wlezzar.doks.sources.GithubSearchSource
import com.github.wlezzar.doks.sources.GoogleDocs
import com.github.wlezzar.doks.utils.json
import com.github.wlezzar.doks.utils.toValue
import com.github.wlezzar.doks.utils.yaml
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
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
        val repositories: GithubRepositoriesConfig,
        val transport: String = "git",
    ) : SourceConfig()

    data class FileSystem(val paths: List<String>) : SourceConfig()

    data class GoogleDrive(val secretFile: String, val folders: List<String> = emptyList()) : SourceConfig()
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "from")
@JsonSubTypes(
    JsonSubTypes.Type(value = GithubRepositoriesConfig.FromList::class, name = "list"),
    JsonSubTypes.Type(value = GithubRepositoriesConfig.FromApi::class, name = "api"),
)
sealed class GithubRepositoriesConfig {
    data class FromList(
        val server: String = "github.com",
        val list: List<GithubRepo>
    ) : GithubRepositoriesConfig()

    data class FromApi(
        val search: String?,
        val starredBy: List<String>?,
        val endpoint: String?,
        val tokenFile: String?
    ) : GithubRepositoriesConfig()
}

data class GithubRepo(val name: String, val folder: String? = null, val branch: String = "master")

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "use")
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

    data class Lucene(val path: String?) : SearchEngineConfig()
}

fun Config.Companion.fromFile(file: File): Config {
    val mapper = when (file.extension) {
        "json" -> json
        in listOf("yaml", "yml") -> yaml
        else -> throw IllegalArgumentException("unsupported file format: $file")
    }

    return file.readText()
        .takeIf { it.isNotBlank() }
        ?.let { mapper.readTree(it).toValue<Config>() }
        ?: throw IllegalArgumentException("file is empty: $file")
}

@FlowPreview
@ExperimentalCoroutinesApi
fun SourceConfig.resolve(): DocumentSource = when (this) {
    is SourceConfig.Github -> when (repositories) {
        is GithubRepositoriesConfig.FromList ->
            repositories
                .list
                .map {
                    GithubRepoListSource.Repository(
                        repository = it.name,
                        folder = it.folder,
                        transport = transport,
                        server = repositories.server,
                        branch = it.branch,
                    )
                }
                .let(::GithubRepoListSource)

        is GithubRepositoriesConfig.FromApi -> GithubSearchSource(
            starredBy = repositories.starredBy,
            search = repositories.search,
            transport = transport,
            endpoint = repositories.endpoint,
            tokenFile = repositories.tokenFile,
        )
    }
    is SourceConfig.FileSystem -> FileSystemSource(paths = paths.map(Paths::get))
    is SourceConfig.GoogleDrive -> GoogleDocs(
        gSuite = GSuite(secretFile = File(secretFile), userId = "doks"),
        folders = folders
    )
}

fun SearchEngineConfig.resolve(home: String, namespace: String): SearchEngine = when (this) {
    is SearchEngineConfig.ElasticSearch -> ElasticEngine(
        hosts = listOf(HttpHost(host, port, scheme)),
        indexName = index,
        typeName = type,
    )
    is SearchEngineConfig.Lucene -> LuceneSearchEngine(
        path = Paths.get(path ?: "$home/storage/lucene/$namespace")
    )
}