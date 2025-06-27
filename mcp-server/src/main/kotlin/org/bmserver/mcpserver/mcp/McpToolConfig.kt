package org.bmserver.mcpserver.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import io.modelcontextprotocol.server.transport.WebFluxSseServerTransportProvider
import io.modelcontextprotocol.spec.McpServerSession
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.mcp.server.autoconfigure.McpServerProperties
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.support.ToolCallbacks
import org.springframework.ai.tool.ToolCallbackProvider
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.util.function.Supplier


@Configuration
class McpToolConfig {
    @Bean
    fun chatClient(chatModel: OpenAiChatModel, exampleTool: ExampleTool, tools: ToolCallbackProvider): ChatClient {

        return ChatClient.builder(chatModel)
            .defaultToolCallbacks(tools)
//            .defaultTools(exampleTool)
            .build()
    }

    @Bean
    fun toolCallbackProvider(tool: ExampleTool): ToolCallbackProvider {

        return ToolCallbackProvider.from(
            ToolCallbacks.from(tool).toList()
        )
    }


    @Bean
    fun webFluxTransport(
        objectMapperProvider: ObjectProvider<ObjectMapper>,
        serverProperties: McpServerProperties
    ): WebFluxSseServerTransportProvider {
        val objectMapper = objectMapperProvider.getIfAvailable(Supplier { ObjectMapper() })
        return object : WebFluxSseServerTransportProvider(
            objectMapper, serverProperties.getBaseUrl(),
            serverProperties.getSseMessageEndpoint(), serverProperties.getSseEndpoint()
        ) {
            override fun setSessionFactory(sessionFactory: McpServerSession.Factory?) {

                super.setSessionFactory { sessionTransport ->
                    sessionFactory!!.create(sessionTransport)
                        .also {
                            Mono.deferContextual { Mono.just<String>(it.get<String>(HttpHeaders.AUTHORIZATION)) }
                                .subscribe {
                                    print(it)
                                }
                            print(it.id)
                        }
                }
            }
        }
    }

    @Bean
    fun webfluxMcpRouterFunction(
        webFluxProvider: WebFluxSseServerTransportProvider,
    ): RouterFunction<*>? = webFluxProvider.routerFunction

}

@Component
class McpAuthFilter : WebFilter {
    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain
    ): Mono<Void?> {

        return if (exchange.request.path.value() == "/sse") {
            chain.filter(exchange)
                .contextWrite {
                    println("======================== McpAuthFilter ========================")
                    it.putNonNull(
                        HttpHeaders.AUTHORIZATION,
                        exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)
                    )
                }
        } else {
            chain.filter(exchange)
        }
    }
}