package org.bmserver.mcpserver.mcp

import java.util.UUID

class User(
    val uuid: UUID,
    val name: String,
    val documents: List<UUID>
) {
    companion object {
        fun allUser() = listOf(user_A(), user_B(), user_C())

        fun user_A() =
            User(UUID.fromString("9e8e5656-8317-46a6-8b04-bd1176bbaf4d"), "일민상", Document.documents_A().map { it.uuid })

        fun user_B() =
            User(UUID.fromString("3d504ec0-995a-417c-a017-476bc35b83be"), "이민상", Document.documents_B().map { it.uuid })

        fun user_C() =
            User(UUID.fromString("dff0fa4e-18d9-420a-8345-d194b718816c"), "삼민상", Document.documents_C().map { it.uuid })
    }
}