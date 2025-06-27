package org.bmserver.mcpserver.mcp

import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ExampleTool {

    @Tool(description = "모든 사용자의 uuid를 가져옵니다.")
    fun getAllUserId(): List<UUID> {
        println("================= 모든 사용자의 uuid를 가져옵니다. ========================")
        return User.allUser().map { it.uuid }
    }

    @Tool(description = "사용자의 정보를 가져옵니다.")
    fun getUser(user: UUID): User? {
        println("================= 사용자(${user})의 정보를 가져옵니다. ========================")
        return User.allUser().firstOrNull { it.uuid == user }
    }


    @Tool(description = "문서를 추가합니다.")
    fun addDocument(title:String, content: String): Document {
        println("================= 문서를 추가합니다. ========================")
        val newDocument = Document(
            uuid = UUID.randomUUID(),
            title = title,
            content =  content
        )
        return Document.otherDocument.add(newDocument).let { newDocument }
    }


    @Tool(description = "모든 문서의 uuid를 가져옵니다.")
    fun getAllDocumentId(): List<UUID> {
        println("================= 모든 문서의 uuid를 가져옵니다. ========================")
        return Document.allDocument().map { it.uuid }
    }

    @Tool(description = "문서 정보를 가져옵니다.")
    fun getDocument(document: UUID): Document? {
        println("================= 문서(${document}) 정보를 가져옵니다. ========================")
        return Document.allDocument().firstOrNull { it.uuid == document }
    }

}
