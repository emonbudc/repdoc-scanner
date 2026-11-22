package com.repdoc.scanner.data

data class Page(
    val id: String,
    val imagePath: String,
    val ocrText: String? = null,
    val originalImagePath: String? = null
)

data class Folder(
    val id: String,
    val name: String,
    val createdAt: Long
)

data class Document(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pages: List<Page>,
    val folderId: String? = null
) {
    val pageCount: Int get() = pages.size
}

data class SearchHit(
    val document: Document,
    val page: Page?,
    val snippet: String
)
