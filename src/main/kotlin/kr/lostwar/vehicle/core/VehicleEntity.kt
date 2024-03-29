package kr.lostwar.vehicle.core

import com.destroystokyo.paper.event.server.ServerTickEndEvent
import com.destroystokyo.paper.event.server.ServerTickStartEvent
import kr.lostwar.GunfightEngine.Companion.plugin
import kr.lostwar.gun.GunEngine
import kr.lostwar.gun.weapon.WeaponPlayer.Companion.weaponPlayer
import kr.lostwar.gun.weapon.WeaponType
import kr.lostwar.gun.weapon.event.WeaponHitEntityEvent
import kr.lostwar.gun.weapon.event.WeaponShootPrepareEvent
import kr.lostwar.netcode.EntityNetcodeFixer
import kr.lostwar.netcode.EntityNetcodeFixer.Companion.forceBindNetcodeFixer
import kr.lostwar.netcode.EntityNetcodeFixer.Companion.unbindNetcodeFixer
import kr.lostwar.util.DrawUtil
import kr.lostwar.util.ExtraUtil.armorStandOffset
import kr.lostwar.util.math.VectorUtil.ZERO
import kr.lostwar.util.math.VectorUtil.minus
import kr.lostwar.util.math.VectorUtil.plus
import kr.lostwar.util.math.VectorUtil.set
import kr.lostwar.util.math.VectorUtil.times
import kr.lostwar.util.math.clamp
import kr.lostwar.util.nms.NMSUtil.damage
import kr.lostwar.util.nms.NMSUtil.setDiscardFriction
import kr.lostwar.util.nms.NMSUtil.setHardCollides
import kr.lostwar.util.nms.NMSUtil.setImpulse
import kr.lostwar.util.nms.NMSUtil.setMaxUpStep
import kr.lostwar.util.nms.NMSUtil.updatePassengersPosition
import kr.lostwar.util.nms.UnsafeNMSUtil.setCollidePredicate
import kr.lostwar.vehicle.VehicleEngine
import kr.lostwar.vehicle.VehicleEngine.isDebugging
import kr.lostwar.vehicle.VehiclePlayer.Companion.vehiclePlayer
import kr.lostwar.vehicle.core.SavedVehicleEntity.Companion.save
import kr.lostwar.vehicle.core.parachute.ParachuteInfo
import kr.lostwar.vehicle.event.*
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
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDamageEvent.DamageCause
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerGameModeChangeEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.event.world.EntitiesLoadEvent
import org.bukkit.event.world.EntitiesUnloadEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.util.EulerAngle
import org.bukkit.util.Vector
import org.spigotmc.event.entity.EntityDismountEvent
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet

