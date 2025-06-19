package org.bmserver.mcpclient
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.mcp.AsyncMcpToolCallbackProvider
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@Configuration
class McpClientConfig {

    @Bean
    fun chatClient(chatModel: OpenAiChatModel, toolCallbackProvider: AsyncMcpToolCallbackProvider,): ChatClient {
        return ChatClient.builder(chatModel)
            .defaultToolCallbacks(toolCallbackProvider)
            .build()
    }

}
