package kr.lostwar.gun.weapon.components

import kr.lostwar.GunfightEngine.Companion.plugin
import kr.lostwar.gun.weapon.WeaponComponent
import kr.lostwar.gun.weapon.WeaponPlayerEventListener
import kr.lostwar.gun.weapon.WeaponType
import kr.lostwar.gun.weapon.event.WeaponShootEvent
import kr.lostwar.util.item.ItemData
import kr.lostwar.util.math.VectorUtil.times
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.event.Event
import org.bukkit.scheduler.BukkitRunnable
import kotlin.math.abs

class Grenade(
    config: ConfigurationSection?,
    weaponType: WeaponType,
    parent: Grenade?,
) : WeaponComponent(config, weaponType, parent) {

    val delay: Int = getInt("delay", parent?.delay, 20)
    val power: Double = getDouble("power", parent?.power, 1.0)
    val frictionSpeedMultiplier: Double = getDouble("friction", parent?.frictionSpeedMultiplier, 0.9)
    val epsilon: Double = getDouble("epsilon", parent?.epsilon, 0.01)
    val startDistance: Double = getDouble("startDistance", parent?.startDistance, 0.1)
    val item: ItemData = getItemData("item", parent?.item, ItemData(Material.GREEN_DYE))!!

    private val onShoot = WeaponPlayerEventListener(WeaponShootEvent::class.java) { event ->
        val ray = event.shootRay
        val direction = ray.direction

        val item = player.world.dropItem(player.eyeLocation.add(direction * startDistance), item.toItemStack()).apply {
            velocity = direction * power
            pickupDelay = Int.MAX_VALUE
            isInvulnerable = true
        }
        object : BukkitRunnable() {
            private var count = delay
            override fun run() {
                if(count <= 0 || item.isDead) {
                    this@Grenade.weapon.explosion?.apply { createExplosion(item.location) }
                    item.remove()
                    cancel()
                    return
                }
                bounce()
                --count
            }
            private var lastVelocity = item.velocity
            private fun bounce() {
                val oldX = lastVelocity.x
                val oldY = lastVelocity.y
                val oldZ = lastVelocity.z
                val new = item.velocity
                val newX = new.x
                val newY = new.y
                val newZ = new.z
                if(newX == 0.0 && oldX != 0.0) {
                    new.x = -oldX * frictionSpeedMultiplier
                    if(abs(new.x) < epsilon) new.x = 0.0
                }
                if(newY == 0.0 && oldY != 0.0) {
                    new.y = -oldY * frictionSpeedMultiplier
                    if(abs(new.y) < epsilon) new.y = 0.0
                }
                if(newZ == 0.0 && oldZ != 0.0) {
                    new.z = -oldZ * frictionSpeedMultiplier
                    if(abs(new.z) < epsilon) new.z = 0.0
                }
                item.velocity = new
                lastVelocity = new
            }
        }.runTaskTimer(plugin, 0, 1)
    }

    override val listeners: List<WeaponPlayerEventListener<out Event>> = listOf(
        onShoot,
    )

}