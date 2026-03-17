package com.depgraph.service

import com.depgraph.domain.Project
import com.depgraph.domain.ProjectStatus
import com.depgraph.dto.CreateProjectRequest
import com.depgraph.exception.ProjectAlreadyExistsException
import com.depgraph.exception.ProjectNotFoundException
import com.depgraph.repository.ProjectRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.Optional

class ProjectServiceTest {

    private val projectRepository: ProjectRepository = mockk()
    private val projectService = ProjectService(projectRepository)

    @Test
    fun `should create project successfully`() {
        val request = CreateProjectRequest(
            name = "Test Project",
            slug = "test-project",
            description = "A test project",
        )
        val savedProject = Project(
            id = "proj-123",
            name = request.name,
            slug = request.slug,
            description = request.description,
        )

        every { projectRepository.existsBySlug(request.slug) } returns false
        every { projectRepository.save(any()) } returns savedProject

        val result = projectService.create(request)

        result.name shouldBe "Test Project"
        result.slug shouldBe "test-project"
        result.status shouldBe ProjectStatus.PENDING
        verify { projectRepository.save(any()) }
    }

    @Test
    fun `should throw ProjectAlreadyExistsException when slug is taken`() {
        val request = CreateProjectRequest(name = "Test", slug = "existing-slug")

        every { projectRepository.existsBySlug(request.slug) } returns true

        shouldThrow<ProjectAlreadyExistsException> {
            projectService.create(request)
        }
    }

    @Test
    fun `should throw ProjectNotFoundException when project does not exist`() {
        every { projectRepository.findById("nonexistent") } returns Optional.empty()

        shouldThrow<ProjectNotFoundException> {
            projectService.findById("nonexistent")
        }
    }

    @Test
    fun `should return project response when found`() {
        val project = Project(
            id = "proj-123",
            name = "My Project",
            slug = "my-project",
        )
        every { projectRepository.findById("proj-123") } returns Optional.of(project)

        val result = projectService.findById("proj-123")

        result.id shouldBe "proj-123"
        result.name shouldBe "My Project"
        result shouldNotBe null
    }
}
