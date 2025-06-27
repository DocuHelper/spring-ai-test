package org.bmserver.mcpserver.mcp

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.modelcontextprotocol.spec.McpError
import io.modelcontextprotocol.spec.McpSchema
import io.modelcontextprotocol.spec.McpSchema.JSONRPCMessage
import io.modelcontextprotocol.spec.McpServerSession
import io.modelcontextprotocol.spec.McpServerTransport
import io.modelcontextprotocol.spec.McpServerTransportProvider
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.reactive.function.server.HandlerFunction
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.RouterFunctions
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.Disposable
import reactor.core.Exceptions
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
import reactor.core.publisher.Mono
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier
import kotlin.concurrent.Volatile

class CustomTransportProvider(
    objectMapper: ObjectMapper, baseUrl: String?, messageEndpoint: String,
    sseEndpoint: String
) : McpServerTransportProvider {
    private val objectMapper: ObjectMapper

    /**
     * Base URL for the message endpoint. This is used to construct the full URL for
     * clients to send their JSON-RPC messages.
     */
    private val baseUrl: String?

    private val messageEndpoint: String

    private val sseEndpoint: String

    /**
     * Returns the WebFlux router function that defines the transport's HTTP endpoints.
     * This router function should be integrated into the application's web configuration.
     *
     *
     *
     * The router function defines two endpoints:
     *
     *  * GET {sseEndpoint} - For establishing SSE connections
     *  * POST {messageEndpoint} - For receiving client messages
     *
     * @return The configured [RouterFunction] for handling HTTP requests
     */
    val routerFunction: RouterFunction<*>

    private var sessionFactory: McpServerSession.Factory? = null

    /**
     * Map of active client sessions, keyed by session ID.
     */
    private val sessions = ConcurrentHashMap<String?, McpServerSession?>()

    /**
     * Flag indicating if the transport is shutting down.
     */
    @Volatile
    private var isClosing = false

    /**
     * Constructs a new WebFlux SSE server transport provider instance.
     * @param objectMapper The ObjectMapper to use for JSON serialization/deserialization
     * of MCP messages. Must not be null.
     * @param messageEndpoint The endpoint URI where clients should send their JSON-RPC
     * messages. This endpoint will be communicated to clients during SSE connection
     * setup. Must not be null.
     * @throws IllegalArgumentException if either parameter is null
     */
    /**
     * Constructs a new WebFlux SSE server transport provider instance with the default
     * SSE endpoint.
     * @param objectMapper The ObjectMapper to use for JSON serialization/deserialization
     * of MCP messages. Must not be null.
     * @param messageEndpoint The endpoint URI where clients should send their JSON-RPC
     * messages. This endpoint will be communicated to clients during SSE connection
     * setup. Must not be null.
     * @throws IllegalArgumentException if either parameter is null
     */
    @JvmOverloads
    constructor(objectMapper: ObjectMapper, messageEndpoint: String, sseEndpoint: String = DEFAULT_SSE_ENDPOINT) : this(
        objectMapper,
        DEFAULT_BASE_URL,
        messageEndpoint,
        sseEndpoint
    )

    /**
     * Constructs a new WebFlux SSE server transport provider instance.
     * @param objectMapper The ObjectMapper to use for JSON serialization/deserialization
     * of MCP messages. Must not be null.
     * @param baseUrl webflux message base path
     * @param messageEndpoint The endpoint URI where clients should send their JSON-RPC
     * messages. This endpoint will be communicated to clients during SSE connection
     * setup. Must not be null.
     * @throws IllegalArgumentException if either parameter is null
     */
    init {
        io.modelcontextprotocol.util.Assert.notNull(objectMapper, "ObjectMapper must not be null")
        io.modelcontextprotocol.util.Assert.notNull(baseUrl, "Message base path must not be null")
        io.modelcontextprotocol.util.Assert.notNull(messageEndpoint, "Message endpoint must not be null")
        io.modelcontextprotocol.util.Assert.notNull(sseEndpoint, "SSE endpoint must not be null")

        this.objectMapper = objectMapper
        this.baseUrl = baseUrl
        this.messageEndpoint = messageEndpoint
        this.sseEndpoint = sseEndpoint
        this.routerFunction = RouterFunctions.route()
            .GET(this.sseEndpoint, HandlerFunction { request: ServerRequest? -> this.handleSseConnection(request) })
            .POST(this.messageEndpoint, HandlerFunction { request: ServerRequest? -> this.handleMessage(request!!) })
            .build()
    }

    override fun setSessionFactory(sessionFactory: McpServerSession.Factory) {
        this.sessionFactory = sessionFactory
    }

    /**
     * Broadcasts a JSON-RPC message to all connected clients through their SSE
     * connections. The message is serialized to JSON and sent as a server-sent event to
     * each active session.
     *
     *
     *
     * The method:
     *
     *  * Serializes the message to JSON
     *  * Creates a server-sent event with the message data
     *  * Attempts to send the event to all active sessions
     *  * Tracks and reports any delivery failures
     *
     * @param method The JSON-RPC method to send to clients
     * @param params The method parameters to send to clients
     * @return A Mono that completes when the message has been sent to all sessions, or
     * errors if any session fails to receive the message
     */
    override fun notifyClients(method: String?, params: Any?): Mono<Void?> {
        if (sessions.isEmpty()) {
            logger.debug("No active sessions to broadcast message to")
            return Mono.empty<Void?>()
        }

        logger.debug("Attempting to broadcast message to {} active sessions", sessions.size)

        return Flux.fromIterable<McpServerSession?>(sessions.values)
            .flatMap<Void?>(Function { session: McpServerSession? ->
                session!!.sendNotification(method, params)
                    .doOnError(
                        Consumer { e: Throwable? ->
                            logger.error(
                                "Failed to send message to session {}: {}",
                                session.getId(),
                                e!!.message
                            )
                        })
                    .onErrorComplete()
            })
            .then()
    }

    // FIXME: This javadoc makes claims about using isClosing flag but it's not
    // actually
    // doing that.
    /**
     * Initiates a graceful shutdown of all the sessions. This method ensures all active
     * sessions are properly closed and cleaned up.
     *
     *
     *
     * The shutdown process:
     *
     *  * Marks the transport as closing to prevent new connections
     *  * Closes each active session
     *  * Removes closed sessions from the sessions map
     *  * Times out after 5 seconds if shutdown takes too long
     *
     * @return A Mono that completes when all sessions have been closed
     */
    override fun closeGracefully(): Mono<Void?> {
        return Flux.fromIterable<McpServerSession?>(sessions.values)
            .doFirst(Runnable { logger.debug("Initiating graceful shutdown with {} active sessions", sessions.size) })
            .flatMap<Void?>(Function { obj: McpServerSession? -> obj!!.closeGracefully() })
            .then()
    }

    /**
     * Handles new SSE connection requests from clients. Creates a new session for each
     * connection and sets up the SSE event stream.
     * @param request The incoming server request
     * @return A Mono which emits a response with the SSE event stream
     */
    private fun handleSseConnection(request: ServerRequest?): Mono<ServerResponse?> {
        if (isClosing) {
            return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE).bodyValue("Server is shutting down")
        }

        return ServerResponse.ok()
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .body(Flux.create<ServerSentEvent<*>?>(Consumer { sink: FluxSink<ServerSentEvent<*>?>? ->
                val sessionTransport = WebFluxMcpSessionTransport(sink!!)
                val session = sessionFactory!!.create(sessionTransport)
                val sessionId = session.getId()

                logger.debug("Created new SSE connection for session: {}", sessionId)
                sessions.put(sessionId, session)

                SessionAuthToken.sessions.put(sessionId, request?.headers()?.firstHeader(HttpHeaders.AUTHORIZATION)!!)

                // Send initial endpoint event
                logger.debug("Sending initial endpoint event to session: {}", sessionId)
                sink.next(
                    ServerSentEvent.builder<Any?>()
                        .event(ENDPOINT_EVENT_TYPE)
                        .data(this.baseUrl + this.messageEndpoint + "?sessionId=" + sessionId)
                        .build()
                )
                sink.onCancel(Disposable {
                    logger.debug("Session {} cancelled", sessionId)
                    sessions.remove(sessionId)
                })
            }), ServerSentEvent::class.java)
    }

    /**
     * Handles incoming JSON-RPC messages from clients. Deserializes the message and
     * processes it through the configured message handler.
     *
     *
     *
     * The handler:
     *
     *  * Deserializes the incoming JSON-RPC message
     *  * Passes it through the message handler chain
     *  * Returns appropriate HTTP responses based on processing results
     *  * Handles various error conditions with appropriate error responses
     *
     * @param request The incoming server request containing the JSON-RPC message
     * @return A Mono emitting the response indicating the message processing result
     */
    private fun handleMessage(request: ServerRequest): Mono<ServerResponse?> {
        if (isClosing) {
            return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE).bodyValue("Server is shutting down")
        }

        if (request.queryParam("sessionId").isEmpty()) {
            return ServerResponse.badRequest().bodyValue(McpError("Session ID missing in message endpoint"))
        }

        val session = sessions.get(request.queryParam("sessionId").get())

        if (session == null) {
            return ServerResponse.status(HttpStatus.NOT_FOUND)
                .bodyValue(McpError("Session not found: " + request.queryParam("sessionId").get()))
        }

        return request.bodyToMono<String?>(String::class.java).flatMap<ServerResponse?>(Function flatMap@{ body: String? ->
            try {
                val message = McpSchema.deserializeJsonRpcMessage(objectMapper, body)
                return@flatMap session.handle(message)
                    .flatMap<ServerResponse?>(Function { response: Void? -> ServerResponse.ok().build() })
                    .onErrorResume(
                        Function { error: Throwable? ->
                            logger.error("Error processing  message: {}", error!!.message)
                            ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .bodyValue(McpError(error.message))
                        })
            } catch (e: IllegalArgumentException) {
                logger.error("Failed to deserialize message: {}", e.message)
                return@flatMap ServerResponse.badRequest().bodyValue(McpError("Invalid message format"))
            } catch (e: IOException) {
                logger.error("Failed to deserialize message: {}", e.message)
                return@flatMap ServerResponse.badRequest().bodyValue(McpError("Invalid message format"))
            }
        })
    }

    private inner class WebFluxMcpSessionTransport(private val sink: FluxSink<ServerSentEvent<*>?>) :
        McpServerTransport {
        override fun sendMessage(message: JSONRPCMessage?): Mono<Void?> {
            return Mono.fromSupplier<String?>(Supplier fromSupplier@{
                try {
                    return@fromSupplier objectMapper.writeValueAsString(message)
                } catch (e: IOException) {
                    throw Exceptions.propagate(e)
                }
            }).doOnNext(Consumer { jsonText: String? ->
                val event = ServerSentEvent.builder<Any?>()
                    .event(MESSAGE_EVENT_TYPE)
                    .data(jsonText)
                    .build()
                sink.next(event)
            }).doOnError(Consumer { e: Throwable? ->
                // TODO log with sessionid
                val exception = Exceptions.unwrap(e!!)
                sink.error(exception)
            }).then()
        }

        override fun <T> unmarshalFrom(data: Any?, typeRef: TypeReference<T?>?): T? {
            return objectMapper.convertValue<T?>(data, typeRef)
        }

        override fun closeGracefully(): Mono<Void?> {
            return Mono.fromRunnable<Void?>(Runnable { sink.complete() })
        }

        override fun close() {
            sink.complete()
        }
    }

    /**
     * Builder for creating instances of [CustomTransportProvider].
     *
     *
     * This builder provides a fluent API for configuring and creating instances of
     * WebFluxSseServerTransportProvider with custom settings.
     */
    class Builder {
        private var objectMapper: ObjectMapper? = null

        private var baseUrl: String? = DEFAULT_BASE_URL

        private var messageEndpoint: String? = null

        private var sseEndpoint = DEFAULT_SSE_ENDPOINT

        /**
         * Sets the ObjectMapper to use for JSON serialization/deserialization of MCP
         * messages.
         * @param objectMapper The ObjectMapper instance. Must not be null.
         * @return this builder instance
         * @throws IllegalArgumentException if objectMapper is null
         */
        fun objectMapper(objectMapper: ObjectMapper): Builder {
            io.modelcontextprotocol.util.Assert.notNull(objectMapper, "ObjectMapper must not be null")
            this.objectMapper = objectMapper
            return this
        }

        /**
         * Sets the project basePath as endpoint prefix where clients should send their
         * JSON-RPC messages
         * @param baseUrl the message basePath . Must not be null.
         * @return this builder instance
         * @throws IllegalArgumentException if basePath is null
         */
        fun basePath(baseUrl: String?): Builder {
            io.modelcontextprotocol.util.Assert.notNull(baseUrl, "basePath must not be null")
            this.baseUrl = baseUrl
            return this
        }

        /**
         * Sets the endpoint URI where clients should send their JSON-RPC messages.
         * @param messageEndpoint The message endpoint URI. Must not be null.
         * @return this builder instance
         * @throws IllegalArgumentException if messageEndpoint is null
         */
        fun messageEndpoint(messageEndpoint: String): Builder {
            io.modelcontextprotocol.util.Assert.notNull(messageEndpoint, "Message endpoint must not be null")
            this.messageEndpoint = messageEndpoint
            return this
        }

        /**
         * Sets the SSE endpoint path.
         * @param sseEndpoint The SSE endpoint path. Must not be null.
         * @return this builder instance
         * @throws IllegalArgumentException if sseEndpoint is null
         */
        fun sseEndpoint(sseEndpoint: String): Builder {
            io.modelcontextprotocol.util.Assert.notNull(sseEndpoint, "SSE endpoint must not be null")
            this.sseEndpoint = sseEndpoint
            return this
        }

        /**
         * Builds a new instance of [CustomTransportProvider] with the
         * configured settings.
         * @return A new WebFluxSseServerTransportProvider instance
         * @throws IllegalStateException if required parameters are not set
         */
        fun build(): CustomTransportProvider {
            io.modelcontextprotocol.util.Assert.notNull(objectMapper, "ObjectMapper must be set")
            io.modelcontextprotocol.util.Assert.notNull(messageEndpoint, "Message endpoint must be set")

            return CustomTransportProvider(objectMapper!!, baseUrl, messageEndpoint!!, sseEndpoint)
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(CustomTransportProvider::class.java)

        /**
         * Event type for JSON-RPC messages sent through the SSE connection.
         */
        const val MESSAGE_EVENT_TYPE: String = "message"

        /**
         * Event type for sending the message endpoint URI to clients.
         */
        const val ENDPOINT_EVENT_TYPE: String = "endpoint"

        /**
         * Default SSE endpoint path as specified by the MCP transport specification.
         */
        const val DEFAULT_SSE_ENDPOINT: String = "/sse"

        const val DEFAULT_BASE_URL: String = ""

        fun builder(): Builder {
            return Builder()
        }
    }
}
