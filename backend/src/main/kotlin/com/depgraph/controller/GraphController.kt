package com.depgraph.controller

import com.depgraph.dto.ApiResponse
import com.depgraph.dto.GraphDataResponse
import com.depgraph.dto.SourceDetailResponse
import com.depgraph.service.GraphService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/projects/{projectId}")
class GraphController(
    private val graphService: GraphService,
) {

    @GetMapping("/graph")
    fun getGraph(@PathVariable projectId: String): ResponseEntity<ApiResponse<GraphDataResponse>> =
        ResponseEntity.ok(ApiResponse.success(graphService.getGraphForFrontend(projectId)))

    @GetMapping("/dependencies/{depId}/source")
    fun getDependencySource(
        @PathVariable projectId: String,
        @PathVariable depId: String,
    ): ResponseEntity<ApiResponse<SourceDetailResponse>> {
        val source = graphService.getDependencySource(projectId, depId)
        return ResponseEntity.ok(ApiResponse.success(source))
    }
}
