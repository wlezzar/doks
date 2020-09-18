package com.github.wlezzar.doks.sources

import com.github.wlezzar.doks.Document
import com.github.wlezzar.doks.DocumentSource
import com.github.wlezzar.doks.utils.retryable
import com.github.wlezzar.doks.utils.useTemporaryDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.eclipse.jgit.api.Git
import org.kohsuke.github.GitHubBuilder
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.RejectedExecutionException
import kotlin.streams.asSequence

private val logger = LoggerFactory.getLogger(GithubSource::class.java)

/**
 * Source that clones a repository and scans it for documents.
 */
class GithubSource(
    private val sourceName: String,
    private val repository: String,
    private val include: List<Regex>,
    private val exclude: List<Regex>,
    private val folder: String? = null,
    private val transport: String = "git",
    private val server: String = "github.mpi-internal.com",
    private val branch: String = "master"
) : DocumentSource {

    private val semaphore = Semaphore(permits = 4)

    override fun fetch(): Flow<Document> = channelFlow {
        launch(Dispatchers.IO) {
            useTemporaryDirectory(prefix = "doks") { tmpClone ->
                val url = "$transport@$server:$repository.git"
                // we need to limit the concurrency of the git clone command here as the library starts erroring
                retryable(
                    delayBetweenRetries = Duration.ofSeconds(3),
                    retryOn = { exc -> exc is RejectedExecutionException },
                    maxRetries = null,
                    messageOnError = { "Repo cloning rate limiting exceeded" }
                ) {
                    semaphore.withPermit {
                        Git
                            .cloneRepository()
                            .setURI(url)
                            .setDirectory(tmpClone.toFile())
                            .also { logger.info("[$repository] cloning '${url}' into '$tmpClone'") }
                            .call()
                    }
                }

                var counter = 0

                Files
                    .walk(folder?.let { tmpClone.resolve(folder) } ?: tmpClone)
                    .filter { path ->
                        include.all { "$path".matches(it) } && exclude.none { "$path".matches(it) }
                    }
                    .map {
                        val relativePath = tmpClone.relativize(it)
                        Document(
                            id = "$repository/$relativePath",
                            source = sourceName,
                            title = "${it.fileName}",
                            link = "https://$server/$repository/blob/$branch/$relativePath",
                            content = it.toFile().readText(),
                            metadata = mapOf("repository" to repository)
                        )
                    }
                    .asSequence()
                    .forEach {
                        send(it)
                        counter += 1
                    }

                logger.info("[$repository] $counter documents found!")
            }
        }
    }

    companion object
}

/**
 * Source that takes a static list of github repositories and fetches documents from them.
 */
class GithubRepoListSource(
    private val sourceId: String,
    private val repositories: List<Repository>
) : DocumentSource {

    data class Repository(
        val repository: String,
        val include: List<Regex>,
        val exclude: List<Regex>,
        val folder: String? = null,
        val transport: String = "git",
        val server: String = "github.com",
        val branch: String = "master",
    )

    override fun fetch(): Flow<Document> =
        repositories
            .asFlow()
            .map {
                GithubSource(
                    sourceName = sourceId,
                    repository = it.repository,
                    include = it.include,
                    exclude = it.exclude,
                    folder = it.folder,
                    transport = it.transport,
                    server = it.server,
                    branch = it.branch
                )
            }
            .flatMapMerge(concurrency = 4) { it.fetch() }

}

/**
 * Source that fetches a list of github repositories from a search request and scans them for documents.
 */
class GithubSearchSource(
    private val sourceId: String,
    private val include: List<Regex>,
    private val exclude: List<Regex>,
    private val starredBy: List<String>?,
    private val search: String?,
    private val transport: String,
    private val endpoint: String?,
    private val tokenFile: String?
) : DocumentSource {

    private val github =
        GitHubBuilder()
            .apply {
                endpoint?.let(this::withEndpoint)
                tokenFile?.let { withOAuthToken(File(it).readText().trim()) }
            }
            .build()

    override fun fetch(): Flow<Document> {

        val sources =
            listOfNotNull(
                starredBy?.let(this::starredBy),
                search?.let(this::search),
            ).merge()

        return sources.flatMapMerge(concurrency = 16) { it.fetch() }
    }

    private fun starredBy(users: List<String>): Flow<GithubSource> = channelFlow {
        launch(Dispatchers.IO) {
            for (user in users) {
                for (starred in github.getUser(user).listStarredRepositories()) {
                    send(
                        GithubSource(
                            sourceName = sourceId,
                            include = include,
                            exclude = exclude,
                            repository = starred.fullName,
                            transport = transport,
                            server = URI(starred.gitTransportUrl).host,
                            branch = starred.defaultBranch
                        )
                    )
                }
            }
        }
    }

    private fun search(q: String): Flow<GithubSource> = channelFlow {
        launch(Dispatchers.IO) {
            for (repo in github.searchRepositories().q(q).list()) {
                send(
                    GithubSource(
                        sourceName = sourceId,
                        include = include,
                        exclude = exclude,
                        repository = repo.fullName,
                        transport = transport,
                        server = repo.url.host,
                        branch = repo.defaultBranch
                    )
                )
            }
        }
    }

}
