package org.bmserver.mcpserver.mcp

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.support.ToolCallbacks
import org.springframework.ai.tool.ToolCallbackProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


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

}