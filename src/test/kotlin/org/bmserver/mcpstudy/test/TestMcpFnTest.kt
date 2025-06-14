package org.bmserver.mcpstudy.test

import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toFlux

@SpringBootTest
class TestMcpFnTest {
    @Autowired
    lateinit var chatModel: OpenAiChatModel

    @Autowired
    lateinit var tool: TestTool

    @Test
    fun testA() {
        val prompts = listOf(
            "모든 유저의 이름을 알려줘",
            "9e8e5656-8317-46a6-8b04-bd1176bbaf4d 유저의 정보와 소유하고 있는 문서의 내용을 각각 알려줘",
            "347df0d0-d4da-43cc-aa10-61dc9d849ac2 문서 내용을 알려줘",
            "모든 문서 정보를 알려줘",
            "모든 사용자의 정보와 각 사용자가 소유한 문서의 제목, 내용을 알려줘",
            "아무도 소유하고있지 않은 문서가 있어?",
        )
        prompts.toFlux().flatMapSequential({
            println(it)
            call(it)
        }, 1)
            .doOnNext {
                println(it)
                println()
                println()
                println()
            }
            .blockLast()
    }


    fun call(prompt: String): Mono<String> {
        return ChatClient.create(chatModel)
            .prompt(prompt)
            .tools(tool)
            .stream()
            .content()
            .collectList()
            .map { it.joinToString("") }
    }
}
