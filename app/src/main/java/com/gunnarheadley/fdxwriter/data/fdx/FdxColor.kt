package com.gunnarheadley.fdxwriter.data.fdx

/**
 * Final Draft stores colors as `#RRRRGGGGBBBB` — 16 bits per channel (12 hex digits).
 * Android works in 8-bit-per-channel ARGB, so we keep the raw FDX string in the model and
 * convert only for display.
 */
object FdxColor {

    /** Parse an FDX color (e.g. `#EBEB62627B7B`) into an opaque ARGB int, or null if malformed. */
    fun toArgb(fdx: String?): Int? {
        val s = fdx?.trim()?.removePrefix("#") ?: return null
        return try {
            when (s.length) {
                12 -> {
                    // 16 bits per channel: take the high byte of each (RRRR GGGG BBBB).
                    val r = s.substring(0, 2).toInt(16)
                    val g = s.substring(4, 6).toInt(16)
                    val b = s.substring(8, 10).toInt(16)
                    (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
                6 -> {
                    // 8 bits per channel (RR GG BB).
                    val r = s.substring(0, 2).toInt(16)
                    val g = s.substring(2, 4).toInt(16)
                    val b = s.substring(4, 6).toInt(16)
                    (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
                else -> null
            }
        } catch (e: NumberFormatException) {
            null
        }
    }

    /** Convert an ARGB int back into an FDX color string by replicating each 8-bit channel. */
    fun fromArgb(argb: Int): String {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return "#" + dup(r) + dup(g) + dup(b)
    }

    private fun dup(v: Int): String = "%02X%02X".format(v, v)

    /** White in FDX form, the common default for note/beat colors. */
    const val WHITE = "#FFFFFFFFFFFF"
}
