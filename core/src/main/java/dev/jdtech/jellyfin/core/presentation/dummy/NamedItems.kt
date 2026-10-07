package dev.jdtech.jellyfin.core.presentation.dummy

import dev.jdtech.jellyfin.models.FindroidImages
import dev.jdtech.jellyfin.models.FindroidNamedItem
import java.util.UUID

/** 预览用的类别 / 制片公司数据，ID 固定，保证预览重组时列表 key 稳定。 */
val dummyNamedItems: List<FindroidNamedItem> =
    listOf(
            "0f8fad5b-d9cb-469f-a165-70867728950e" to "Action",
            "7c9e6679-7425-40de-944b-e07fc1f90ae7" to "Comedy",
            "3f2504e0-4f89-11d3-9a0c-0305e82c3301" to "Documentary",
            "9b2c1b0e-5a1f-4b1e-9d5c-1f2e3d4c5b6a" to "Drama",
            "1c2d3e4f-5a6b-7c8d-9e0f-1a2b3c4d5e6f" to "Science Fiction",
            "5f4dcc3b-5d16-4a0e-9b2c-7e1d2f3a4b5c" to "Warner Bros.",
        )
        .map { (id, name) ->
            FindroidNamedItem(id = UUID.fromString(id), name = name, images = FindroidImages())
        }
