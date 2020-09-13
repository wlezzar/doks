package com.github.wlezzar.doks.utils

import kotlinx.coroutines.future.await
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService


suspend fun <T> ExecutorService.submitSuspending(action: () -> T): T =
    CompletableFuture.supplyAsync(action, this).await()
