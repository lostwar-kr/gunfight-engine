package kr.lostwar.vehicle.core

import com.destroystokyo.paper.event.server.ServerTickEndEvent
import com.destroystokyo.paper.event.server.ServerTickStartEvent
import kr.lostwar.GunfightEngine.Companion.plugin
import kr.lostwar.gun.GunEngine
import kr.lostwar.gun.weapon.Weapon
import kr.lostwar.gun.weapon.WeaponPlayer.Companion.weaponPlayer
import kr.lostwar.util.DrawUtil
import kr.lostwar.util.math.VectorUtil.plus
import kr.lostwar.util.math.VectorUtil.times
import kr.lostwar.util.math.VectorUtil.toYawPitch
import kr.lostwar.util.nms.NMSUtil.setDiscardFriction
import kr.lostwar.util.nms.NMSUtil.setMaxUpStep
import kr.lostwar.vehicle.VehicleEngine
import kr.lostwar.vehicle.VehicleEngine.isDebugging
import kr.lostwar.vehicle.VehiclePlayer.Companion.vehiclePlayer
import kr.lostwar.vehicle.event.VehicleEntityDamageEvent
import kr.lostwar.vehicle.event.VehiclePreExitEvent
import kr.lostwar.vehicle.util.ExtraUtil.getOutline
import org.bukkit.*
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageEvent.DamageCause
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.util.EulerAngle
import org.bukkit.util.Vector
import org.spigotmc.event.entity.EntityDismountEvent
import java.util.*

