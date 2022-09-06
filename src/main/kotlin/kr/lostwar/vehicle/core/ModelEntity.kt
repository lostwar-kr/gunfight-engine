package kr.lostwar.vehicle.core

import kr.lostwar.gun.weapon.WeaponPlayer.Companion.weaponPlayer
import kr.lostwar.gun.weapon.event.WeaponShootPrepareEvent
import kr.lostwar.util.DrawUtil
import kr.lostwar.util.ExtraUtil.armorStandOffset
import kr.lostwar.util.item.ItemData
import kr.lostwar.util.math.VectorUtil
import kr.lostwar.util.math.VectorUtil.lerp
import kr.lostwar.util.math.VectorUtil.localToWorld
import kr.lostwar.util.math.VectorUtil.minus
import kr.lostwar.util.math.VectorUtil.unaryMinus
import kr.lostwar.util.ui.text.consoleWarn
import kr.lostwar.vehicle.core.VehicleEntity.Companion.asVehicleEntityOrNull
import org.bukkit.Color
import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.EntityType
import org.bukkit.util.Vector

open class ModelEntity(
    info: VehicleModelInfo,
    val entity: ArmorStand,
    val vehicle: VehicleEntity<*>,
) : ArmorStand by entity {
    open var info: VehicleModelInfo = info
        set(new) {
            val old = field
            field = new

            item = info.item
            if(old.type != new.type) {
                entity.equipment.apply {
                    setItem(old.type, null, true)
                    setItem(new.type, item.toItemStack(), true)
                }
            }
            localTransform.position = info.localPosition
            offset = if(info.hitbox.isEmpty()) info.type.armorStandOffset else VectorUtil.ZERO
        }
    var item: ItemData = info.item
        set(new) {
            val old = field
//            console("modelEntity ${info.key} changed item from ${field} to ${value}")
            field = new
            if(old != new) {
                entity.equipment.setItem(info.type, new.toItemStack(), true)
            }
        }
    var parent: ModelEntity? = null
        set(value) {
            field?.children?.remove(this)
            field = value?.also { it.children.add(this) }
            updateWorld()
        }
    // 월드 회전이 터렛에 의해 별도로 조종되는 경우
    // updateWorld 시 local transform에 의한 world rotation을 사용하지 않음
    val useOwnWorldRotation; get() = info.turretInfo != null
    private val children = arrayListOf<ModelEntity>()

    private var offset = if(info.hitbox.isEmpty()) info.type.armorStandOffset else VectorUtil.ZERO
    // 차량 transform이므로, localTransform의 위치를 변경할 일은 없음
    // 대부분의 사용법은 localRotation을 변경함
    private val localTransform = VehicleTransform(
        info.localPosition
    )

    // worldTransform은 실제로 transform 과정에 사용함
    val worldTransform = VehicleTransform(Vector())

    var localRotation: Vector
        get() = localTransform.eulerRotation
        set(value) { localTransform.eulerRotation = value; updateWorld() }

    val worldRawPosition; get() = worldTransform.position
    val worldLocation: Location
        get() {
            val position = worldTransform.position
            val offset = worldTransform.applyRotationOnlyYaw(offset)
//            DrawUtil.drawPoints(
//                DrawUtil.getRay(position, -offset, 10)  ,
//                Particle.DustOptions(Color.BLUE   , 0.5f))
//            DrawUtil.drawPoints(
//                DrawUtil.getRay(position, -offset, 10)    ,
//                Particle.DustOptions(Color.RED    , 0.5f))
//            DrawUtil.drawPoints(
//                DrawUtil.getRay(position, -offset, 10)       ,
//                Particle.DustOptions(Color.YELLOW  , 0.5f))

            return (position - offset).toLocation(world).setDirection(worldTransform.forward)
        }

    open fun tick() {
        if(parent == null) updateWorld()
        info.turretInfo?.let { tickTurret(it) }
    }

    open fun onShoot(event: WeaponShootPrepareEvent) {
        val turret = info.turretInfo ?: return
        val shootPosition = worldTransform.localToWorld(turret.localShootPosition)

        event.ray = shootPosition.toLocation(world).setDirection(worldTransform.forward)
    }

    private fun tickTurret(turret: VehicleTurretInfo) {
        val seatIndex = turret.seat
        if(seatIndex < 0 || vehicle.seatEntities.size <= seatIndex) {
            consoleWarn("invalid seat data at ${info.key} on vehicle ${vehicle.base.key}")
            return
        }
        val seat = vehicle.seatEntities[seatIndex]
        val player = seat.passenger
        val targetDirection: Vector
        // 탑승자 없거나 해당 슬롯이 아닌 경우
        if(player == null || player.inventory.heldItemSlot !in turret.slotIndexes) {
            targetDirection = vehicle.transform.forward.clone()
        } else {
            val maxDistance = player.weaponPlayer.weapon?.type?.shoot?.adjustDirectionRange ?: 160.0
            val eye = player.eyeLocation
            val eyeDirection = eye.direction
            val raycast = player.world.rayTrace(
                eye,
                eyeDirection,
                maxDistance, FluidCollisionMode.NEVER, true, 1.0
            ) { entity ->
                // 같은 차량에 탑승 중인 플레이어에 대해서 무시
                if(entity.type == EntityType.PLAYER) {
                    if(vehicle.isRiding(entity)){
                        return@rayTrace false
                    }
                }
                // 같은 차량에 대해서 무시
                entity.asVehicleEntityOrNull?.takeIf { !it.isDead }?.let { targetVehicle ->
                    if(targetVehicle == vehicle) {
                        return@rayTrace false
                    }
                }
                true
            }
            val hitPosition = raycast?.hitPosition
                // 안 닿았으면, 최대거리로 설정
                ?: eye.toVector().add(eyeDirection.clone().multiply(maxDistance))

            targetDirection = hitPosition.subtract(turret.localShootPosition.localToWorld(eyeDirection).add(worldTransform.position)).normalize()
//                .run { if(y < -0.6) player.eyeLocation.direction else this }
        }
        worldTransform.forward = lerp(worldTransform.forward, targetDirection, turret.rotateLerpSpeed)
    }


    // 부모가 있으면 부모 기준 변환
    // 없으면 root(차량) 기준 변환
    private val parentWorldTransform; get() = parent?.worldTransform ?: vehicle.transform
    fun updateWorld() {
        parentWorldTransform.localToWorld(localTransform, worldTransform, useOwnWorldRotation)
        updateChildrenWorld()
    }
    private fun updateChildrenWorld() {
        children.forEach { it.updateWorld() }
    }

    override fun remove() {
        entity.remove()
        parent = null
        children.clear()
    }

    operator fun component1() = info
    operator fun component2() = entity
}