package com.depgraph.controller

import com.depgraph.dto.ApiResponse
import com.depgraph.dto.GraphDataResponse
import com.depgraph.service.GraphService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/projects/{projectId}/graph")
class GraphController(
    private val graphService: GraphService,
) {

    @GetMapping
    fun getGraph(@PathVariable projectId: String): ResponseEntity<ApiResponse<GraphDataResponse>> =
        ResponseEntity.ok(ApiResponse.success(graphService.getGraphForFrontend(projectId)))
}
