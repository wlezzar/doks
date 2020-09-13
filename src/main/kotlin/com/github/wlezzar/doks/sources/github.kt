package com.github.wlezzar.doks.sources

import com.github.wlezzar.doks.Document
import com.github.wlezzar.doks.DocumentSource
import com.github.wlezzar.doks.DocumentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import org.eclipse.jgit.api.Git
import org.slf4j.LoggerFactory
import java.nio.file.Files

@ExperimentalCoroutinesApi
private val logger = LoggerFactory.getLogger(GithubSource::class.java)

@ExperimentalCoroutinesApi
class GithubSource(
    private val repository: String,
    private val folder: String? = null,
    private val transport: String = "git",
    private val server: String = "github.mpi-internal.com",
    private val branch: String = "master"
) : DocumentSource {

    override fun fetch(): Flow<Document> = channelFlow {
        launch(Dispatchers.IO) {
            val tmpClone = Files.createTempDirectory("doks").also { it.toFile().deleteOnExit() }
            Git
                .cloneRepository()
                .setURI("$transport@$server:$repository.git")
                .setDirectory(tmpClone.toFile())
                .also { logger.error("[$repository] cloning into '$tmpClone'") }
                .call()

            val base = folder?.let { tmpClone.resolve(folder) } ?: tmpClone
            var counter = 0

            Files
                .walk(base)
                .filter { "$it".endsWith(".md") }
                .map {
                    val relativePath = base.relativize(it)
                    Document(
                        id = "$repository/$relativePath",
                        type = DocumentType.Markdown,
                        title = "${it.fileName}",
                        link = "https://$server/$repository/blob/$branch/$relativePath",
                        content = it.toFile().readText()
                    )
                }
                .forEach {
                    sendBlocking(it)
                    counter += 1
                }

            logger.info("[$repository] $counter documents found!")
        }
    }
}

