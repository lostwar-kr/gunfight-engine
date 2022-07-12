package kr.lostwar.vehicle.core

import com.destroystokyo.paper.event.server.ServerTickEndEvent
import kr.lostwar.GunfightEngine.Companion.plugin
import kr.lostwar.gun.GunEngine
import kr.lostwar.gun.weapon.WeaponPlayer.Companion.weaponPlayer
import kr.lostwar.util.DrawUtil
import kr.lostwar.util.math.VectorUtil.plus
import kr.lostwar.util.math.VectorUtil.times
import kr.lostwar.util.math.VectorUtil.toYawPitch
import kr.lostwar.vehicle.VehicleEngine
import kr.lostwar.vehicle.VehiclePlayer.Companion.vehiclePlayer
import kr.lostwar.vehicle.core.VehicleTransform.Companion.eulerRoll
import kr.lostwar.vehicle.core.VehicleTransform.Companion.eulerYaw
import kr.lostwar.vehicle.event.VehiclePreExitEvent
import kr.lostwar.vehicle.util.ExtraUtil.getOutline
import org.bukkit.*
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.util.EulerAngle
import org.bukkit.util.Vector
import org.spigotmc.event.entity.EntityDismountEvent
import java.util.*

open class VehicleEntity(
    base: VehicleInfo,
    val location: Location,
    val decoration: Boolean = false,
) {
    val world = location.world
    private var internalUUID: UUID? = null
    val uniqueId: UUID; get() = internalUUID!!

    open var base: VehicleInfo = base
        set(newBase) {
            field = newBase
            onReload(newBase)
        }

    protected open fun onReload(newBase: VehicleInfo) {
        if(modelEntities.size != newBase.models.size) {
            VehicleEngine.logWarn("unsafe hot reload at ${base.key}: inconsistency model count")
        }
        modelEntities.mapKeys { (info, entity) ->
            newBase.models[info.key] ?: info
        }.also { modelEntities.clear() }.forEach { (info, entity) ->
            modelEntities[info] = entity.apply {
                info.hitbox.apply(this)
            }
        }
        if(seatEntities.size != newBase.seats.size) {
            VehicleEngine.logWarn("unsafe hot reload at ${base.key}: inconsistency seat count")
        }
        seatEntities.forEachIndexed { index, entity ->
            if(newBase.seats.size <= index) return@forEachIndexed
            entity.apply {
                info = newBase.seats[index]
                info.hitbox.apply(this)
            }
        }
    }

    val transform: VehicleTransform = run {
        val position = location.toVector()
        val (yaw, pitch) = location.direction.toYawPitch()
        VehicleTransform(position, Vector(pitch, yaw, 0f))
    }

    var velocity: Vector = Vector()

    val modelEntities = base.models.mapKeys { it.value }.mapValues { spawnModel(it.value) }.toMutableMap()

    val seatEntities = base.seats.map { SeatEntity(it, spawnModel(it), this) }.toMutableList()
    val driverSeat = seatEntities[0]

    protected fun spawnModel(info: VehicleModelInfo): ArmorStand {
        val worldPosition = transform.transform(info, world)
        return (world.spawnEntity(worldPosition, EntityType.ARMOR_STAND) as ArmorStand).apply {
            isSmall = info.isSmall

            isInvisible = true
            info.hitbox.apply(this)
            isMarker = info.hitbox.isEmpty()
            if(info.type == EquipmentSlot.HAND || info.type == EquipmentSlot.OFF_HAND) {
                setArms(true)
                setPose(EquipmentSlot.HAND, transform.eulerAngleForPose)
                setPose(EquipmentSlot.OFF_HAND, transform.eulerAngleForPose)
            }
            equipment.setItem(info.type, info.item.toItemStack(), true)
            addEquipmentLock(info.type, ArmorStand.LockType.REMOVING_OR_CHANGING)
            setMetadata(Constants.vehicleEntityKey, FixedMetadataValue(plugin, internalUUID ?: uniqueId.also {
                internalUUID = it
                byUUID[it] = this@VehicleEntity
            }))

            if(decoration) {
                setGravity(false)
            }
        }
    }

    private val aabbParticle = Particle.DustOptions(Color.WHITE, 0.5f)
    var isDead = false
    protected open fun tick() {
        if(decoration) return
        if(!isDead && (modelEntities.values.any { it.isDead } || seatEntities.any { it.isDead })) {
            death()
            return
        }
        if(isDead) return

        DrawUtil.drawPoints((0..10).map { transform.forward * (it / 10.0) + transform.position }, Particle.DustOptions(Color.BLUE, 1f))
        DrawUtil.drawPoints((0..10).map { transform.right * (it / 10.0) + transform.position }, Particle.DustOptions(Color.RED, 1f))
        DrawUtil.drawPoints((0..10).map { transform.up * (it / 10.0) + transform.position }, Particle.DustOptions(Color.GREEN, 1f))

        driverSeat.passenger?.let { driver ->
//            val (yaw, pitch) = driver.eyeLocation.direction.toYawPitch()
//            var roll = transform.eulerRotation.eulerRoll
            var yaw = -transform.eulerRotation.eulerYaw
            val player = driver.vehiclePlayer
            if(player.isLeft) {
//                roll -= 0.05f
                yaw -= 0.05f
            }else if(player.isRight) {
//                roll += 0.05f
                yaw += 0.05f
            }

            transform.eulerRotation = Vector(0f, -yaw, 0f)
        }

        modelEntities.forEach { (info, entity) ->
            val location = transform.transform(info, world)
            entity.teleport(location)
            if(info.item.type != Material.AIR) {
                entity.setPose(info.type, transform.eulerAngleForPose)
            }
            if(!info.hitbox.isEmpty()) {
                DrawUtil.drawPoints(entity.boundingBox.getOutline(4), aabbParticle)
            }
        }
        seatEntities.forEach { (info, entity) ->
            val location = transform.transform(info, world)
            entity.teleport(location)
            if(info.item.type != Material.AIR) {
                entity.setPose(info.type, transform.eulerAngleForPose)
            }
        }

    }

    private fun ArmorStand.setPose(type: EquipmentSlot, rotation: EulerAngle) {
        when(type) {
            EquipmentSlot.HAND -> rightArmPose = rotation
            EquipmentSlot.OFF_HAND -> leftArmPose = rotation
            EquipmentSlot.HEAD -> headPose = rotation
            else -> return
        }
    }

    fun death() {
        if(isDead) return

        isDead = true

        modelEntities.values.forEach { it.remove() }
        seatEntities.forEach { it.remove() }

        modelEntities.clear()
        seatEntities.clear()
    }

    fun ride(player: Player, forced: Boolean = false): Int {
        if(!forced) {
            if(decoration || isDead || player.gameMode == GameMode.SPECTATOR) return -1
        }
        for((index, seatEntity) in seatEntities.withIndex()) {
            if(seatEntity.ride(player)) {
                return index
            }
        }
        return -1
    }

    fun exit(riding: ArmorStand, player: Player, forced: Boolean = false): Boolean {
        val exitEvent = VehiclePreExitEvent(this, player, riding)
        exitEvent.callEvent()
        if(!forced && !isDead && exitEvent.isCancelled) {
            return false
        }
        for((index, entity) in seatEntities.withIndex()) {
            if(entity.entityId != riding.entityId) {
                continue
            }
            return entity.exit(player, forced)
        }
        return true
    }

    companion object : Listener {
        val byUUID = HashMap<UUID, VehicleEntity>()

        val Entity.vehicleEntityIdOrNull: UUID?
            get() = getMetadata(Constants.vehicleEntityKey).firstOrNull()?.value() as? UUID
        val Entity.asVehicleEntityOrNull: VehicleEntity?
            get() {
                val id = vehicleEntityIdOrNull ?: return null
                return byUUID[id]
            }

        @EventHandler
        fun ServerTickEndEvent.onTick() {
            byUUID.values.forEach { it.tick() }
            byUUID.entries.removeIf { it.value.isDead }
        }

        @EventHandler
        fun PlayerInteractEvent.onInteract() {
            if(action != Action.LEFT_CLICK_AIR) return
            if(hand != EquipmentSlot.HAND) return

            if(GunEngine.isEnable && player.weaponPlayer.weapon != null) return

            val origin = player.eyeLocation
            val forward = origin.clone().add(origin.direction.multiply(1.5))
            val entities = forward.getNearbyEntitiesByType(ArmorStand::class.java, 1.5)
            val vehicles = entities
                .mapNotNull {
                    val uniqueId = it.vehicleEntityIdOrNull ?: return@mapNotNull null
                    it!! to uniqueId
                }
                .groupBy { it.second }
                .mapValues { it.value.minByOrNull { (entity, id) -> origin.distanceSquared(entity.location) }!! }

            for((id, _) in vehicles) {
                val vehicle = byUUID[id] ?: continue
                if(vehicle.ride(player) >= 0) return
            }
        }

        @EventHandler
        fun EntityDismountEvent.onDismount() {
            // entity == 내린 사람
            // dismounted == vehicle
            if(entity.type != EntityType.PLAYER) return
            if(dismounted.type != EntityType.ARMOR_STAND) return
            val player = entity as Player
            val vehiclePlayer = player.vehiclePlayer
            val riding = dismounted as ArmorStand
            val vehicle = riding.asVehicleEntityOrNull ?: return
            if(vehiclePlayer.isReseating) return
            player.fallDistance = 0f
            if(player.isDead) return
            if(!vehicle.exit(riding, player, !isCancellable)) {
                isCancelled = true
            }
        }
    }

    override fun hashCode(): Int {
        return uniqueId.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if(other is VehicleEntity) {
            return other.uniqueId == uniqueId
        }
        return super.equals(other)
    }

}