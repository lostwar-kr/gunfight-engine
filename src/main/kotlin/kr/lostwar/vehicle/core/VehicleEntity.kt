package kr.lostwar.vehicle.core

import com.destroystokyo.paper.event.server.ServerTickEndEvent
import kr.lostwar.GunfightEngine.Companion.plugin
import kr.lostwar.gun.GunEngine
import kr.lostwar.gun.weapon.WeaponPlayer.Companion.weaponPlayer
import kr.lostwar.util.math.VectorUtil.toYawPitch
import kr.lostwar.vehicle.VehiclePlayer.Companion.vehiclePlayer
import kr.lostwar.vehicle.core.VehicleTransform.Companion.eulerRoll
import org.bukkit.GameMode
import org.bukkit.Location
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
import org.bukkit.util.Vector
import java.util.*
import kotlin.collections.HashMap

open class VehicleEntity(
    base: VehicleInfo,
    val location: Location,
    val decoration: Boolean = false,
) {
    val world = location.world
    private var internalUUID: UUID? = null
    val uniqueId: UUID; get() = internalUUID!!

    open var base: VehicleInfo = base
        set(value) {
            field = value
            // TODO
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
            isVisible = false
            if(!info.hitbox.isEmpty()) {
                info.hitbox.apply(this)
                isMarker = false
            }else{
                isMarker = true
            }
            setMetadata(Constants.vehicleEntityKey, FixedMetadataValue(plugin, internalUUID ?: uniqueId.also {
                internalUUID = it
                byUUID[it] = this@VehicleEntity
            }))

            if(decoration) {
                setGravity(false)
            }
        }
    }

    var isDead = false
    protected open fun tick() {
        if(decoration) return
        if(!isDead && (modelEntities.values.any { it.isDead } || seatEntities.any { it.isDead })) {
            death()
            return
        }
        if(isDead) return

        driverSeat.passenger?.let { driver ->
            val (yaw, pitch) = driver.eyeLocation.direction.toYawPitch()
            var roll = transform.eulerRotation.eulerRoll

            val player = driver.vehiclePlayer
            if(player.isLeft) {
                roll -= 1f
            }else if(player.isRight) {
                roll += 1f
            }

            transform.eulerRotation = Vector(pitch, yaw, roll)
        }

        modelEntities.forEach { (info, entity) ->
            entity.teleport(transform.transform(info, world))
        }
        seatEntities.forEach { (info, entity) ->
            entity.teleport(transform.transform(info, world))
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