package org.bmserver.mcpserver.mcp

import java.util.UUID

class Document(
    val uuid: UUID,
    val title: String,
    val content: String,
) {
    companion object {
        val otherDocument: MutableList<Document> = mutableListOf()

        fun allDocument() = listOf(documents_A(), documents_B(), documents_C(), otherDocument).flatten()

        fun documents_A() = listOf(
            Document(UUID.fromString("8b040dae-538d-4beb-88c3-ad60dcc448aa"), "문서 A_1", "내가 본 사람은 별로 친해지고싶지 않았다."),
            Document(UUID.fromString("a84839b6-d0ea-46ab-9d15-a405d6217154"), "문서 A_2", "내가 본 사람은 인상이 날카로워 보였다."),
            Document(UUID.fromString("347df0d0-d4da-43cc-aa10-61dc9d849ac2"), "문서 A_3", "내가 본 사람은 기억이 나지 않는다."),
        )

        fun documents_B() = listOf(
            Document(UUID.fromString("66912cfd-c7c6-48a4-97de-e09b5e39298c"), "문서 B_1", "타인이 본 나는 별로 친해지고 싶지 않았다."),
            Document(UUID.fromString("ef26570a-c9ec-458b-9f94-ded9f3201d92"), "문서 B_2", "타인이 본 나는 인상이 날카로워 보였다."),
            Document(UUID.fromString("503ac48a-fecd-4ed3-a31e-0e089eb4f4d8"), "문서 B_3", "타인이 본 나는 기억에 남지 않았다."),
        )

        fun documents_C() = listOf(
            Document(UUID.fromString("d3a0e46e-c047-48ea-a3e5-f21c09c2cd0e"), "문서 C_1", "우리가 본 너는 우리가 본 너는 말수가 적었다."),
            Document(UUID.fromString("8fd3d51a-69cf-487e-8d0c-6e8943ed4126"), "문서 C_2", "우리가 본 너는 우리가 본 너는 날카로워 보였다."),
            Document(UUID.fromString("9267fab5-5268-4440-88e0-dc74fcae8a5e"), "문서 C_3", "우리가 본 너는 우리가 본 너는 기억에 남지 않았다.")
        )

    }

}
