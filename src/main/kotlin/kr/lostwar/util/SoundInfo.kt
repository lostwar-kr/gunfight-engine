package kr.lostwar.util

import kr.lostwar.GunfightEngine.Companion.plugin
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

    fun playToPlayer(player: Player, location: Location = player.eyeLocation.add(player.eyeLocation.direction.multiply(0.5))) {
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
}

class SoundClip(
    val sounds: List<SoundInfo>
) : List<SoundInfo> by sounds {
    private fun play(offset: Int = 0, playMethod: SoundInfo.() -> Unit): BukkitTask? {
        if(isEmpty()) return null
        val soundQueue = LinkedList<ArrayList<SoundInfo>>()

        var lastDelay = -1
        var index = 0
        for(sound in this.sortedBy { it.delay }) {
            if(sound.delay < offset) continue
            val list = if(lastDelay != sound.delay) {
                ++index
                lastDelay = sound.delay
                val l = ArrayList<SoundInfo>()
                soundQueue.addLast(l)
                l
            }else{
                soundQueue.peekLast()
            }
            list.add(sound)
        }
        if(soundQueue.isEmpty()) return null
        return object : BukkitRunnable() {
            val iterator = soundQueue.iterator()
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
    fun playAt(player: Player, offset: Int = 0)
            = play(offset) { playAt(player.location) }
    fun playAt(location: Location, offset: Int = 0)
            = play(offset) { playAt(location) }
    fun playToPlayer(player: Player, offset: Int = 0)
            = play(offset) { playToPlayer(player) }

    companion object {
        val emptyClip = SoundClip(emptyList())
        fun ConfigurationSection.getSounds(key: String, def: SoundClip = emptyClip): SoundClip {
            if(!isList(key)) return def
            val list = getStringList(key)
            return parse(list)
        }
        fun parse(list: List<String>): SoundClip {
            return SoundClip(list.mapNotNull { SoundInfo.parse(it) })
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

}