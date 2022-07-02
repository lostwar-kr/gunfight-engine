package kr.lostwar.util

import kr.lostwar.GunfightEngine
import kr.lostwar.GunfightEngine.Companion.plugin
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.configuration.ConfigurationSection

class SoundInfo private constructor(
    val custom: Boolean,
    val category: SoundCategory = SoundCategory.BLOCKS,
    val volume: Float = 1f,
    val pitch: Float = 1f,
    val delay: Int = 0
) {

    private lateinit var soundString: String
    private lateinit var soundEnum: Sound
    constructor(
        sound: String,
        category: SoundCategory = SoundCategory.BLOCKS,
        volume: Float = 1f,
        pitch: Float = 1f,
        delay: Int = 0
    ) : this(true, category, volume, pitch, delay) {
        soundString = sound
    }
    constructor(
        sound: Sound,
        category: SoundCategory = SoundCategory.BLOCKS,
        volume: Float = 1f,
        pitch: Float = 1f,
        delay: Int = 0
    ) : this(false, category, volume, pitch, delay) {
        soundEnum = sound
    }

    companion object {
        fun ConfigurationSection.getSounds(key: String, def: List<SoundInfo> = emptyList()): List<SoundInfo> {
            if(!isList(key)) return def
            val list = getStringList(key)
            return parse(list)
        }
        fun parse(list: List<String>): List<SoundInfo> {
            return list.mapNotNull { parse(it) }
        }
        fun parse(raw: String?, def: SoundInfo? = null): SoundInfo? {
            if(raw == null) return def

            return try {
                val split = raw.split('-').map { it.trim() }
                when(split.size) {
                    4 -> SoundInfo(
                            Sound.valueOf(split[0].uppercase()),
                            SoundCategory.BLOCKS,
                            split[1].toFloat(),
                            split[2].toFloat(),
                            split[3].toInt(),
                        )
                    5 -> SoundInfo(
                            split[0],
                            SoundCategory.BLOCKS,
                            split[1].toFloat(),
                            split[2].toFloat(),
                            split[3].toInt(),
                    )
                    else -> def
                }
            }catch (e: java.lang.Exception) {
                plugin.logWarn("failed to parse sound: $raw")
                def
            }
        }
    }
}