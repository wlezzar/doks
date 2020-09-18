package com.github.wlezzar.doks.sources

import com.github.wlezzar.doks.Document
import com.github.wlezzar.doks.DocumentSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import java.nio.file.Files
import java.nio.file.Path

class FileSystemSource(
    private val sourceId: String,
    private val paths: List<Path>,
    private val include: List<Regex>,
    private val exclude: List<Regex>,
) : DocumentSource {

    override fun fetch(): Flow<Document> = channelFlow {
        for (path in paths) {
            launch(Dispatchers.IO) {
                Files
                    .walk(path)
                    .filter { path ->
                        include.all { "$path".matches(it) } && exclude.none { "$path".matches(it) }
                    }
                    .map {
                        Document(
                            id = "$it",
                            source = sourceId,
                            title = "${it.fileName}",
                            link = "$it",
                            content = it.toFile().readText(),
                            metadata = emptyMap(),
                        )
                    }
            }
        }
    }
}