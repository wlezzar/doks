package com.github.wlezzar.doks.utils

import kotlinx.coroutines.future.await
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService


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