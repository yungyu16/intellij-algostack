package io.github.yungyu16.algostack.domain

import kotlin.math.ceil
import kotlin.math.max

data class Pagination(val currentPage: Int = 1, val pageSize: PageSize = PageSize.Twenty, val totalSize: Int = 0) {
    init {
        require(currentPage > 0) { "Current page must be greater than 0" }
    }

    val totalPages: Int get() = max(1, ceil(totalSize.toDouble() / pageSize.value).toInt())
    val offset: Int get() = pageSize.value * (currentPage - 1)
    val limit: Int get() = pageSize.value
    fun resetToFirstPage() = copy(currentPage = 1)
}

enum class PageSize(val value: Int) {
    Twenty(20), Fifty(50), OneHundred(100);
}
