package xyz.flipchat.services.domain.model.query

typealias PagingToken = List<Byte>

data class QueryOptions(
    val limit: Int = 100,
    val token: PagingToken? = null,
    val descending: Boolean = true
)
