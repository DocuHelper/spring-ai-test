package org.bmserver.mcpclient

import org.springframework.ai.chat.client.ChatClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux

@RestController
class TestMcpClient(
    private val chatClient: ChatClient
) {

    @GetMapping
    fun test(@RequestParam text: String): Flux<String> {
        return chatClient.prompt(text)
            .stream()
            .content()
    }
}