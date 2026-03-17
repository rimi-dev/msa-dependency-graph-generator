package com.depgraph.repository

import com.depgraph.domain.AnalysisJob
import org.springframework.data.jpa.repository.JpaRepository

interface AnalysisJobRepository : JpaRepository<AnalysisJob, String>
