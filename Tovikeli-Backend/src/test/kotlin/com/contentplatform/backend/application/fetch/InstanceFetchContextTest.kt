package com.contentplatform.backend.application.fetch

import com.contentplatform.backend.adapter.FetchCommand
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.source.Instance
import com.contentplatform.backend.domain.source.InstanceHealthStatus
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class InstanceFetchContextTest {

    private val instance = Instance(
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

    @Test
    fun context_carriesResolvedInstanceForPolicyChecks() {
        val ctx = InstanceFetchContext(instance)

        // Application can read every policy field it needs.
        assertEquals(PublicId("inst_misskey_io"), ctx.instance.id)
        assertEquals(PublicId("src_misskey"), ctx.instance.sourceId)
        assertEquals("misskey.io", ctx.instance.domain)
        assertTrue(ctx.instance.enabled)
        assertTrue(ctx.instance.allowIndex)
        assertTrue(ctx.instance.allowMediaPreview)
        assertFalse(ctx.instance.allowMediaCache)
        assertFalse(ctx.instance.allowOriginalAccess)
        assertEquals(InstanceHealthStatus.HEALTHY, ctx.instance.healthStatus)
    }

    @Test
    fun context_instanceDomain_isExternalSemanticsOnly() {
        val ctx = InstanceFetchContext(instance)
        assertEquals("misskey.io", ctx.instanceDomain)

        // The adapter-facing value is the domain; the DB PublicId is NOT part
        // of the FetchCommand the adapter receives.
        val command = FetchCommand(
            sourceType = com.contentplatform.backend.domain.source.SourceType.MISSKEY,
            instanceDomain = ctx.instanceDomain
        )
        assertEquals("misskey.io", command.instanceDomain)
        // FetchCommand has no instanceId/publicId field by design (verified in 7A).
        val cmdFields = FetchCommand::class.java.declaredFields.map { it.name }
        assertTrue(cmdFields.none { it == "instanceId" || it.contains("PublicId") || it == "id" })
    }
}
