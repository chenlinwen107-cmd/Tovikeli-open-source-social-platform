package com.contentplatform.backend.adapter

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class AdapterExceptionTest {

    @Test
    fun adapterException_isUncheckedRuntimeException() {
        // Direct superclass is RuntimeException (unchecked by construction).
        assertTrue(AdapterException::class.java.superclass == RuntimeException::class.java)
        // Throwable compatibility is preserved.
        assertTrue(Throwable::class.java.isAssignableFrom(AdapterException::class.java))
    }

    @Test
    fun adapterException_preservesMessage() {
        val ex = AdapterException("platform unreachable")
        assertEquals("platform unreachable", ex.message)
    }

    @Test
    fun adapterException_supportsCause() {
        val cause = IllegalStateException("timeout")
        val ex = AdapterException("fetch failed", cause)
        assertSame(cause, ex.cause)
    }

    @Test
    fun adapterException_withoutCause() {
        val ex = AdapterException("plain failure")
        assertNull(ex.cause)
    }

    @Test
    fun adapterException_hasNoSpringOrHttpDependency() {
        // Structural: superclass chain is plain RuntimeException; constructors
        // take only String + Throwable — no Spring/HTTP/SDK types involved.
        val ctors = AdapterException::class.java.constructors.flatMap { it.parameterTypes.toList() }
        assertTrue(ctors.none { it.name.startsWith("org.springframework") })
        assertTrue(ctors.none { it.name.startsWith("org.apache.hc") || it.name.contains("okhttp") })
    }
}
