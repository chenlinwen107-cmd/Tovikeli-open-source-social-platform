package com.contentplatform.backend.domain.common

/**
 * Public identifier of a domain aggregate or entity.
 *
 * PublicId is a generic value object:
 * - it enforces only generic basic validity
 * - it does NOT enforce type prefixes such as ct_/au_/md_/tg_
 * - it does NOT depend on Content/Author/Tag/Media business concepts
 */
@JvmInline
value class PublicId(
    val value: String
) {
    init {
        require(value.isNotBlank()) { "PublicId must not be blank" }
    }
}