open class VehicleEntity<T : VehicleInfo>(
    base: T,
    val spawnLocation: Location,
    val decoration: Boolean = false,
) {
    val world = spawnLocation.world
    private var internalPrimaryEntity: ArmorStand? = null
        set(entity) {
            field = entity
            if(entity != null) {
                entity.isInvulnerable = false
                entity.maxHealth = base.health
                entity.health = base.health
            }
        }
    val primaryEntity: ArmorStand; get() = internalPrimaryEntity!!
    val uniqueId: UUID; get() = internalPrimaryEntity!!.uniqueId

    var base: T = base
        set(newBase) {
            field = newBase
            onReload(newBase)
        }
    fun setBaseForced(info: VehicleInfo) {
        base = info as T
    }

    protected open fun onReload(newBase: T) {
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
        kinematicEntities.clear()
        kinematicEntities.putAll(modelEntities.filter { it.key.isKinematicEntity }.onEach { (info, entity) ->
            entity.setMaxUpStep(base.upStep)
        })
        kinematicEntitiesSortedByZ.clear()
        kinematicEntitiesSortedByZ.addAll(kinematicEntities.entries.sortedByDescending { it.key.localPosition.z })
        nonKinematicEntities.clear()
        nonKinematicEntities.putAll(modelEntities.filter { !it.key.isKinematicEntity })
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
        val position = spawnLocation.toVector()
        val (yaw, pitch) = spawnLocation.direction.toYawPitch()
        VehicleTransform(position).apply { eulerRotation = Vector(0f, yaw, 0f) }
    }
    val location: Location; get() = transform.position.toLocation(world)

    var velocity: Vector = Vector()

    val modelEntities = base.models.mapKeys { it.value }.mapValues { spawnModel(it.value) }.toMutableMap()
    val kinematicEntities = modelEntities.filter { it.key.isKinematicEntity }.toMutableMap()
        .onEach { (info, entity) ->
            entity.setMaxUpStep(base.upStep)
            entity.setDiscardFriction(true)
        }
    val kinematicEntitiesSortedByZ = kinematicEntities.entries.sortedByDescending { it.key.localPosition.z }.toMutableList()
    val nonKinematicEntities = modelEntities.filter { !it.key.isKinematicEntity }.toMutableMap()

    val seatEntities = base.seats.mapIndexed { index, it -> SeatEntity(index, it, spawnModel(it), this) }.toMutableList()
    val driverSeat = seatEntities[0]

    protected fun spawnModel(info: VehicleModelInfo): ArmorStand {
        val worldPosition = transform.transform(info, world)
        return (world.spawnEntity(worldPosition, EntityType.ARMOR_STAND) as ArmorStand).apply {
            isSmall = info.isSmall

            isInvisible = true
            isInvulnerable = true
            setGravity(false)
            if(!info.isKinematicEntity) {
                isMarker = true
            }
            info.hitbox.apply(this)
            if(info.type == EquipmentSlot.HAND || info.type == EquipmentSlot.OFF_HAND) {
                setArms(true)
                setPose(EquipmentSlot.HAND, transform.eulerAngleForPose)
                setPose(EquipmentSlot.OFF_HAND, transform.eulerAngleForPose)
            }
            equipment.setItem(info.type, info.item.toItemStack(), true)
            addEquipmentLock(info.type, ArmorStand.LockType.REMOVING_OR_CHANGING)
            setMetadata(Constants.vehicleEntityKey, FixedMetadataValue(plugin, internalPrimaryEntity?.uniqueId ?: uniqueId.also {
                internalPrimaryEntity = this
                byUUID[it] = this@VehicleEntity
            }))

            if(decoration) {
                setGravity(false)
            }
        }
    }

    private val aabbParticle = Particle.DustOptions(Color.WHITE, 0.5f)
    var isDead = false
    private fun earlyTick() {
        if(decoration) return
        if(isDead) return
        onEarlyTick()
    }
    protected open fun onEarlyTick() {}
    private fun tick() {
        if(decoration) return
        if(!isDead && (modelEntities.values.any { it.isDead } || seatEntities.any { it.isDead })) {
            death()
            return
        }
        if(isDead) return
        onTick()
        updateChildEntities()
        onLateTick()
        processDamage()
    }
    protected open fun onTick() {
        if(isDebugging) {
            // 로컬 축 그리기
            DrawUtil.drawPoints((0..10).map { transform.forward * (it / 10.0) + transform.position }, Particle.DustOptions(Color.BLUE   , 0.5f))
            DrawUtil.drawPoints((0..10).map { transform.right * (it / 10.0) + transform.position }  , Particle.DustOptions(Color.RED    , 0.5f))
            DrawUtil.drawPoints((0..10).map { transform.up * (it / 10.0) + transform.position }     , Particle.DustOptions(Color.GREEN  , 0.5f))
        }

        /*
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
        */
    }
    protected open fun updateChildEntities() {
        nonKinematicEntities.forEach { (info, entity) ->
            val location = transform.transform(info, world)
            entity.teleport(location)
            if(info.item.type != Material.AIR) {
                entity.setPose(info.type, transform.eulerAngleForPose)
            }
            if(isDebugging && !info.hitbox.isEmpty()) {
                DrawUtil.drawPoints(entity.boundingBox.getOutline(4), aabbParticle)
            }
        }
        if(isDebugging) kinematicEntities.forEach { (info, entity) ->
            DrawUtil.drawPoints(entity.boundingBox.getOutline(4), aabbParticle)
        }
        seatEntities.forEach { (info, entity) ->
            val location = transform.transform(info, world)
            entity.teleport(location)
            if(info.item.type != Material.AIR) {
                entity.setPose(info.type, transform.eulerAngleForPose)
            }
        }
    }
    protected open fun onLateTick() {}

    private fun ArmorStand.setPose(type: EquipmentSlot, rotation: EulerAngle) {
        when(type) {
            EquipmentSlot.HAND -> rightArmPose = rotation
            EquipmentSlot.OFF_HAND -> leftArmPose = rotation
            EquipmentSlot.HEAD -> headPose = rotation
            else -> return
        }
    }

    private val damageList = mutableListOf<VehicleEntityDamage>()
    fun damage(amount: Double, cause: DamageCause, victim: ArmorStand? = null, damager: Entity? = null, weapon: Weapon? = null) {
        if(decoration) return
        if(isDead) return

        when(cause) {
            DamageCause.FALL, DamageCause.SUFFOCATION -> return
            DamageCause.ENTITY_ATTACK, DamageCause.ENTITY_SWEEP_ATTACK, DamageCause.ENTITY_EXPLOSION -> if(damager == null) return
        }

        damageList.add(VehicleEntityDamage(this, amount, cause, victim, damager, weapon))
    }

    private fun processDamage() {
        if(decoration) return
        if(isDead) return
        val damages = damageList
            .sortedByDescending { it.amount }
            .distinctBy { it.hashCode() }

        for(damage in damages) {
            val event = VehicleEntityDamageEvent(this, damage, primaryEntity.health - damage.amount <= 0)
            if(event.callEvent()){
                primaryEntity.health -= event.damageInfo.amount
                if(primaryEntity.health <= 0) {
                    death()
                    break
                }
            }
        }

        damageList.clear()
    }

    fun death() {
        if(isDead) return

        isDead = true

        for(seat in seatEntities) {
            val player = seat.passenger
            if(player != null)
                seat.exit(player, true)
        }

        modelEntities.values.forEach { it.remove() }
        seatEntities.forEach { it.remove() }

        modelEntities.clear()
        seatEntities.clear()
        damageList.clear()
    }

    fun ride(player: Player, forced: Boolean = false): Int {
        if(!forced) {
            if(decoration || isDead || player.gameMode == GameMode.SPECTATOR) return -1
        }
        val oldRiding = player.vehicle
        // 같은 차량 내에서, 좌석 변경 시도
        oldRiding?.asVehicleEntityOrNull?.let { oldVehicle ->
            if(oldVehicle != this) return@let
            val seatEntity = seatEntities.find { it.entityId == oldRiding.entityId } ?: return@let
            val seatCount = seatEntities.size
            // 같은 차량에서는, 빈 다음 좌석으로 탈 수 있도록 함
            var nextSeatIndex = (seatEntity.index + 1) % seatCount
            while(nextSeatIndex != seatEntity.index) {
                if(seatEntities[nextSeatIndex].ride(player)) {
                    return nextSeatIndex
                }
                nextSeatIndex = (seatEntity.index + 1) % seatCount
            }
            return -1
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
        val byUUID = HashMap<UUID, VehicleEntity<out VehicleInfo>>()

        val Entity.vehicleEntityIdOrNull: UUID?
            get() = getMetadata(Constants.vehicleEntityKey).firstOrNull()?.value() as? UUID
        val Entity.asVehicleEntityOrNull: VehicleEntity<*>?
            get() {
                val id = vehicleEntityIdOrNull ?: return null
                return byUUID[id]
            }

        @EventHandler
        fun ServerTickStartEvent.onTick() {
            byUUID.values.forEach { it.earlyTick() }
        }

        @EventHandler(priority = EventPriority.MONITOR)
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
        if(other is VehicleEntity<*>) {
            return other.uniqueId == uniqueId
        }
        return super.equals(other)
    }

}