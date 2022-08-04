package kr.lostwar.util

import kr.lostwar.GunfightEngine.Companion.plugin
import kr.lostwar.gun.GunEngine
import kr.lostwar.util.ExtraUtil.joinToString
import kr.lostwar.util.ui.text.console
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.util.LinkedList

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

    fun playAt(location: Location, volume: Float = this.volume, pitch: Float = this.pitch) {
        if(custom) {
            location.world.playSound(location, soundString, category, volume, pitch)
        }else {
            location.world.playSound(location, soundEnum, category, volume, pitch)
        }
    }

    fun playToPlayer(
        player: Player,
        location: Location = player.eyeLocation.add(player.eyeLocation.direction.multiply(0.5)),
        volume: Float = this.volume,
        pitch: Float = this.pitch
    ) {
//        console("play sound(${this}) to player(${player.name}) at ${location} with volume ${volume}, pitch ${pitch}")
        if(custom) {
            player.playSound(location, soundString, category, volume, pitch)
        }else {
            player.playSound(location, soundEnum, category, volume, pitch)
        }
    }

    companion object {
        fun parse(raw: String?, def: SoundInfo? = null): SoundInfo? {
            if(raw == null) return def

            return try {
                val split = raw.split('-').map { it.trim() }
                if(split.isEmpty()) {
                    throw Exception("cannot parse empty sound")
                }
                val rawSound = split[0]
                val soundEnum = try {
                    Sound.valueOf(rawSound)
                } catch (_: Exception) {
                    if(!rawSound.contains('.')) {
                        GunEngine.logWarn("${rawSound} seems to Enum Sound, but failed to find, used as named sound")
                    }
                    null
                }
                val volume = split.getOrNull(1)?.toFloat() ?: 1f
                val pitch = split.getOrNull(2)?.toFloat() ?: 1f
                val delay = split.getOrNull(3)?.toInt() ?: 0
                if(soundEnum != null) {
                    SoundInfo(
                        soundEnum,
                        SoundCategory.BLOCKS,
                        volume, pitch, delay
                    )
                }else{
                    SoundInfo(
                        rawSound,
                        SoundCategory.BLOCKS,
                        volume, pitch, delay
                    )
                }
            }catch (e: Exception) {
                plugin.logWarn("failed to parse sound: $raw, $e")
                def
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if(other is SoundInfo) {
            return custom == other.custom && (if(custom) soundString == other.soundString else soundEnum == other.soundEnum)
        }
        return super.equals(other)
    }

    override fun hashCode(): Int {
        var hashCode = if(custom) 1 else 0
        hashCode = 31 * hashCode + if(custom) soundString.hashCode() else soundEnum.hashCode()
        return hashCode
    }

    override fun toString(): String {
        return if(custom) {
            "${soundString}-${volume}-${pitch}"
        }else{
            "${soundEnum}-${volume}-${pitch}"
        }
    }
}

class SoundClip(
    val sounds: List<SoundInfo>
) : List<SoundInfo> by sounds {
    private val queue = LinkedList<ArrayList<SoundInfo>>().apply {
        var lastDelay = -1
        for(sound in sounds) {
            val list = if(lastDelay != sound.delay) {
                lastDelay = sound.delay
                val l = ArrayList<SoundInfo>()
                addLast(l)
                l
            }else{
                peekLast()
            }
            list.add(sound)
        }
    }
    private fun play(offset: Int = 0, playMethod: SoundInfo.() -> Unit): BukkitTask? {
        if(isEmpty()) return null
        if(queue.isEmpty()) return null
        return object : BukkitRunnable() {
            val iterator = queue.iterator()
            var current = iterator.next()
            var currentDelay = current.first().delay
            var count = offset
            override fun run() {
                if(currentDelay == count) {
                    current.forEach { it.playMethod() }
                    if(!iterator.hasNext()) {
                        cancel()
                        return
                    }
                    current = iterator.next()
                    currentDelay = current.first().delay
                }
                ++count
            }
        }.runTaskTimer(plugin, 0, 1)
    }
    fun playAt(player: Player, offset: Int = 0, volume: Float? = null, pitch: Float? = null)
            = play(offset) { playAt(player.location, volume ?: this.volume, pitch ?: this.pitch) }
    fun playAt(location: Location, offset: Int = 0, volume: Float? = null, pitch: Float? = null)
            = play(offset) { playAt(location, volume ?: this.volume, pitch ?: this.pitch) }
    fun playToPlayer(player: Player, offset: Int = 0, volume: Float? = null, pitch: Float? = null)
            = play(offset) { playToPlayer(player, volume = volume ?: this.volume, pitch = pitch ?: this.pitch) }

    companion object {
        val emptyClip = SoundClip(emptyList())
        fun ConfigurationSection.getSounds(key: String, def: SoundClip = emptyClip): SoundClip {
            if(!isList(key)) return def
            val list = getStringList(key)
            return parse(list)
        }
        fun parse(list: List<String>): SoundClip {
            return SoundClip(list.mapNotNull { SoundInfo.parse(it) }.sortedBy { it.delay })
        }
    }

    override fun equals(other: Any?): Boolean {
        if(other is SoundClip) {
            if(sounds.size != other.sounds.size) return false
//            for((index, sound) in sounds.withIndex()) {
//                if(sound != other.sounds[index]) return false
//            }
            return hashCode() == other.hashCode()
        }
        return super.equals(other)
    }

    override fun hashCode(): Int {
        var hashCode = sounds.size
        for(sound in sounds) {
            hashCode = 31 * hashCode + sound.hashCode()
        }
        return hashCode
    }

    override fun toString(): String {
        return sounds.joinToString()
    }

}