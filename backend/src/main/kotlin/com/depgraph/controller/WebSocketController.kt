package com.depgraph.controller

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.SendTo
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PathVariable

private val log = KotlinLogging.logger {}

@Controller
class WebSocketController(
    private val messagingTemplate: SimpMessagingTemplate,
) {

    @MessageMapping("/subscribe")
    @SendTo("/topic/status")
    fun handleSubscribe(projectId: String): String {
        log.debug { "WebSocket subscription for project: $projectId" }
        return "Subscribed to project: $projectId"
    }

    fun notifyProjectStatusChange(projectId: String, status: String) {
        log.info { "Notifying WebSocket clients: project $projectId status -> $status" }
        val payload: Any = mapOf("projectId" to projectId, "status" to status)
        messagingTemplate.convertAndSend("/topic/projects/$projectId/status", payload)
    }

    fun notifyGraphUpdated(projectId: String) {
        log.info { "Notifying WebSocket clients: graph updated for project $projectId" }
        val payload: Any = mapOf("projectId" to projectId, "event" to "GRAPH_UPDATED")
        messagingTemplate.convertAndSend("/topic/projects/$projectId/graph", payload)
    }
}
