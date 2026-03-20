package com.depgraph.controller

import com.depgraph.dto.ApiResponse
import com.depgraph.dto.RenameServiceRequest
import com.depgraph.dto.ServiceResponse
import com.depgraph.service.GraphService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/projects/{projectId}/services")
class ServiceController(
    private val graphService: GraphService,
) {

    @GetMapping
    fun listServices(@PathVariable projectId: String): ApiResponse<List<ServiceResponse>> {
        val services = graphService.listServicesByProject(projectId)
        return ApiResponse.success(services)
    }

    @PatchMapping("/{serviceId}")
    fun renameService(
        @PathVariable projectId: String,
        @PathVariable serviceId: String,
        @RequestBody request: RenameServiceRequest,
    ): ApiResponse<ServiceResponse> {
        val updated = graphService.renameService(projectId, serviceId, request)
        return ApiResponse.success(ServiceResponse.from(updated))
    }
}
