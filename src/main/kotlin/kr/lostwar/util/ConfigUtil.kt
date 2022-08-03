package kr.lostwar.util

import kr.lostwar.util.ui.text.consoleWarn
import kr.lostwar.vehicle.VehicleEngine
import org.bukkit.configuration.ConfigurationSection

object ConfigUtil {

    fun ConfigurationSection.getFloatRange(
        key: String,
        def: FloatRange = 0f..0f,
        clampRange: FloatRange? = null,
    ): FloatRange = getFloatRangeOrNull(key, clampRange) ?: def

    fun ConfigurationSection.getFloatRangeOrNull(
        key: String,
        clampRange: FloatRange? = null,
    ): FloatRange? {
        val raw = getString(key) ?: return null
        val split = raw.split("..").map { it.trim() }
        if(split.size != 2) {
            consoleWarn("cannot parse range: ${raw}")
            return null
        }
        val min = split[0].toFloatOrNull()
        if(min == null){
            consoleWarn("cannot parse range: ${raw} (invalid minimum value: ${split[0]})")
            return null
        }
        val max = split[1].toFloatOrNull()
        if(max == null) {
            consoleWarn("cannot parse range: ${raw} (invalid maximum value: ${split[1]})")
            return null
        }
        val start = Math.min(min, max).coerceAtLeast(clampRange?.start ?: -Float.MAX_VALUE)
        val end = Math.max(min, max).coerceAtMost(clampRange?.endInclusive ?: Float.MAX_VALUE)
        return start .. end
    }

    fun ConfigurationSection.getDoubleRange(
        key: String,
        def: DoubleRange = 0.0..0.0,
        clampRange: DoubleRange? = null,
    ): DoubleRange = getDoubleRangeOrNull(key, clampRange) ?: def
    fun ConfigurationSection.getDoubleRangeOrNull(
        key: String,
        clampRange: DoubleRange? = null,
    ): DoubleRange? {
        val raw = getString(key) ?: return null
        val split = raw.split("..").map { it.trim() }
        if(split.size != 2) {
            consoleWarn("cannot parse range: ${raw}")
            return null
        }
        val min = split[0].toDoubleOrNull()
        if(min == null){
            consoleWarn("cannot parse range: ${raw} (invalid minimum value: ${split[0]})")
            return null
        }
        val max = split[1].toDoubleOrNull()
        if(max == null) {
            consoleWarn("cannot parse range: ${raw} (invalid maximum value: ${split[1]})")
            return null
        }
        val start = Math.min(min, max).coerceAtLeast(clampRange?.start ?: -Double.MAX_VALUE)
        val end = Math.max(min, max).coerceAtMost(clampRange?.endInclusive ?: Double.MAX_VALUE)
        return start .. end
    }

}