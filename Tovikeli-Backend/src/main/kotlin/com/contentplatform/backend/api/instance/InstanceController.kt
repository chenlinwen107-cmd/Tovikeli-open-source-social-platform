package com.contentplatform.backend.api.instance

import com.contentplatform.backend.application.instance.GetInstanceByIdUseCase
import com.contentplatform.backend.domain.common.PublicId
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Read-only Instance detail API.
 *
 *   GET /api/v1/instances/{id}
 *
 * Flow: HTTP -> this controller -> [GetInstanceByIdUseCase] -> InstanceRepository.
 * The controller never touches a Repository directly and contains no business
 * logic.
 *
 * Not-found semantics: when the use case returns null the controller responds
 * HTTP 404 with an empty body; otherwise HTTP 200 with the API [InstanceResponse].
 *
 * PublicId: the raw path variable is wrapped by the existing [PublicId] value
 * object (its only rule: non-blank). No new parser and no prefix contract is
 * introduced at the API layer.
 *
 * Wiring: conditional on a configured datasource, mirroring the Stage 9
 * persistence-backed query use cases, so the no-datasource context keeps
 * working.
 */
@RestController
@RequestMapping("/api/v1/instances")
@ConditionalOnProperty(name = ["spring.datasource.url"])
class InstanceController(
    private val getInstanceByIdUseCase: GetInstanceByIdUseCase
) {

    @GetMapping("/{id}")
    fun getInstance(@PathVariable("id") id: String): ResponseEntity<InstanceResponse> {
        val instance = getInstanceByIdUseCase.getById(PublicId(id))
            ?: return ResponseEntity.notFound().build<InstanceResponse>()
        return ResponseEntity.ok(InstanceResponse.from(instance))
    }
}
