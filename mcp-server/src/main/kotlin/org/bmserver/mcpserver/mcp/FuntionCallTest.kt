package org.bmserver.mcpserver.mcp

import org.springframework.ai.chat.client.ChatClient
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux

@RestController
class FuntionCallTest(
    private val chatClient: ChatClient
) {

    @GetMapping
    fun call(@RequestParam text: String): Flux<String> {

        return chatClient.prompt(text)
            .stream()
            .content()
    }
}