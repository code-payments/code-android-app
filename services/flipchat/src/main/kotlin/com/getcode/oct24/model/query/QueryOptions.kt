package com.getcode.oct24.model.query

import com.getcode.model.Cursor

data class QueryOptions(
    val limit: Int = 100,
    val offset: Int? = null,
    val cursor: Cursor? = null,
    val descending: Boolean = true
)
