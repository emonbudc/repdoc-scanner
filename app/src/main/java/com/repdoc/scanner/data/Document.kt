package com.repdoc.scanner.data

data class Page(
    val id: String,
    val imagePath: String,
    val ocrText: String? = null
)

data class Document(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pages: List<Page>
) {
    val pageCount: Int get() = pages.size
}
