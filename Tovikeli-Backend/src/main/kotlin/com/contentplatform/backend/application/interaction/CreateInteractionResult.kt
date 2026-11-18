package com.contentplatform.backend.application.interaction

import com.contentplatform.backend.domain.interaction.Interaction

/**
 * Result of a [CreateInteractionUseCase.create] invocation (Stage 20-A).
 *
 * Carries the resolved [Interaction] aggregate together with a [created] flag
 * that reliably distinguishes a brand-new creation from an idempotent
 * re-read of an already-existing interaction (including the concurrent
 * duplicate-key race path).
 *
 * The [created] flag is determined inside the UseCase at the exact code path
 * that decided the outcome:
 *  - brand-new save succeeded       -> created = true
 *  - natural-key dedup returned      -> created = false
 *  - DuplicateKeyException re-read   -> created = false
 *
 * This is the single source of truth for the HTTP 201 vs 200 distinction;
 * the controller never needs to query a Repository.
 */
data class CreateInteractionResult(
    val interaction: Interaction,
    val created: Boolean
)
