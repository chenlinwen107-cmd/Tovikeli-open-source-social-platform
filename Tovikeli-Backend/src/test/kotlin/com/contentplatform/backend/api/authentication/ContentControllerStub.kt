package com.contentplatform.backend.api.authentication

import com.contentplatform.backend.api.authentication.CallerResolver
import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * Minimal controller stub for testing [AuthenticationFilter].
 * Provides one public GET, one protected GET, and one protected POST endpoint.
 */
@RestController
@ConditionalOnProperty(name = ["spring.datasource.url"])
class ContentControllerStub {

    @GetMapping("/stub/public")
    fun publicEndpoint(): ResponseEntity<String> {
        return ResponseEntity.ok("public")
    }

    @GetMapping("/stub/protected")
    fun protectedEndpoint(servletRequest: HttpServletRequest): ResponseEntity<String> {
        val caller = CallerResolver.require(servletRequest)
        return ResponseEntity.ok("protected:${caller.userId.value}")
    }

    @PostMapping("/stub/protected-post")
    fun protectedPostEndpoint(servletRequest: HttpServletRequest, @RequestBody body: String): ResponseEntity<String> {
        val caller = CallerResolver.require(servletRequest)
        return ResponseEntity.ok("protected-post:${caller.userId.value}")
    }
}
