package com.github.wlezzar.doks.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import org.slf4j.MDC

suspend fun <T> withMDC(vararg context: Pair<String, String>, block: suspend CoroutineScope.() -> T) =
    withContext(MDCContext(contextMap = MDC.getCopyOfContextMap() + context), block)