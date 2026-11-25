package ru.nto.guestmanagement.mobile.testing

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * Мини-сервер для локальных UI-тестов: настоящий HTTP на 127.0.0.1, поэтому клиент приложения
 * (Ktor CIO) работает без подмены движка. Позволяет задавать ответы по «METHOD /path» и считать вызовы.
 */
class FakeBackend : AutoCloseable {

    data class Reply(val status: Int, val body: String, val delayMillis: Long = 0)

    /** Наблюдаемые вызовы в формате `METHOD /path[?query]`. */
    val calls = CopyOnWriteArrayList<String>()

    /** Тела запросов (для проверки нормализации полей формы). */
    val bodies = CopyOnWriteArrayList<String>()

    private val handlers = ConcurrentHashMap<String, (String) -> Reply>()
    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

    init {
        server.createContext("/") { exchange ->
            val uri = exchange.requestURI
            val method = exchange.requestMethod
            val path = uri.path
            val query = uri.rawQuery.orEmpty()
            calls += if (query.isEmpty()) "$method $path" else "$method $path?$query"
            bodies += exchange.requestBody.readBytes().toString(Charsets.UTF_8)

            val reply = handlers["$method $path"]?.invoke(query)
                ?: Reply(status = 404, body = """{"error":"Request not found"}""")
            if (reply.delayMillis > 0) {
                Thread.sleep(reply.delayMillis)
            }
            val bytes = reply.body.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
            exchange.sendResponseHeaders(reply.status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.executor = Executors.newCachedThreadPool()
        server.start()
    }

    fun handle(method: String, path: String, handler: (String) -> Reply) {
        handlers["$method $path"] = handler
    }

    fun json(method: String, path: String, body: String, status: Int = 200) {
        handle(method, path) { Reply(status, body) }
    }

    fun url(): String = "http://127.0.0.1:${server.address.port}"

    override fun close() {
        server.stop(0)
    }
}