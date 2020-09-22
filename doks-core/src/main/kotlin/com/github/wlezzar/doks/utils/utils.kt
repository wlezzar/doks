package com.github.wlezzar.doks.utils

import kotlinx.coroutines.future.await
import kotlinx.coroutines.time.delay
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

private val logger = LoggerFactory.getLogger("utils")

val tmpDir by lazy {
    System.getProperty("java.io.tmpdir")
        ?: throw IllegalArgumentException("couldn't get temporary directory location")
}

suspend fun <T> ExecutorService.submitSuspending(action: () -> T): T =
    CompletableFuture.supplyAsync(action, this).await()

inline fun <T> useTemporaryDirectory(prefix: String? = null, action: (Path) -> T): T {
    val tempFile = Files.createTempDirectory(prefix)
    try {
        return action(tempFile)
    } finally {
        tempFile.toFile().takeIf { it.exists() }?.deleteRecursively()
    }
}

suspend fun <T> retryable(
    delayBetweenRetries: Duration,
    maxRetries: Int?,
    retryOn: (Exception) -> Boolean,
    messageOnError: (Exception) -> String,
    action: suspend () -> T
): T {
    while (true) {
        var numberOfRetries = 0
        try {
            return action()
        } catch (err: Exception) {
            if (!retryOn(err)) throw err

            if (maxRetries != null && numberOfRetries >= maxRetries) {
                throw err
            }

            messageOnError(err)

            logger.warn("${messageOnError(err)} - retrying operation after '$delayBetweenRetries'")

            numberOfRetries += 1
            delay(delayBetweenRetries)
        }
    }
}