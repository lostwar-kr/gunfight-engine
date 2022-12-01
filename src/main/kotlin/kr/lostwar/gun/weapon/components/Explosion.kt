package kr.lostwar.gun.weapon.components

import kr.lostwar.GunfightEngine.Companion.plugin
import kr.lostwar.gun.weapon.WeaponComponent
import kr.lostwar.gun.weapon.WeaponPlayer
import kr.lostwar.gun.weapon.WeaponPlayerEventListener
import kr.lostwar.gun.weapon.WeaponType
import kr.lostwar.gun.weapon.components.Explosion.Vector3Int.Companion.plus
import kr.lostwar.util.ParticleSet
import kr.lostwar.util.SoundClip
import kr.lostwar.util.math.VectorUtil.minus
import kr.lostwar.util.math.VectorUtil.normalized
import kr.lostwar.util.nms.PacketUtil.sendMultiBlockChange
import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.LivingEntity
import org.bukkit.event.Event
import org.bukkit.scheduler.BukkitRunnable
import kotlin.math.floor

class Explosion(
    config: ConfigurationSection?,
    weaponType: WeaponType,
    parent: Explosion?,
) : WeaponComponent(config, weaponType, parent) {
    companion object {
        private val airs = setOf(
            Material.AIR,
            Material.CAVE_AIR,
            Material.VOID_AIR,
        )
    }

    val radius: Double = getDouble("radius", parent?.radius, 1.0)
    val canSuicide: Boolean = getBoolean("canSuicide", parent?.canSuicide, false)
    val damageReducePerDistance: Double = getDouble("damageReducePerDistance", parent?.damageReducePerDistance, 1.0)

    val sound: SoundClip = getSoundClip("sound", parent?.sound)
    val effect: ParticleSet = getParticleSet("effect", parent?.effect)

    val useSmoke: Boolean = getBoolean("smoke.enable", parent?.useSmoke, false)
    val smokeRadius: Int = getInt("smoke.radius", parent?.smokeRadius, 5)
    val smokeMaterial: Material = getEnumString("smoke.material", parent?.smokeMaterial, Material.GRASS)
    val smokeDuration: Int = getInt("smoke.duration", parent?.smokeDuration, 60)

    private data class Vector3Int(
        val x: Int,
        val y: Int,
        val z: Int,
    ) {
        companion object {
            private fun Double.floorToInt() = floor(this).toInt()
            operator fun Block.plus(delta: Vector3Int): Block {
                return this.getRelative(delta.x, delta.y, delta.z)
            }
        }
        constructor(location: Location) : this(
            location.x.floorToInt(),
            location.y.floorToInt(),
            location.z.floorToInt()
        )
        operator fun plus(other: Vector3Int) = Vector3Int(x + other.x, y + other.y, z + other.z)
    }
    private val smokeSphereDeltaList: List<Vector3Int> = if(!useSmoke) emptyList() else buildList {
        val range = -smokeRadius .. smokeRadius
        val radiusSquared = smokeRadius * smokeRadius
        for(x in range) for(y in range) for(z in range) {
            val lengthSquared = x * x + y * y + z * z
            if(lengthSquared > radiusSquared) continue
            add(Vector3Int(x, y, z))
        }
    }

    fun WeaponPlayer.createExplosion(location: Location) {
        effect.executeEach { it.spawnAt(location, forced = true) }
        sound.playAt(location)

        if(useSmoke) {
            val blocks = ArrayList<Block>(smokeSphereDeltaList.size)
            val center = location.block
            for(delta in smokeSphereDeltaList) {
                val block = center + delta
                if(block.type !in airs) continue
                blocks.add(block)
            }
            if(blocks.isNotEmpty()) object : BukkitRunnable() {
                val world = location.world
                init {
                    // 연막 설치
                    val blockChanges = blocks.associate {
                        it.location to smokeMaterial.createBlockData()
                    }
                    world.players.sendMultiBlockChange(blockChanges, true)
                }
                private var count = smokeDuration
                override fun run() {
                    if(count <= 0) {
                        // 원래대로 복
                        val blockChanges = blocks.associate {
                            it.location to it.location.block.blockData
                        }
                        world.players.sendMultiBlockChange(blockChanges, true)
                        cancel()
                        return
                    }
                    --count
                }
            }.runTaskTimer(plugin, 0, 1)
        }

        if(radius > 0) {
            val world = location.world
            val entities = world.getNearbyLivingEntities(location, radius)
            for(entity in entities) {
                if(!canSuicide && entity == player) {
                    continue
                }
                val entityCenterLocation = entity.getCenterLocation()
                val ray = (entityCenterLocation - location).toVector()
                val distance = ray.length()
                if(distance > radius) continue // circle
                val direction = ray.normalized
                val result = world.rayTraceBlocks(location, direction, distance, FluidCollisionMode.NEVER, true)
                if(result != null && result.hitBlock != null) continue

                with(this@Explosion.weapon.hit) {
                    hitEntity(
                        victim = entity,
                        distance = distance,
                        location = entityCenterLocation,
                    ) {
                        it - (distance * damageReducePerDistance)
                    }
                }
            }
        }
    }
    private fun LivingEntity.getCenterLocation() = location.add(0.0, height/2.0, 0.0)

    override val listeners: List<WeaponPlayerEventListener<out Event>> = listOf(

    )

}