package dev.jdtech.jellyfin.presentation.film.components

import androidx.annotation.StringRes
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.MetadataFacet

/** 各元数据维度在界面上的标题，浏览入口与列表页共用同一套文案。 */
@StringRes
fun MetadataFacet.titleRes(): Int =
    when (this) {
        MetadataFacet.GENRE -> CoreR.string.genres
        MetadataFacet.TAG -> CoreR.string.tags
        MetadataFacet.STUDIO -> CoreR.string.studios
        MetadataFacet.OFFICIAL_RATING -> CoreR.string.official_ratings
        MetadataFacet.YEAR -> CoreR.string.years
        MetadataFacet.ACTOR -> CoreR.string.actors
        MetadataFacet.DIRECTOR -> CoreR.string.director
        MetadataFacet.WRITER -> CoreR.string.writers
    }
