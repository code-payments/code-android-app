package com.getcode.opencode.transport

import com.getcode.wire.canonical.encodeCanonical
import com.squareup.wire.Message

/**
 * Encodes this OCP message in canonical order, the bytes OCP signs and sends. See
 * [com.getcode.wire.canonical.encodeCanonical].
 */
public fun Message<*, *>.encodeCanonical(): ByteArray = encodeCanonical(OcpCanonicalSchema)
