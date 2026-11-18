package com.contentplatform.backend.api.authentication

import com.contentplatform.backend.application.authentication.DisabledUserAuthenticationException
import com.contentplatform.backend.application.authentication.InvalidCredentialsException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

/**
 * API boundary for translating authentication-related application/HTTP
 * exceptions into HTTP status codes.
 */
@ControllerAdvice
class AuthenticationExceptionHandler {

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthenticationException(
        ex: AuthenticationException
    ): ResponseEntity<Void> {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
    }

    @ExceptionHandler(DisabledUserException::class)
    fun handleDisabledUserException(
        ex: DisabledUserException
    ): ResponseEntity<Void> {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
    }

    @ExceptionHandler(InvalidCredentialsException::class)
    fun handleInvalidCredentialsException(
        ex: InvalidCredentialsException
    ): ResponseEntity<Void> {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
    }

    @ExceptionHandler(DisabledUserAuthenticationException::class)
    fun handleDisabledUserAuthenticationException(
        ex: DisabledUserAuthenticationException
    ): ResponseEntity<Void> {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
    }
}
