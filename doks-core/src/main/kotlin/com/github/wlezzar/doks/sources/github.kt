package com.github.wlezzar.doks.sources

import com.github.wlezzar.doks.Document
import com.github.wlezzar.doks.DocumentSource
import com.github.wlezzar.doks.utils.Hasher
import com.github.wlezzar.doks.utils.parseJson
import com.github.wlezzar.doks.utils.retryable
import com.github.wlezzar.doks.utils.tmpDir
import com.github.wlezzar.doks.utils.toJsonNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.kohsuke.github.GitHubBuilder
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.System.currentTimeMillis
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.*
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger

private val logger = LoggerFactory.getLogger(GithubSource::class.java)

/**
 * Source that clones repositories and scans them for documents.
 */
class GithubSource(
    private val sourceId: String,
    private val repositories: GitRepositoryLister,
    private val concurrency: Int
) : DocumentSource {

    override fun fetch(): Flow<Document> =
        repositories.list().flatMapMerge(concurrency = concurrency) { repo -> extractDocuments(repo) }

    private suspend fun extractDocuments(repository: GitRepository) = flow {
        withRepositoryCloned(repository.url) { cloned ->
            val counter = AtomicInteger(0)
            val baseDir = repository.folder?.let { cloned.resolve(it) } ?: cloned
            listRecursively(baseDir)
                .filter { path ->
                    repository.include.all { "$path".matches(it) } && repository.exclude.none { "$path".matches(it) }
                }
                .map {
                    val relativePath = cloned.relativize(it)
                    Document(
                        id = "${repository.name}/$relativePath",
                        source = sourceId,
                        title = "${it.fileName}",
                        link = "https://${repository.server}/${repository.name}/blob/${repository.branch}/$relativePath",
                        content = it.toFile().readText(),
                        metadata = mapOf("repository" to repository.name)
                    )
                }
                .onEach { counter.incrementAndGet() }
                .onCompletion { logger.info("[${repository.name}] $counter documents found!") }
                .let { emitAll(it) }

        }
    }
}

data class GitRepository(
    val url: String,
    val name: String,
    val include: List<Regex>,
    val exclude: List<Regex>,
    val folder: String?,
    val branch: String,
    val server: String,
)

interface Hashable {
    fun hash(): String
}

/**
 * List git repositories
 */
interface GitRepositoryLister {
    fun list(): Flow<GitRepository>
}

@Suppress("BlockingMethodInNonBlockingContext")
private suspend fun <T> withRepositoryCloned(url: String, action: suspend (Path) -> T): T {
    val dest = Paths.get("$tmpDir/doks-repo-${UUID.randomUUID()}")
    try {
        withContext(Dispatchers.IO) {
            Files.createDirectories(dest)
            retryable(
                delayBetweenRetries = Duration.ofSeconds(3),
                retryOn = { exc -> exc is RejectedExecutionException },
                maxRetries = null,
                messageOnError = { "Repo cloning rate limiting exceeded" }
            ) {
                Git
                    .cloneRepository()
                    .setURI(url)
                    .setDirectory(dest.toFile())
                    .also { logger.info("cloning '${url}' into '$dest'") }
                    .call()
            }
        }

        return action(dest)
    } finally {
        withContext(Dispatchers.IO) {
            dest.toFile().deleteRecursively()
        }
    }
}

private fun listRecursively(path: Path): Flow<Path> = channelFlow {
    launch(Dispatchers.IO) {
        for (child in Files.walk(path)) {
            send(child)
        }
    }
}

class StaticRepositoryLister(private val list: List<GitRepository>) : GitRepositoryLister {
    override fun list(): Flow<GitRepository> = list.asFlow()
}

class GithubSearchLister(
    private val include: List<Regex>,
    private val exclude: List<Regex>,
    private val starredBy: List<String>?,
    private val search: String?,
    private val transport: String,
    private val endpoint: String?,
    private val tokenFile: String?,
) : GitRepositoryLister, Hashable {
    private val github =
        GitHubBuilder()
            .apply {
                endpoint?.let(this::withEndpoint)
                tokenFile?.let { withOAuthToken(File(it).readText().trim()) }
            }
            .build()

    override fun list(): Flow<GitRepository> {
        return listOfNotNull(starredBy?.let(this::starredBy), search?.let(this::search)).merge()
    }

    private fun starredBy(users: List<String>): Flow<GitRepository> = channelFlow {
        launch(Dispatchers.IO) {
            for (user in users) {
                logger.info("fetching repositories starred by: $user")
                for (starred in github.getUser(user).listStarredRepositories()) {
                    send(
                        GitRepository(
                            url = when (transport) {
                                "git" -> starred.gitTransportUrl
                                "http" -> starred.httpTransportUrl
                                else -> throw IllegalArgumentException("unknown transport mode: $transport")
                            },
                            include = include,
                            exclude = exclude,
                            name = starred.fullName,
                            server = URI(starred.gitTransportUrl).host,
                            branch = starred.defaultBranch,
                            folder = null
                        )
                    )
                }
            }
        }
    }

    private fun search(q: String): Flow<GitRepository> = channelFlow {
        launch(Dispatchers.IO) {
            logger.info("searching repositories with query: $q")
            for (repo in github.searchRepositories().q(q).list()) {
                send(
                    GitRepository(
                        url = when (transport) {
                            "git" -> repo.gitTransportUrl
                            "http" -> repo.httpTransportUrl
                            else -> throw IllegalArgumentException("unknown transport mode: $transport")
                        },
                        include = include,
                        exclude = exclude,
                        name = repo.fullName,
                        branch = repo.defaultBranch,
                        server = URL(endpoint).host,
                        folder = null
                    )
                )
            }
        }
    }

    override fun hash(): String = Hasher.hash(
        *listOfNotNull(search, endpoint, tokenFile).toTypedArray(),
        *(starredBy?.toTypedArray() ?: emptyArray()),
    )
}

class GitRepositoryListerCache<T>(
    private val cacheMaxDuration: Duration,
    private val wrapped: T
) : GitRepositoryLister
    where T : GitRepositoryLister, T : Hashable {

    private val cache = File("$tmpDir/doks/sources-cache/${wrapped.hash()}.txt")

    override fun list(): Flow<GitRepository> = channelFlow {
        launch(Dispatchers.IO) {
            if (!cache.exists() || (cache.lastModified() + cacheMaxDuration.toMillis()) < currentTimeMillis()) {
                logger.info("refreshing cache: ${cache.absolutePath}")
                val tempCache = File("${cache.absolutePath}.tmp")
                tempCache.parentFile.mkdirs()
                tempCache.outputStream().bufferedWriter(Charsets.UTF_8).use { writer ->
                    wrapped.list().collect { writer.appendLine(it.toJsonNode().toString()) }
                }
                Files.move(tempCache.toPath(), cache.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }

            cache.useLines { it.forEach { line -> send(line.parseJson()) } }
        }
    }
}

fun <T> T.cached(maxCacheDuration: Duration): GitRepositoryLister where T : GitRepositoryLister, T : Hashable =
    GitRepositoryListerCache(maxCacheDuration, this)