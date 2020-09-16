package com.github.wlezzar.doks

import com.fasterxml.jackson.databind.JsonNode
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.defaultLazy
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.wlezzar.doks.utils.jsonObject
import com.github.wlezzar.doks.utils.toJsonNode
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.logging.LogManager

/**
 * CLI implementation.
 */
class Doks : CliktCommand(name = "doks") {
    private val doksHome
        by option("--home", envvar = "DOKS_HOME", hidden = true)
            .defaultLazy { "${System.getenv("HOME") ?: error("couldn't locate user home")}/.doks" }

    private val namespace: String
        by option("-n", "--namespace", envvar = "DOKS_NAMESPACE").default("default")

    private val configFile: File?
        by option("-c", "--config", envvar = "DOKS_CONFIG_FILE").file(mustExist = true, canBeDir = false)

    private val config: Config by lazy {
        val file = configFile
            ?: File("${doksHome}/config/${namespace}.yml")

        Config.fromFile(file)
    }

    private fun <T> useSearch(action: suspend (SearchEngine) -> T): T = runBlocking {
        config.engine.resolve(home = doksHome, namespace = namespace).use { action(it) }
    }

    init {
        subcommands(Index(), Search(), Purge())
    }

    inner class Index : CliktCommand(name = "index", help = "index all documentation sources into the search engine") {
        override fun run() = this@Doks.useSearch { search ->
            this@Doks.config.sources.asSequence().forEach {
                search.index(it.resolve().fetch())
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
                                    put("title", it.document.title)
                                    set<JsonNode>("matches", it.matches.toJsonNode())
                                }
                            }
                            .toJsonNode()
                    )
            }
        }
    }

    override fun run() {
//        LogManager.getRootLogger().level = when {
//            silent -> Level.ERROR
//            verbosity <= 1 -> Level.INFO
//            verbosity == 2 -> Level.DEBUG
//            else -> Level.ALL
//        }
    }
}