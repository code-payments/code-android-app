package com.getcode.utils

interface BreadcrumbSink {
    fun record(
        message: String,
        metadata: Map<String, Any>,
        type: TraceType,
    )
}
