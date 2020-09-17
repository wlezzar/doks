package com.github.wlezzar.doks.http

import com.github.wlezzar.doks.SearchEngine
import com.github.wlezzar.doks.utils.toJsonNode
import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.Route
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.CorsHandler
import io.vertx.ext.web.handler.StaticHandler
import io.vertx.kotlin.core.http.endAwait
import io.vertx.kotlin.core.http.listenAwait
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.apache.lucene.queryparser.classic.ParseException
import org.slf4j.LoggerFactory


private val logger = LoggerFactory.getLogger(DoksServer::class.java)

class DoksServer(
    private val engine: SearchEngine,
    private val port: Int = 8888
) : CoroutineVerticle() {

    override suspend fun start() {
        val router = Router.router(vertx).apply {
            route().handler(
                CorsHandler.create("*").allowedMethods(
                    setOf(
                        HttpMethod.POST,
                        HttpMethod.GET,
                        HttpMethod.PUT,
                        HttpMethod.PATCH,
                        HttpMethod.DELETE
                    )
                )
            )

            route().handler(BodyHandler.create())
            get("/api/search").asyncHandler(scope = this@DoksServer) { ctx ->
                val query = ctx.request().getParam("q")
                    ?: throw IllegalArgumentException("'q' parameter not supplied")

                try {
                    val result = engine.search(query)
                    ctx.response().apply {
                        statusCode = 200
                        endAwait(result.toJsonNode().toString())
                    }
                } catch (err: ParseException) {
                    ctx.response().apply {
                        statusCode = 400
                        endAwait(mapOf("error" to err.toString()).toJsonNode().toString())
                    }
                }

            }
            route().handler(StaticHandler.create())
        }

        logger.info("listening on port: $port")
        vertx
            .createHttpServer()
            .requestHandler(router)
            .listenAwait(port)
    }
}

private fun Route.asyncHandler(
    scope: CoroutineScope,
    timeout: Long? = null,
    action: suspend (RoutingContext) -> Unit
): Route = handler { ctx ->
    scope.launch(ctx.vertx().dispatcher()) {
        try {
            if (timeout != null) {
                withTimeout(timeout) { action(ctx) }
            } else {
                action(ctx)
            }
        } catch (e: IllegalArgumentException) {
            ctx.fail(400, e)
        } catch (e: Exception) {
            logger.error("http request failed", e)
            ctx.fail(e)
        }
    }
}
