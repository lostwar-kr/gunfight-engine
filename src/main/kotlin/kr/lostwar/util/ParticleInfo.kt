package kr.lostwar.util

import kr.lostwar.gun.GunEngine
import kr.lostwar.util.ColorUtil.getBukkitColor
import kr.lostwar.util.math.VectorUtil.getBukkitVector
import kr.lostwar.util.ui.text.consoleWarn
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Particle.DustOptions
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import java.io.File
import java.util.*
import kotlin.collections.HashMap

data class ParticleInfo(
    val key: String,
    val type: Particle,
    val offset: Vector,
    val count: Int,
    val extra: Double,
    val data: Any? = null,
    val forced: Boolean = false,
) {
    fun spawnAt(
        location: Location,
        receiver: List<Player> = location.world.players,
        source: Player? = null,
        offset: Vector = this.offset,
        count: Int = this.count,
        extra: Double = this.extra,
        data: Any? = this.data,
        forced: Boolean = this.forced,
    ) {
        location.world.spawnParticle(
            type,
            receiver,
            source,
            location.x,
            location.y,
            location.z,
            count,
            offset.x,
            offset.y,
            offset.z,
            extra,
            data,
            forced
        )
    }
    companion object {
        private val path = GunEngine.directory + "particles/"
        private val byKey = HashMap<String, ParticleInfo>()
        operator fun get(key: String) = byKey[key]
        fun load() {
            byKey.clear()
            val folder = File(path)
            val files = folder.listFiles()
            if (files == null || files.isEmpty()) {
                return
            }
            val queue = LinkedList<File>()
            queue.addFirst(folder)

            val particleFiles = arrayListOf<File>()
            while (queue.isNotEmpty()) {
                val file = queue.poll()
                if (file.isDirectory) {
                    val children = file.listFiles() ?: continue
                    queue.addAll(children)
                    continue
                }
                if (file.name.endsWith(".yml")) {
                    particleFiles.add(file)
                }
            }

            particleFiles.forEach { file ->
                val config = Config(file)
                for (key in config.getKeys(false)) {
                    val info = config.getParticleInfo(key) ?: continue
                    byKey[key] = info
                }
            }
            GunEngine.log("${byKey.size}개의 Particle 불러옴")
        }
        private fun nullWarn(message: String) = consoleWarn(message).let { null }
        fun ConfigurationSection.getParticleInfo(key: String): ParticleInfo? {
            val section = getConfigurationSection(key) ?: return null

            val rawParticle = section.getString("type")
                ?: return nullWarn("failed to loading particle info ${key}: particle type not specified")
            val particle = try {
                Particle.valueOf(rawParticle)
            } catch (e: Exception) {
                return nullWarn("failed to loading particle info ${key}: invalid particle type ${rawParticle}")
            }

            val offset = section.getBukkitVector("offset") ?: Vector()
            val count = section.getInt("count", 0)
            val extra = section.getDouble("extra", 0.0)
            val data = section.getParticleData(key, particle)
            val forced = section.getBoolean("forced", false)

            return ParticleInfo(
                key,
                particle,
                offset,
                count,
                extra,
                data,
                forced,
            )
        }

        fun ConfigurationSection.getParticleData(parentKey: String, type: Particle): Any? {
            val key = "data"
            return when(type.dataType) {
                Void::class.java -> null
                Integer::class.java -> getInt(key, 0)
                Float::class.java -> getDouble(key, 0.0).toFloat()
                DustOptions::class.java -> {
                    val section = getConfigurationSection(key)
                        ?: return nullWarn("failed to loading dust data from ${parentKey}: no section")
                    val color = section.getBukkitColor("color") ?: Color.WHITE
                    val size = section.getDouble("size", 0.0).toFloat()
                    return DustOptions(color, size)
                }
//                ItemStack::class.java -> {
//
//                }
//                BlockData::class.java -> {
//
//                }
//                DustTransition::class.java -> {
//
//                }
//                Vibration::class.java -> {
//
//                }
//                MaterialData::class.java -> {
//
//                }
                else -> if(contains(key)) nullWarn("currently not supported extra data on type: ${type}") else null
            }
        }
    }

}

class ParticleSet(
    private val set: Set<ParticleInfo>
) : Set<ParticleInfo> by set {

    fun executeEach(block: (ParticleInfo) -> Unit) {
        forEach(block)
    }
    companion object {
        val emptySet = ParticleSet(emptySet())
        private fun nullWarn(message: String) = consoleWarn(message).let { null }
        fun getParticleSetOrNull(section: ConfigurationSection, key: String): ParticleSet? {
            with(section) {
                val raw = get(key)
                if(raw is String) {
                    val info = ParticleInfo.get(raw)
                        ?: return nullWarn("invalid particle info ${raw} while loading ${key}")
                    return ParticleSet(setOf(info))
                }
                if(raw is List<*>) {
                    return ParticleSet(buildSet {
                        for(rawElement in raw) {
                            if(rawElement !is String) {
                                consoleWarn("invalid particle info while loading ${key}: ${rawElement}")
                                continue
                            }
                            val particle = ParticleInfo.get(rawElement)
                            if(particle == null) {
                                consoleWarn("invalid particle info ${rawElement} while loading ${key}")
                                continue
                            }
                            add(particle)
                        }
                    })
                }
                return null
            }
        }
    }

}