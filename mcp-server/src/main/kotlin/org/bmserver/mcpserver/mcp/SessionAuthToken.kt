package org.bmserver.mcpserver.mcp

import java.util.concurrent.ConcurrentHashMap

class SessionAuthToken {
    companion object {
        val sessions: ConcurrentHashMap<String, String> = ConcurrentHashMap()
    }
}