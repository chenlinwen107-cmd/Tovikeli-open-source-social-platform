package com.contentplatform.backend.api.instance

import com.contentplatform.backend.application.instance.GetInstanceByIdUseCase
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.source.Instance
import com.contentplatform.backend.domain.source.InstanceHealthStatus
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import com.contentplatform.backend.repository.UserRepository
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * MVC slice test for [InstanceController].
 *
 * The controller under test is registered because the slice property enables
 * its datasource-conditional wiring. The Stage 9 use case is mocked; no
 * database, no Flyway and no external HTTP is involved.
 */
@WebMvcTest(controllers = [InstanceController::class])
@TestPropertySource(properties = ["spring.datasource.url=jdbc:postgresql://localhost:5432/unused_in_slice_test"])
class InstanceControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var getInstanceByIdUseCase: GetInstanceByIdUseCase

    @MockitoBean
    private lateinit var userRepository: UserRepository

    // ------------------------------------------------------------------
    // Found -> 200 + InstanceResponse projection
    // ------------------------------------------------------------------

    @Test
    fun getInstance_whenFound_returns200WithResponseDto() {
        val instance = Instance(
            id = PublicId("inst_misskey_io"),
            sourceId = PublicId("src_misskey"),
            domain = "misskey.io",
            displayName = "Misskey.io",
            enabled = true,
            allowIndex = true,
            allowMediaPreview = true,
            allowMediaCache = false,
            allowOriginalAccess = false,
            nsfwPolicy = null,
            healthStatus = InstanceHealthStatus.HEALTHY
        )
        Mockito.`when`(getInstanceByIdUseCase.getById(instance.id)).thenReturn(instance)

        mockMvc.perform(get("/api/v1/instances/{id}", instance.id.value))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value("inst_misskey_io"))
            .andExpect(jsonPath("$.sourceId").value("src_misskey"))
            .andExpect(jsonPath("$.domain").value("misskey.io"))
            .andExpect(jsonPath("$.displayName").value("Misskey.io"))
            .andExpect(jsonPath("$.enabled").value(true))
            .andExpect(jsonPath("$.allowIndex").value(true))
            .andExpect(jsonPath("$.allowMediaPreview").value(true))
            .andExpect(jsonPath("$.allowMediaCache").value(false))
            .andExpect(jsonPath("$.allowOriginalAccess").value(false))
            .andExpect(jsonPath("$.healthStatus").value("HEALTHY"))
    }

    // ------------------------------------------------------------------
    // Not found -> 404
    // ------------------------------------------------------------------

    @Test
    fun getInstance_whenAbsent_returns404() {
        Mockito.`when`(getInstanceByIdUseCase.getById(PublicId("inst_missing"))).thenReturn(null)

        mockMvc.perform(get("/api/v1/instances/{id}", "inst_missing"))
            .andExpect(status().isNotFound)
    }
}
