package kr.lostwar.gun.weapon.components

import kr.lostwar.gun.weapon.WeaponComponent
import kr.lostwar.gun.weapon.WeaponPlayer
import kr.lostwar.gun.weapon.WeaponPlayerEventListener
import kr.lostwar.gun.weapon.WeaponType
import kr.lostwar.util.ParticleSet
import kr.lostwar.util.SoundClip
import kr.lostwar.util.math.VectorUtil.minus
import kr.lostwar.util.math.VectorUtil.normalized
import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.LivingEntity
import org.bukkit.event.Event

class Explosion(
    config: ConfigurationSection?,
    weaponType: WeaponType,
    parent: Explosion?,
) : WeaponComponent(config, weaponType, parent) {

    val radius: Double = getDouble("radius", parent?.radius, 1.0)
    val canSuicide: Boolean = getBoolean("canSuicide", parent?.canSuicide, false)
    val damageReducePerDistance: Double = getDouble("damageReducePerDistance", parent?.damageReducePerDistance, 1.0)

    val sound: SoundClip = getSoundClip("sound", parent?.sound)
    val effect: ParticleSet = getParticleSet("effect", parent?.effect)

    fun WeaponPlayer.createExplosion(location: Location) {
        effect.executeEach { it.spawnAt(location, forced = true) }
        sound.playAt(location)

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
    private fun LivingEntity.getCenterLocation() = location.add(0.0, height/2.0, 0.0)

    override val listeners: List<WeaponPlayerEventListener<out Event>> = listOf(

    )

}