open class VehicleEntity<T : VehicleInfo>(
    base: T,
    val spawnLocation: Location,
    val decoration: Boolean = false,
) {
    init {
        // 오프셋이 음수로 설정된 히트박스 기반으로 스폰 위치 결정
        val kinematicBaseY = base.models.values
            .filter { it.isKinematicEntity }
            .minOf { it.localPosition.y }
        spawnLocation.add(0.0, -kinematicBaseY, 0.0)
        spawnLocation.pitch = 0f
    }
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
    var health: Double; get() = primaryEntity.health; set(value) { primaryEntity.health = value }
    val maxHealth = base.health
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
        modelEntities.forEach { (key, entity) ->
            entity.info = newBase.models[key] ?: entity.info
            entity.info.hitbox.apply(entity.entity)
        }
        kinematicEntities.clear()
        kinematicEntities.putAll(modelEntities.filter { it.value.info.isKinematicEntity }.onEach { (info, entity) ->
            entity.entity.setMaxUpStep(base.upStep)
        })
        kinematicEntitiesSortedByZ.clear()
        kinematicEntitiesSortedByZ.addAll(kinematicEntities.entries.sortedByDescending { it.value.info.localPosition.z })
        nonKinematicEntities.clear()
        nonKinematicEntities.putAll(modelEntities.filter { !it.value.info.isKinematicEntity })
        if(seatEntities.size != newBase.seats.size) {
            VehicleEngine.logWarn("unsafe hot reload at ${base.key}: inconsistency seat count")
        }
        seatEntities.forEachIndexed { index, entity ->
            if(newBase.seats.size <= index) return@forEachIndexed
            entity.apply {
                info = newBase.seats[index]
                info.hitbox.apply(this.entity)
            }
        }
    }

    val transform: VehicleTransform = run {
        val position = spawnLocation.toVector()
        val direction = spawnLocation.direction
        VehicleTransform(position).apply {
            forward = direction
        }
    }
    val location: Location; get() = transform.position.toLocation(world)

    var velocity: Vector = Vector()

    protected val modelEntitiesIdSet = TreeSet<Int>()
    operator fun contains(entity: Entity?): Boolean {
        if(entity == null) return false
        return modelEntitiesIdSet.contains(entity.entityId)
    }
    val modelEntities = base.models
        .mapValues { ModelEntity(it.value, spawnModel(it.value), this) }
        .toMutableMap()
        .also { map ->
            map.forEach { (key, entity) ->
                entity.info.parent?.let { parentInfo -> entity.parent = map[parentInfo.key] } ?: run {
                    entity.updateWorld()
                }
            }
        }
    val kinematicEntities = modelEntities.filter { it.value.info.isKinematicEntity }.toMutableMap()
        .onEach { (info, entity) ->
            entity.entity.setMaxUpStep(base.upStep)
            entity.entity.setDiscardFriction(true)
        }
    val kinematicEntitiesSortedByZ = kinematicEntities.entries.sortedByDescending { it.value.info.localPosition.z }.toMutableList()
    val nonKinematicEntities = modelEntities.filter { !it.value.info.isKinematicEntity }.toMutableMap()

    val seatEntities = base.seats
        .mapIndexed { index, it -> SeatEntity(index, it, spawnModel(it), this) }
        .toMutableList()
        .also { list ->
            list.forEach { entity ->
                entity.info.parent?.let { parentInfo -> entity.parent = modelEntities[parentInfo.key] } ?: run {
                    entity.updateWorld()
                }
            }
        }
    val driverSeat = seatEntities[0]
    var lastDriver: Player? = null; private set
    fun isRiding(entity: Entity?): Boolean {
        if(entity == null) return false
        return seatEntities.any { it.passenger?.entityId == entity.entityId }
    }
    fun isEmpty(): Boolean {
        return seatEntities.all { it.passenger == null }
    }

    protected open fun spawnModel(info: VehicleModelInfo): ArmorStand {
        val worldPosition = transform.transform(info, world)
//        console("spawnModel(${info.key}) on ${worldPosition.toLocationString()}")
        return (world.spawnEntity(worldPosition, EntityType.ARMOR_STAND) as ArmorStand).apply {
            modelEntitiesIdSet.add(entityId)
            isPersistent = false
            isSmall = info.isSmall

            isInvisible = !isDebugging
            setGravity(false)
            if(!info.isKinematicEntity) {
                isMarker = !info.noMarker
                isInvulnerable = true
            }else{
//                console("info ${info.key} is kinematic")
                maxHealth = base.health
                health = base.health
                setHardCollides(true)
                setCollidePredicate { _, other ->
                    // 같은 차량 엔티티끼리는 상관 없음
                    if(other.entityId in modelEntitiesIdSet)
                        return@setCollidePredicate false
                    // 같은 차량은 아닌데 아무튼 차량 엔티티일 경우?
                    // 개같이 꼴아박기
                    if(other.vehicleEntityIdOrNull != null) {
                        return@setCollidePredicate true
                    }
                    // 그 외의 경우에는 엔티티를 통한 hard collision은 비활성화
                    false
                }
            }
//            console("info ${info.key} hardCollides: ${isHardCollides()}")
//            console("info ${info.key} hitbox size: ${info.hitbox}")
            info.hitbox.apply(this)
            if(info.type == EquipmentSlot.HAND || info.type == EquipmentSlot.OFF_HAND) {
                setArms(true)
                setPose(EquipmentSlot.HAND, transform.eulerAngleForPose)
                setPose(EquipmentSlot.OFF_HAND, transform.eulerAngleForPose)
            }
            equipment.setItem(info.type, info.item.toItemStack(), true)
            for(slot in EquipmentSlot.values()) {
                for(lockType in ArmorStand.LockType.values()) {
                    addEquipmentLock(info.type, lockType)
                }
            }
            setMetadata(Constants.vehicleEntityKey, FixedMetadataValue(plugin, internalPrimaryEntity?.uniqueId ?: uniqueId.also {
                internalPrimaryEntity = this
            }))

            if(decoration) {
                setGravity(false)
            }
        }
    }
    var livingTicks = 0
    var removeWhenAbandoned = base.abandonedRemoveTime > 0; private set
    var abandonedRemoveTimeInTicks = base.abandonedRemoveTime
        set(value) {
            field = value
            removeWhenAbandoned = value > 0
            unusedTicks = 0
        }
    var unusedTicks = 0; private set

    val neverUsed; get() = livingTicks == unusedTicks
    init {
        byUUID[uniqueId] = this
    }

    private val aabbParticle = Particle.DustOptions(Color.WHITE, 0.5f)
    var isDead = false
    protected fun isOnChunkLoaded(): Boolean {
        val x = transform.position.x.toInt() shr 4
        val z = transform.position.z.toInt() shr 4
        return world.isChunkLoaded(x, z)
    }
    private fun earlyTick() {
        if(!isOnChunkLoaded()) {
            return
        }
        if(decoration) return
        if(isDead) return
        val driver = driverSeat.passenger
        if(driver != null && driver != lastDriver) {
            lastDriver = driver
        }
        onEarlyTick()
        updateChildEntities()
    }
    protected open fun onEarlyTick() {}
    private val lastPosition = transform.position.clone()
    var onMoveBroadcast: (Entity) -> Unit = {}
    val netcodeFixer = EntityNetcodeFixer(VehicleEntityNetcodeAdapter(this))
        .also {
            modelEntities.forEach { (_, entity) -> entity.forceBindNetcodeFixer(it) }
            seatEntities.forEach { (_, entity) -> entity.forceBindNetcodeFixer(it) }
        }
    private fun tick() {
        if(!isOnChunkLoaded()) {
            return
        }
        if(decoration) return
        if(!isDead && (modelEntities.values.any { it.isDead } || seatEntities.any { it.isDead })) {
            death()
            return
        }
        if(isDead) return
        onTick()
        onLateTick()
        if(livingTicks % 60 == 0 || transform.position.distanceSquared(lastPosition) > 0.0) {
            lastPosition.set(transform.position)
            onMoveBroadcast(primaryEntity)
        }
        processDamage()

        if(removeWhenAbandoned && !storeOnUnload) {
            if(isEmpty()) {
                if(!neverUsed) {
                    if(unusedTicks > abandonedRemoveTimeInTicks) {
                        death(explosion = false)
                        return
                    }
                }
                ++unusedTicks
            }else{
                unusedTicks = 0
            }
        }

        ++livingTicks

    }
    protected open fun onTick() {
        if(isDebugging) {
            // 로컬 축 그리기
            DrawUtil.drawPoints(DrawUtil.getRay(transform.position, transform.forward * 5, 10)  ,Particle.DustOptions(Color.BLUE   , 0.5f))
            DrawUtil.drawPoints(DrawUtil.getRay(transform.position, transform.right * 5, 10)    ,Particle.DustOptions(Color.RED    , 0.5f))
            DrawUtil.drawPoints(DrawUtil.getRay(transform.position, transform.up * 5, 10)       ,Particle.DustOptions(Color.GREEN  , 0.5f))
//            DrawUtil.drawPoints((0..40).map { transform.forward * (it / 10.0) + transform.position }, Particle.DustOptions(Color.BLUE   , 0.5f))
//            DrawUtil.drawPoints((0..40).map { transform.right * (it / 10.0) + transform.position }  , Particle.DustOptions(Color.RED    , 0.5f))
//            DrawUtil.drawPoints((0..40).map { transform.up * (it / 10.0) + transform.position }     , Particle.DustOptions(Color.GREEN  , 0.5f))
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
        nonKinematicEntities.forEach { (key, entity) ->
            entity.entity.setImpulse()
            entity.tick()
            val transform = entity.worldTransform
            val location = entity.worldLocation
            entity.teleport(location)
            if(entity.info.item.type != Material.AIR) {
                entity.setPose(entity.info.type, transform.eulerAngleForPose)
            }
            if(isDebugging) {
                val localPosition = entity.info.localPosition
                DrawUtil.drawPoints(DrawUtil.getRay(transform.position + transform.right * localPosition.x, transform.forward * localPosition.z, 10)  ,Particle.DustOptions(Color.BLUE   , 0.5f))
                DrawUtil.drawPoints(DrawUtil.getRay(transform.position + transform.forward * localPosition.z, transform.right * localPosition.x, 10)    ,Particle.DustOptions(Color.RED    , 0.5f))
                DrawUtil.drawPoints(DrawUtil.getRay(transform.position + transform.right * localPosition.x + transform.forward * localPosition.z, transform.up * localPosition.y, 10)       ,Particle.DustOptions(Color.GREEN  , 0.5f))
                if(!entity.info.hitbox.isEmpty()) {
                    DrawUtil.drawPoints(entity.boundingBox.getOutline(4), aabbParticle)
                }
            }
        }
        if(isDebugging) kinematicEntities.forEach { (info, entity) ->
            DrawUtil.drawPoints(entity.boundingBox.getOutline(4), aabbParticle)
        }
        seatEntities.forEach { entity ->
            entity.entity.setImpulse()
            entity.tick()
            val transform = entity.worldTransform
            val info = entity.info
            val location = entity.worldLocation
            // 탑승자 있는 상태에서 teleport 하므로 ignorePassengers
            entity.teleport(location, true)
            if(info.item.type != Material.AIR) {
                entity.setPose(info.type, transform.eulerAngleForPose)
            }
            if(isDebugging) {
                val localPosition = info.localPosition - if(info.hitbox.isEmpty()) info.type.armorStandOffset else ZERO
                DrawUtil.drawPoints(DrawUtil.getRay(transform.position, transform.forward * localPosition.z, 10)  ,Particle.DustOptions(Color.BLUE   , 0.5f))
                DrawUtil.drawPoints(DrawUtil.getRay(transform.position + transform.forward * localPosition.z, transform.right * localPosition.x, 10)    ,Particle.DustOptions(Color.RED    , 0.5f))
                DrawUtil.drawPoints(DrawUtil.getRay(transform.position + transform.right * localPosition.x + transform.forward * localPosition.z, transform.up * localPosition.y, 10)       ,Particle.DustOptions(Color.GREEN  , 0.5f))
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
    fun damage(amount: Double, cause: DamageCause, victim: ArmorStand? = null, damager: Entity? = null, weapon: WeaponType? = null) {
        if(decoration) return
        if(isDead) return
        if(base.invincible) return

        when(cause) {
            DamageCause.FALL, DamageCause.SUFFOCATION -> return
            DamageCause.ENTITY_ATTACK, DamageCause.ENTITY_SWEEP_ATTACK, DamageCause.ENTITY_EXPLOSION -> if(damager == null) return
        }

        // 차량을 근접에서 때리려 한 경우
        if(damager != null
            && damager.type == EntityType.PLAYER
            && weapon == null
            && (cause == DamageCause.ENTITY_ATTACK || cause == DamageCause.ENTITY_SWEEP_ATTACK)
        ) {
            // 탑승에 성공하면 데미지 처리로 치지 않음
            if(ride(damager as Player) >= 0) {
                return
            }
        }

        // 탑승하고 있는 차량에 대한 피해는 지형 충돌로 인한 피해를 제외하고 무시
        if(cause != Constants.collisionDamageCause && damager?.vehicleEntityIdOrNull == this.uniqueId) {
            return
        }

        // 탑승자가 자기 차량을 때리는 경우 무시
        if(isRiding(damager)) {
            return
        }

        val damage = VehicleEntityDamage(this, amount, cause, victim, damager, weapon)
//        VehicleEngine.log("damage registered: $damage")
        damageList.add(damage)
    }

    private fun processDamage() {
        if(decoration) return
        if(isDead) return
        val damages = damageList
            .sortedByDescending { it.amount }
            .distinctBy { it.hashCode() }

        if(damages.isNotEmpty()) {
            base.hitSound.playAt(location)
//            VehicleEngine.log("damage process:")
        }
        for(damage in damages) {
//            VehicleEngine.log("- $damage : ${primaryEntity.health - damage.amount}/${primaryEntity.maxHealth}")
            val event = VehicleEntityDamageEvent(this, damage, primaryEntity.health - damage.amount <= 0)
            if(event.callEvent()){
                val newHealth = health - event.damageInfo.amount
                if(newHealth <= 0) {
//                    VehicleEngine.log("! death")
                    death(event)
                    break
                }
                health = newHealth.clamp(0.0  .. maxHealth)
            }
        }


        damageList.clear()
    }

    fun death(damageEvent: VehicleEntityDamageEvent? = null, explosion: Boolean = true) {
        if(isDead) return
        isDead = true

        val lastPassengers = seatEntities.mapNotNull { it.passenger }
        // 탑승자 전부 내보내기
        for(seat in seatEntities) {
            seat.exit()
        }

        val location = location
        if(explosion && base.deathExplosionEnable && damageEvent?.damageInfo?.damager?.uniqueId != uniqueId) {
//            console("explosion:")
            for(entity in location.getNearbyLivingEntities(base.deathExplosionRadius)) {
                if(entity.type == EntityType.ARMOR_STAND && entity.vehicleEntityIdOrNull == uniqueId) {
//                    console("- ${entity} is self car, ignored")
                    continue
                }
                val damage: Double
                val wasPassenger = lastPassengers.contains(entity)
                // 탑승자가 아닌 경우
                if(!wasPassenger) {
                    // 몸통 중앙
                    val entityLocation = entity.location.add(0.0, entity.height / 2.0, 0.0)
                    val distance = location.distance(entityLocation)
                    // ray 쐈는데 블록에 가로막힌 경우 폭발 영향 X
                    val hitBlock = world.rayTraceBlocks(
                        location,
                        entityLocation.subtract(location).toVector().normalize(),
                        distance,
                        FluidCollisionMode.NEVER,
                        true
                    )?.takeIf { it.hitBlock != null }
                    if(hitBlock != null){
//                        console("- ${entity} was not passenger, but block hit(${hitBlock}), cancelled")
                        continue
                    }
                    damage = base.deathExplosionDamage + (distance * -base.deathExplosionDamageDecreasePerDistance)
//                    console("- ${entity} was not passenger, damage: ${damage}")
                }
                // 탑승자의 경우
                else{
                    damage = base.deathExplosionDamage * base.deathExplosionPassengerDamageMultiply
//                    console("- ${entity} was passenger, damage: ${damage}")
                }
                val event = VehicleExplodeDamageEvent(this, entity, damage, wasPassenger, damageEvent)
                if(event.callEvent()) {
//                    console("  * event not cancelled, applied damage ${event.damage}")
                    entity.damage(event.damage, Constants.vehicleExplosionDamageCause)
                }
            }

            if(base.deathExplosionTryStretchParachute) {
                for(lastPassenger in lastPassengers) {
                    if(lastPassenger.isDead) continue
                    ParachuteInfo.tryStretchParachute(lastPassenger)
                }
            }

            base.deathExplosionSound.playAt(location)
            base.deathExplosionEffect.executeEach { it.spawnAt(location, forced = true) }
        }

        val deathEvent = VehicleEntityDeathEvent(this, damageEvent)
        deathEvent.callEvent()

        modelEntities.values.forEach {
            it.unbindNetcodeFixer()
            it.remove()
        }
        seatEntities.forEach {
            it.unbindNetcodeFixer()
            it.remove()
        }

        modelEntities.clear()
        seatEntities.clear()
        damageList.clear()
    }

    var storeOnUnload = false
    protected open fun MutableMap<String, Any>.storeData() {}
    open fun store(): SavedVehicleEntity {
//        console("store to cache ${base.key}(${uniqueId})")
        val data = save(this) {
            storeData()
        }

        modelEntities.values.forEach { it.remove() }
        seatEntities.forEach { it.remove() }

        modelEntities.clear()
        seatEntities.clear()
        damageList.clear()
        isDead = true
        return data
    }

    open fun apply(data: SavedVehicleEntity): Boolean {
//        console("recovered from cache ${base.key}(${uniqueId})")
        return true
    }

    open fun ride(player: Player, forced: Boolean = false): Int {
        fun Int.onRide(): Int {
            if(base.disableDriverExitVehicleByShiftKey) {
                // 착석 성공했을 때 Shift 하차 아닌 경우
                player.inventory.heldItemSlot = 0
            }
            return this
        }
        val rideEvent = VehiclePreEnterEvent(this, player).also { it.callEvent() }
        if(!forced) {
            if(rideEvent.isCancelled || decoration || isDead || player.gameMode == GameMode.SPECTATOR) return -1
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
                    return nextSeatIndex.onRide()
                }
                nextSeatIndex = (nextSeatIndex + 1) % seatCount
            }
            return -1
        }
        for((index, seatEntity) in seatEntities.withIndex()) {
            if(seatEntity.ride(player)) {
                return index.onRide()
            }
        }
        return -1
    }

    open fun exit(riding: ArmorStand, player: Player, forced: Boolean = false): Boolean {
        val exitEvent = VehiclePreExitEvent(this, player, riding, forced)
        exitEvent.callEvent()
        if(!forced && !isDead && exitEvent.isCancelled) {
            return false
        }
        for((index, entity) in seatEntities.withIndex()) {
            if(entity.entityId != riding.entityId) {
                continue
            }
            return entity.exit()
        }
        return false
    }

    fun callAnimation(eventKey: String) {
        val animation = base.animations[eventKey] ?: return
//        console("calling animation ${eventKey}: (${animation.itemMap.entries.joinToString { it.key+"="+it.value }})")
        for((key, item) in animation.itemMap) {
            val model = modelEntities[key] ?: continue
            model.item = item
        }
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

        private val ignoreClick = HashSet<UUID>()
        @EventHandler
        fun PlayerDropItemEvent.onDrop() {
            ignoreClick.add(player.uniqueId)
        }
        @EventHandler
        fun PlayerInteractEvent.onInteract() {
            if(action != Action.LEFT_CLICK_AIR) return
            if(hand != EquipmentSlot.HAND) return

            if(GunEngine.isEnable){
                // fixme 좌클릭 사용하는 무기인 경우 캔슬
                // 임시 하드코딩
                player.weaponPlayer.weapon?.let { weapon ->
                    if(weapon.type.zoom != null) {
                        return
                    }
                }
            }
            if(ignoreClick.contains(player.uniqueId)){
                ignoreClick.remove(player.uniqueId)
                return
            }

            val origin = player.eyeLocation
            val direction = origin.direction
            val scanCenter = origin.clone().add(origin.direction.multiply(1.5))
            val entities = scanCenter.getNearbyEntitiesByType(ArmorStand::class.java, 3.0)
            val vehicles = entities
                .mapNotNull {
                    val uniqueId = it.vehicleEntityIdOrNull ?: return@mapNotNull null
                    it!! to uniqueId
                }
                .distinctBy { it.second }
                .sortedByDescending { (entity, id) -> direction.dot(byUUID[id]!!.location.subtract(origin).toVector().normalize()) }

            for((_, id) in vehicles) {
                val vehicle = byUUID[id] ?: continue
                if(vehicle.ride(player) >= 0){
                    isCancelled = true
                    return
                }
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
            if(vehiclePlayer.isExiting) return // 중복 하차 처리 방지
            val riding = dismounted as ArmorStand
            val vehicle = riding.asVehicleEntityOrNull ?: return
            // shift 를 조작키로 사용하는 차량의 경우
            if(isCancellable 
                && riding.entityId == vehicle.driverSeat.entityId 
                && vehicle.base.disableDriverExitVehicleByShiftKey 
                && (player.isSneaking || vehiclePlayer.isShift)
            ) {
                isCancelled = true
                return    
            }
            if(vehiclePlayer.isReseating) return
            player.fallDistance = 0f
            if(!vehicle.exit(riding, player, !isCancellable)) {
                isCancelled = true
            }
        }
        /*
        @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
        fun PlayerGameModeChangeEvent.onGameModeChange() {
            if(newGameMode != GameMode.SPECTATOR) {
                return
            }
            val player = player
            val riding = player.vehicle
            val vehicle = riding?.asVehicleEntityOrNull ?: return
            val ridingSeat = riding as ArmorStand
            vehicle.exit(ridingSeat, player, true)
        }

         */
        @EventHandler(priority = EventPriority.MONITOR) fun EntityDismountEvent.onDismountFixer() {
            if(!isCancellable) {
                return
            }
            if(isCancelled) {
                dismounted.updatePassengersPosition()
            }
        }

        @EventHandler(priority = EventPriority.LOWEST)
        fun EntityDamageEvent.onEntityDamage() {
            if(cause == kr.lostwar.gun.weapon.Constants.weaponDamageCause) return
            if(entityType != EntityType.ARMOR_STAND) return
            val entity = entity as ArmorStand
            val vehicle = entity.asVehicleEntityOrNull ?: return
            isCancelled = true
            vehicle.damage(damage, cause, entity)
        }
        @EventHandler(priority = EventPriority.LOWEST)
        fun EntityDamageByEntityEvent.onEntityDamageByEntity() {
            if(entityType != EntityType.ARMOR_STAND) return
            val entity = entity as ArmorStand
            val vehicle = entity.asVehicleEntityOrNull ?: return
            isCancelled = true
            vehicle.damage(damage, cause, entity, damager)
        }
        @EventHandler(priority = EventPriority.LOWEST)
        fun WeaponHitEntityEvent.onEntityDamageByWeapon() {
            if(victim.type != EntityType.ARMOR_STAND) return
            val entity = victim as ArmorStand
            val vehicle = entity.asVehicleEntityOrNull ?: return
            result = WeaponHitEntityEvent.DamageResult.IGNORE
            vehicle.damage(damage, DamageCause.CUSTOM, entity, player.player, weaponType)
        }

        /*
        @EventHandler
        fun PlayerItemHeldEvent.onItemHeld() {
            val player = player
            val riding = player.vehicle ?: return
            val vehicle = riding.asVehicleEntityOrNull ?: return
            if(!vehicle.base.disableDriverExitVehicleByShiftKey) return
            if(riding.entityId != vehicle.driverSeat.entityId) return

            if(previousSlot != newSlot && newSlot == 8) {
                isCancelled = true
                vehicle.exit(riding as ArmorStand, player)
            }
        }

         */

        @EventHandler
        fun PlayerSwapHandItemsEvent.onSwap() {
            val player = player
            val riding = player.vehicle ?: return
            val vehicle = riding.asVehicleEntityOrNull ?: return
            if(!vehicle.base.disableDriverExitVehicleByShiftKey) return
            if(riding.entityId != vehicle.driverSeat.entityId) return

            isCancelled = true
            vehicle.exit(riding as ArmorStand, player)
        }

        @EventHandler
        fun WeaponShootPrepareEvent.onShootPrepare() {
            val player = player.player
            val riding = player.vehicle ?: return
            val vehicle = riding.asVehicleEntityOrNull ?: return
            val seat = vehicle.seatEntities.find { it.entityId == riding.entityId } ?: return
            seat.onShoot(this)
        }
        
        // 청크 관련 로직
        private val cachedVehicleStorage = HashMap<Long, MutableList<SavedVehicleEntity>>()
        fun clearCachedVehicleStorage() {
            cachedVehicleStorage.clear()
        }
        @EventHandler
        fun EntitiesUnloadEvent.onDespawnByChunkUnload() {
            val vehicles = hashSetOf<VehicleEntity<*>>()
            for(entity in entities) {
                if(entity.type != EntityType.ARMOR_STAND) continue
                val vehicle = entity.asVehicleEntityOrNull ?: continue
                vehicles.add(vehicle)
            }
            if(vehicles.isEmpty()) return
//            console("EntitiesUnloadEvent called on next vehicles: [${chunk.x}, ${chunk.z}]")
            val list = cachedVehicleStorage.getOrPut(chunk.chunkKey) { arrayListOf() }
            for(vehicle in vehicles) {
                if(vehicle.storeOnUnload) list.add(vehicle.store())
                else vehicle.death(explosion = false)
//                console(" - ${vehicle.base.key}(${vehicle.uniqueId}) at ${vehicle.location.toLocationString()}")
            }
        }

        @EventHandler
        fun EntitiesLoadEvent.onChunkEntitiesLoad() {
            val key = chunk.chunkKey
            val list = cachedVehicleStorage[key] ?: return

            for(data in list) {
                data.spawn()
            }

            list.clear()
            cachedVehicleStorage.remove(key)
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

    override fun toString(): String {
        return "${base.key}:${uniqueId}"
    }

}