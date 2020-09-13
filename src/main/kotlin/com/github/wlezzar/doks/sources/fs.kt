package com.github.wlezzar.doks.sources

import com.github.wlezzar.doks.Document
import com.github.wlezzar.doks.DocumentSource
import com.github.wlezzar.doks.DocumentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import java.nio.file.Files
import java.nio.file.Path

@ExperimentalCoroutinesApi
class FileSystemSource(
    private val path: Path,
    private val filter: (Path) -> Boolean = { "$it".endsWith(".md") }
) : DocumentSource {

    override fun fetch(): Flow<Document> = channelFlow {
        launch(Dispatchers.IO) {
            Files
                .walk(path)
                .filter(filter)
                .map {
                    Document(
                        id = "$it",
                        type = DocumentType.Markdown,
                        title = "${it.fileName}",
                        link = "$it",
                        content = it.toFile().readText()
                    )
                }
        }
    }
}