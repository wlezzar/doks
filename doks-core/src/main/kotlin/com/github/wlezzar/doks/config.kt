package com.github.wlezzar.doks

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonValue
import com.github.wlezzar.doks.search.ElasticEngine
import com.github.wlezzar.doks.search.LuceneSearchEngine
import com.github.wlezzar.doks.sources.FileSystemSource
import com.github.wlezzar.doks.sources.GitRepository
import com.github.wlezzar.doks.sources.GithubSearchLister
import com.github.wlezzar.doks.sources.GithubSource
import com.github.wlezzar.doks.sources.GoogleDriveSource
import com.github.wlezzar.doks.sources.StaticRepositoryLister
import com.github.wlezzar.doks.sources.cached
import com.github.wlezzar.doks.utils.json
import com.github.wlezzar.doks.utils.toValue
import com.github.wlezzar.doks.utils.yaml
import org.apache.http.HttpHost
import java.io.File
import java.nio.file.Paths
import java.time.Duration
import com.github.wlezzar.doks.sources.GitCloneTransport as DomainGitCloneTransport

/**
 * Main application config model
 */
data class Config(
    val sources: List<SourceConfig>,
    val engine: SearchEngineConfig
) {
    companion object
}

enum class GitCloneTransport(@JsonValue val code: String) { Ssh("ssh"), Https("https") }

fun GitCloneTransport.toDomain(): DomainGitCloneTransport = when (this) {
    GitCloneTransport.Ssh -> DomainGitCloneTransport.Ssh
    GitCloneTransport.Https -> DomainGitCloneTransport.Https
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "source")
@JsonSubTypes(
    JsonSubTypes.Type(value = SourceConfig.Github::class, name = "github"),
    JsonSubTypes.Type(value = SourceConfig.FileSystem::class, name = "fs"),
    JsonSubTypes.Type(value = SourceConfig.GoogleDrive::class, name = "googleDrive"),
)
sealed class SourceConfig {
    data class Github(
        val id: String,
        val repositories: GithubRepositoriesConfig,
        val transport: GitCloneTransport = GitCloneTransport.Ssh,
        val include: List<String> = listOf("""^.*\.md$"""),
        val exclude: List<String> = emptyList(),
        val concurrency: Int = 2
    ) : SourceConfig()

    data class FileSystem(
        val id: String,
        val paths: List<String>,
        val include: List<String> = listOf("""^.*\.md$"""),
        val exclude: List<String> = emptyList()
    ) : SourceConfig()

    data class GoogleDrive(
        val id: String,
        val secretFile: String,
        val searchQuery: String?,
        val driveId: String?,
        val folders: List<String> = emptyList(),
        val concurrency: Int = 4
    ) : SourceConfig()
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

data class GithubRepo(
    val name: String,
    val folder: String? = null,
    val branch: String = "master",
    val include: List<String>? = null,
    val exclude: List<String>? = null
)

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

fun SourceConfig.resolve(): DocumentSource = when (this) {
    is SourceConfig.Github -> GithubSource(
        sourceId = id,
        concurrency = concurrency,
        repositories = when (repositories) {
            is GithubRepositoriesConfig.FromList ->
                StaticRepositoryLister(
                    list = repositories.list.map {
                        GitRepository(
                            url = when (transport) {
                                GitCloneTransport.Ssh -> "git@${repositories.server}:${it.name}.git"
                                GitCloneTransport.Https -> "https://${repositories.server}/${it.name}.git"
                            },
                            name = it.name,
                            folder = it.folder,
                            branch = it.branch,
                            include = (it.include ?: include).map(::Regex),
                            exclude = (it.exclude ?: exclude).map(::Regex),
                            server = repositories.server
                        )
                    }
                )

            is GithubRepositoriesConfig.FromApi -> GithubSearchLister(
                starredBy = repositories.starredBy,
                search = repositories.search,
                transport = transport.toDomain(),
                endpoint = repositories.endpoint,
                tokenFile = repositories.tokenFile,
                include = include.map(::Regex),
                exclude = exclude.map(::Regex),
            ).cached(maxCacheDuration = Duration.ofMinutes(10))
        }
    )

    is SourceConfig.FileSystem -> FileSystemSource(
        sourceId = id,
        paths = paths.map(Paths::get),
        include = include.map(::Regex),
        exclude = exclude.map(::Regex),
    )

    is SourceConfig.GoogleDrive -> GoogleDriveSource(
        sourceId = id,
        secretFile = File(secretFile),
        searchQuery = searchQuery,
        driveId = driveId,
        folders = folders,
        concurrency = concurrency
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