package com.wenjunhuang.codeepiphany.presentation

import com.wenjunhuang.codeepiphany.domain.Pagination

/** 只保存已提交条件；输入草稿属于搜索栏，不影响筛选、排序或翻页请求。 */
internal class ProblemQueryState {
    var mode = QueryMode.QueryParameters
        private set
    var parameters = ProblemCriteria()
    var company = CompanyCriteria()
    val pages = QueryMode.entries.associateWith { Pagination() }.toMutableMap()

    fun submitKeyword(keyword: String) {
        when (mode) {
            QueryMode.QueryParameters -> parameters = parameters.withKeyword(keyword)
            QueryMode.CompanyQuery -> company = company.withKeyword(keyword)
        }
        pages[mode] = pages.getValue(mode).resetToFirstPage()
    }

    fun switchTo(next: QueryMode) {
        if (mode == next) return
        mode = next
        when (mode) {
            QueryMode.QueryParameters -> parameters = parameters.copy(orderBy = null)
            QueryMode.CompanyQuery -> company = company.copy(orderBy = null)
        }
        pages[mode] = pages.getValue(mode).resetToFirstPage()
    }
}
