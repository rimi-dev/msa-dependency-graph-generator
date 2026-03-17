package com.depgraph.controller

import com.depgraph.dto.DependencyGraphResponse
import com.depgraph.service.GraphService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/projects/{projectId}/graph")
class GraphController(
    private val graphService: GraphService,
) {

    @GetMapping
    fun getGraph(@PathVariable projectId: String): ResponseEntity<DependencyGraphResponse> =
        ResponseEntity.ok(graphService.getGraph(projectId))
}
