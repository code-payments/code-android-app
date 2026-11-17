package com.flipcash.services.models

/**
 * What moderated text will be used as.
 *
 * The server binds the kind into the attestation and accepts an attestation only for the kind it
 * names, so text used in two places has to be moderated once for each. Text over the kind's
 * maximum length is refused as invalid: 32 bytes for a currency name, 8 for a symbol.
 */
enum class TextContentKind {
    CurrencyName,
    CurrencySymbol,
    CurrencyDescription,
}

/**
 * What a moderated image will be used as. The attestation is bound to the kind, as with
 * [TextContentKind].
 */
enum class ImageContentKind {
    CurrencyIcon,
}
