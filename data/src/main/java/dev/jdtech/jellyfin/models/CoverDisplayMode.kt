package dev.jdtech.jellyfin.models

/** 列表封面的显示形态：竖图取 primary，横图取 backdrop。 */
enum class CoverDisplayMode {
    PORTRAIT,
    LANDSCAPE;

    companion object {
        val defaultValue = PORTRAIT

        fun fromString(string: String): CoverDisplayMode {
            return try {
                valueOf(string)
            } catch (_: IllegalArgumentException) {
                defaultValue
            }
        }
    }
}
