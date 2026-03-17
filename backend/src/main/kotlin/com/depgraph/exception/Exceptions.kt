package com.depgraph.exception

sealed class DepGraphException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

class ProjectNotFoundException(id: String) :
    DepGraphException("Project not found: $id")

class ServiceNotFoundException(id: String) :
    DepGraphException("Service not found: $id")

class ProjectAlreadyExistsException(slug: String) :
    DepGraphException("Project already exists with slug: $slug")

class IngestionException(message: String, cause: Throwable? = null) :
    DepGraphException(message, cause)

class AnalysisException(message: String, cause: Throwable? = null) :
    DepGraphException(message, cause)

class InvalidGitUrlException(url: String) :
    DepGraphException("Invalid git URL: $url")

class StorageException(message: String, cause: Throwable? = null) :
    DepGraphException(message, cause)

class JobNotFoundException(jobId: String) :
    DepGraphException("Analysis job not found: $jobId")
