package com.depgraph.exception

import com.depgraph.dto.ApiError
import com.depgraph.dto.ApiResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

private val log = KotlinLogging.logger {}

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ProjectNotFoundException::class, ServiceNotFoundException::class)
    fun handleNotFound(ex: DepGraphException): ResponseEntity<ApiResponse<Nothing>> {
        log.warn { "Resource not found: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(ApiError(code = "NOT_FOUND", message = ex.message ?: "Not found")))
    }

    @ExceptionHandler(JobNotFoundException::class)
    fun handleJobNotFound(ex: JobNotFoundException): ResponseEntity<ApiResponse<Nothing>> {
        log.warn { "Job not found: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(ApiError(code = "JOB_NOT_FOUND", message = ex.message ?: "Job not found")))
    }

    @ExceptionHandler(ProjectAlreadyExistsException::class)
    fun handleConflict(ex: DepGraphException): ResponseEntity<ApiResponse<Nothing>> {
        log.warn { "Conflict: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.error(ApiError(code = "CONFLICT", message = ex.message ?: "Conflict")))
    }

    @ExceptionHandler(InvalidGitUrlException::class)
    fun handleBadRequest(ex: DepGraphException): ResponseEntity<ApiResponse<Nothing>> {
        log.warn { "Bad request: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(ApiError(code = "BAD_REQUEST", message = ex.message ?: "Bad request")))
    }

    @ExceptionHandler(IngestionException::class)
    fun handleIngestionError(ex: IngestionException): ResponseEntity<ApiResponse<Nothing>> {
        log.error(ex) { "Ingestion error: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error(ApiError(code = "INGESTION_ERROR", message = "An ingestion error occurred")))
    }

    @ExceptionHandler(AnalysisException::class)
    fun handleAnalysisError(ex: AnalysisException): ResponseEntity<ApiResponse<Nothing>> {
        log.error(ex) { "Analysis error: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error(ApiError(code = "ANALYSIS_ERROR", message = "An analysis error occurred")))
    }

    @ExceptionHandler(StorageException::class)
    fun handleStorageError(ex: StorageException): ResponseEntity<ApiResponse<Nothing>> {
        log.error(ex) { "Storage error: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error(ApiError(code = "STORAGE_ERROR", message = "A storage error occurred")))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Nothing>> {
        val errors = ex.bindingResult.fieldErrors.joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        log.warn { "Validation error: $errors" }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(ApiError(code = "VALIDATION_ERROR", message = "Validation failed", details = errors)))
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadable(ex: HttpMessageNotReadableException): ResponseEntity<ApiResponse<Nothing>> {
        log.warn { "Message not readable: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(ApiError(code = "BAD_REQUEST", message = "Malformed request body")))
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneral(ex: Exception): ResponseEntity<ApiResponse<Nothing>> {
        log.error(ex) { "Unexpected error: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error(ApiError(code = "INTERNAL_ERROR", message = "An unexpected error occurred")))
    }
}
