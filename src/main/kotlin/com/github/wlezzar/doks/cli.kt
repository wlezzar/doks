package com.github.wlezzar.doks

import com.fasterxml.jackson.databind.JsonNode
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import com.github.wlezzar.doks.utils.jsonObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * CLI implementation.
 */
@ExperimentalCoroutinesApi
class Docx : NoOpCliktCommand(name = "docx") {
    private val configPath: File
        by option("-c", "--config", envvar = "DOCX_CONFIG_PATH")
            .file(mustExist = true, canBeDir = false)
            .required()

    private val config: Config by lazy { Config.fromFile(configPath) }

    private fun <T> useSearch(action: suspend (SearchEngine) -> T): T =
        runBlocking { config.engine.resolve().use { action(it) } }

    init {
        subcommands(Index(), Search(), Purge())
    }

    @ExperimentalCoroutinesApi
    inner class Index : CliktCommand(name = "index") {
        override fun run() = this@Docx.useSearch { search ->
            this@Docx.config.sources.asSequence().flatMap { it.resolve() }.forEach {
                search.index(it.fetch())
            }
        }
    }

    inner class Purge : CliktCommand(name = "purge") {
        override fun run() = this@Docx.useSearch { it.purge() }
    }

    inner class Search : CliktCommand(name = "search") {
        private val query: String by argument("query")

        override fun run(): Unit = runBlocking {
            this@Docx.useSearch { search ->
                val result = search.search(query)
                if (result.isEmpty()) echo("No matches found!", err = true) else
                    echo(
                        result
                            .map {
                                jsonObject {
                                    put("title", it.document.title)
                                    put("link", it.document.link)
                                    put("score", it.score)
                                    set<JsonNode>("matches", it.matches.toJsonNode())
                                }
                            }
                            .toJsonNode()
                    )
            }
        }
    }
}