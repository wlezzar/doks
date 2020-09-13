package com.github.wlezzar.doks

import com.fasterxml.jackson.databind.JsonNode
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.wlezzar.doks.utils.jsonObject
import com.github.wlezzar.doks.utils.toJsonNode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * CLI implementation.
 */
@ExperimentalCoroutinesApi
class Doks : NoOpCliktCommand(name = "doks") {
    private val configFile: File?
        by option("-c", "--config", envvar = "DOKS_CONFIG_FILE").file(mustExist = true, canBeDir = false)

    private val config: Config by lazy {
        val file = configFile
            ?: File("${System.getenv("HOME") ?: error("couldn't locate user home")}/.doks/config.yml")

        Config.fromFile(file)
    }

    private fun <T> useSearch(action: suspend (SearchEngine) -> T): T = runBlocking {
        config.engine.resolve().use { action(it) }
    }

    init {
        subcommands(Index(), Search(), Purge())
    }

    @ExperimentalCoroutinesApi
    inner class Index : CliktCommand(name = "index", help = "index all documentation sources into the search engine") {
        override fun run() = this@Doks.useSearch { search ->
            this@Doks.config.sources.asSequence().flatMap { it.resolve() }.forEach {
                search.index(it.fetch())
            }
        }
    }

    inner class Purge : CliktCommand(name = "purge", help = "delete all indexing from the search engine") {
        override fun run() = this@Doks.useSearch { it.purge() }
    }

    inner class Search : CliktCommand(name = "search", help = "query the search engine to retrieve indexed docs") {
        private val query: String by argument("query")

        override fun run(): Unit = runBlocking {
            this@Doks.useSearch { search ->
                val result = search.search(query)
                if (result.isEmpty()) echo("No matches found!", err = true) else
                    echo(
                        result
                            .map {
                                jsonObject {
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