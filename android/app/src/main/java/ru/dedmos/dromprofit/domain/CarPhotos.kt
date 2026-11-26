package ru.dedmos.dromprofit.domain

internal const val MaxCarPhotos = 64

internal fun photosWithCover(photos: List<String>, cover: String?): List<String> =
    if (cover == null) emptyList() else listOf(cover) + photos.filterNot { it == cover }
