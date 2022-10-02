package kr.lostwar.gun.weapon.components

import kr.lostwar.GunfightEngine.Companion.plugin
import kr.lostwar.gun.weapon.WeaponComponent
import kr.lostwar.gun.weapon.WeaponPlayerEventListener
import kr.lostwar.gun.weapon.WeaponType
import kr.lostwar.gun.weapon.event.WeaponShootEvent
import kr.lostwar.util.ExtraUtil.armorStandOffset
import kr.lostwar.util.item.ItemData
import kr.lostwar.util.math.VectorUtil
import kr.lostwar.util.math.VectorUtil.times
import kr.lostwar.util.math.VectorUtil.unaryMinus
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.ArmorStand.LockType
import org.bukkit.event.Event
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.EulerAngle
import kotlin.math.abs

class Grenade(
    config: ConfigurationSection?,
    weaponType: WeaponType,
    parent: Grenade?,
) : WeaponComponent(config, weaponType, parent) {
    companion object {
        private val zero = VectorUtil.ZERO
        private val pi = Math.PI
        private val piHalf = pi / 2.0
        private val eulerByBlockFace = mapOf<BlockFace, EulerAngle>(
            BlockFace.UP    to EulerAngle(0.0, 0.0, 0.0),
            BlockFace.DOWN  to EulerAngle(pi, 0.0, 0.0),
            BlockFace.EAST  to EulerAngle(piHalf, 0.0, 0.0),
            BlockFace.WEST  to EulerAngle(piHalf, 0.0, 0.0),
            BlockFace.NORTH to EulerAngle(piHalf, 0.0, 0.0),
            BlockFace.SOUTH to EulerAngle(piHalf, 0.0, 0.0),
        )
    }

    val delay: Int = getInt("delay", parent?.delay, 20)
    val power: Double = getDouble("power", parent?.power, 1.0)
    val frictionSpeedMultiplier: Double = getDouble("friction", parent?.frictionSpeedMultiplier, 0.9)
    val epsilon: Double = getDouble("epsilon", parent?.epsilon, 0.01)
    val startDistance: Double = getDouble("startDistance", parent?.startDistance, 0.1)
    val item: ItemData = getItemData("item", parent?.item, ItemData(Material.GREEN_DYE))!!

    val isSticky: Boolean = getBoolean("sticky.enable", parent?.isSticky, false)
    val stickItem: ItemData = getItemData("item", parent?.stickItem, item)!!


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
            private var isStuck = false
            private var stuckEntity: ArmorStand? = null
            override fun run() {
                if(count <= 0 || !isStuck && item.isDead) {
                    val location = stuckEntity?.location ?: item.location
                    if(!item.isDead) {
                        item.remove()
                    }
                    stuckEntity?.remove()
                    stuckEntity = null
                    this@Grenade.weapon.explosion?.apply { createExplosion(location) }
                    cancel()
                    return
                }
                bounce()
                --count
            }
            private var lastVelocity = item.velocity
            private fun bounce() {
                if(isStuck) {
                    item.velocity = zero
                    return
                }
                var boundFace: BlockFace? = null
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
                    else boundFace = if(oldX < 0) BlockFace.EAST else BlockFace.WEST
                }
                if(newY == 0.0 && oldY != 0.0) {
                    new.y = -oldY * frictionSpeedMultiplier
                    if(abs(new.y) < epsilon) new.y = 0.0
                    else boundFace = if(oldY < 0) BlockFace.UP else BlockFace.DOWN
                }
                if(newZ == 0.0 && oldZ != 0.0) {
                    new.z = -oldZ * frictionSpeedMultiplier
                    if(abs(new.z) < epsilon) new.z = 0.0
                    else boundFace = if(oldZ < 0) BlockFace.SOUTH else BlockFace.NORTH
                }
                if(!isStuck && isSticky && boundFace != null) {
                    val world = player.world
                    val location = item.location
                        .subtract(oldX, oldY, oldZ)
                    val hit = world.rayTraceBlocks(location, -boundFace.direction, 1.5)
                    if(hit != null) {
                        isStuck = true
                        val spawnLocation: Location = hit.hitPosition.toLocation(world)
                        spawnLocation.subtract(EquipmentSlot.HEAD.armorStandOffset)
                        spawnLocation.direction = boundFace.direction
                        spawnLocation.pitch = 0f
                        stuckEntity = world.spawn(spawnLocation, ArmorStand::class.java) { entity ->
                            entity.isMarker = true
//                        entity.isVisible = false
                            entity.isInvulnerable = true
                            entity.setItem(EquipmentSlot.HEAD, item.itemStack)
                            entity.setGravity(false)
                            for(slot in EquipmentSlot.values()) {
                                for (type in LockType.values()) {
                                    entity.addEquipmentLock(slot, type)
                                }
                            }
                            entity.headPose = eulerByBlockFace[boundFace] ?: EulerAngle(0.0, 0.0, 0.0)
                        }
                        item.remove()
                        item.setGravity(false)
                    }
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