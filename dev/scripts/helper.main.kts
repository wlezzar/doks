#!/usr/bin/env kotlin

@file:Repository("https://repo.maven.apache.org/maven2/")
@file:DependsOn("org.freemarker:freemarker:2.3.30")
@file:DependsOn("com.github.ajalt.clikt:clikt-jvm:3.0.1")
@file:DependsOn("commons-codec:commons-codec:1.11")

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.associate
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import freemarker.cache.StringTemplateLoader
import freemarker.template.Configuration
import freemarker.template.TemplateExceptionHandler
import org.apache.commons.codec.digest.DigestUtils
import java.net.URL


NoOpCliktCommand(name = "helper")
    .subcommands(
        FormulaGenerator(),
        Template(),
    )
    .main(args)


/**
 * Generates the brew formula given a version and a package
 */
class FormulaGenerator : CliktCommand(name = "formula") {
    private val url by option("--url").required()
    private val version by option("-v", "--version").required()

    override fun run() = echo(
        generateFormula(
            version = version,
            url = url,
            hash = URL(url).openStream().use { DigestUtils.sha256Hex(it) }
        )
    )
}

class Template : CliktCommand(name = "template") {
    private val template by argument("template").file(mustExist = true, canBeDir = false, mustBeReadable = true)
    private val output by argument("output").file(canBeDir = false)

    private val params by option("-p", "--params").associate()

    override fun run() {
        val cfg = Configuration(Configuration.VERSION_2_3_29).apply {
            templateLoader = StringTemplateLoader().apply {
                putTemplate("main", template.readText())
            }
            defaultEncoding = "UTF-8";
            templateExceptionHandler = TemplateExceptionHandler.RETHROW_HANDLER;
            logTemplateExceptions = true;
            wrapUncheckedExceptions = true;
            fallbackOnNullLoopVariable = false;
        }

        output.writer().use { out -> cfg.getTemplate("main").process(HashMap(params), out) }
    }
}

fun generateFormula(version: String, url: String, hash: String) = """
    |class Doks < Formula
    |  desc "Provides a simple search engine over your distributed documentation"
    |  homepage "https://github.com/wlezzar/doks"
    |  version "$version"
    |  
    |  url "$url"
    |  sha256 "$hash"
    |
    |  def install
    |    bin.install "bin/doks"
    |    prefix.install "lib"
    |  end
    |end
""".trimMargin()

val tmpl = """
    
""".trimIndent()
