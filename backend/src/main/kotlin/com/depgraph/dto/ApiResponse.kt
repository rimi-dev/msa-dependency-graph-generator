package com.depgraph.dto

import java.time.Instant

data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val timestamp: Instant = Instant.now(),
    val error: ApiError? = null
) {
    companion object {
        fun <T> success(data: T): ApiResponse<T> = ApiResponse(success = true, data = data)
        fun <T> error(error: ApiError): ApiResponse<T> = ApiResponse(success = false, error = error)
    }
}

data class ApiError(
    val code: String,
    val message: String,
    val details: String? = null,
    val traceId: String? = null
)
