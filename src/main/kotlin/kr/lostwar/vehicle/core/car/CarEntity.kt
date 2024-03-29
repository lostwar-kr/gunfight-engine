package kr.lostwar.vehicle.core.car

import kr.lostwar.util.math.VectorUtil.minus
import kr.lostwar.util.math.VectorUtil.modifiedY
import kr.lostwar.util.math.clamp
import kr.lostwar.util.math.lerp
import kr.lostwar.util.nms.BoatNMSUtil
import kr.lostwar.util.nms.BoatNMSUtil.getGroundFriction
import kr.lostwar.util.nms.BoatNMSUtil.getWaterLevel
import kr.lostwar.util.nms.BoatNMSUtil.isUnderWaterAndGetBoatState
import kr.lostwar.util.nms.NMSUtil.damage
import kr.lostwar.util.nms.NMSUtil.setIsOnGround
import kr.lostwar.util.nms.NMSUtil.setPosition
import kr.lostwar.util.nms.NMSUtil.tryCollideAndGetModifiedVelocity
import kr.lostwar.vehicle.VehiclePlayer.Companion.vehiclePlayer
import kr.lostwar.vehicle.core.Constants
import kr.lostwar.vehicle.core.VehicleEntity
import kr.lostwar.vehicle.core.VehicleTransform.Companion.eulerPitch
import kr.lostwar.vehicle.core.VehicleTransform.Companion.eulerRoll
import kr.lostwar.vehicle.event.VehiclePushEntityEvent
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.util.Vector
import kotlin.math.abs

class CarEntity(
    base: CarInfo,
    location: Location,
    decoration: Boolean = false,
) : VehicleEntity<CarInfo>(base, location, decoration) {

    private var lastForwardSpeed = 0.0
    private var lastUpSpeed = 0.0
    override fun onEarlyTick() {
        lastForwardSpeed = forwardSpeed
        lastUpSpeed = upSpeed
        input()
        updateRotation()
        move()
    }

    override fun onTick() {
        super.onTick()
        pushEntities()
    }
    private fun pushEntities() {
        val speed = abs(forwardSpeed)
        if(speed < base.pushMinimumSpeed) {
            return
        }
        val speedMultiplier = speed / base.maxSpeed
        val damage = base.pushDamage * speedMultiplier
        if(damage <= 0) return
        val entities = kinematicEntitiesSortedByZ.flatMap { (_, kinematic) ->
            val checkHitbox = kinematic.boundingBox.expand(base.pushHitboxInflateAmount)
            world.getNearbyEntities(checkHitbox) { entity ->
                // 같은 차량인 경우 스킵
                if(contains(entity) || contains(entity.vehicle)) return@getNearbyEntities false
                // 차량이면 아무튼 스킵
                if(entity.vehicleEntityIdOrNull != null) return@getNearbyEntities false
                // 플레이어 게임모드 스킵
                if(entity.type == EntityType.PLAYER) {
                    val player = entity as Player
                    if(player == lastDriver || player.gameMode == GameMode.SPECTATOR || player.gameMode == GameMode.CREATIVE) {
                        return@getNearbyEntities false
                    }
                }
                // LivingEntity 아니면 스킵
                if(entity !is LivingEntity) return@getNearbyEntities false
                return@getNearbyEntities true
            }.map { it as LivingEntity to kinematic }
        }.distinctBy { it.first.entityId }
        for((entity, kinematic) in entities) {
            if(entity.noDamageTicks > 0) continue
            val event = VehiclePushEntityEvent(this, entity, damage).also { it.callEvent() }
            if(event.isCancelled) continue
            val direction = entity.location.toVector()
                .subtract(kinematic.worldRawPosition)
                .add(transform.forward)
                .multiply(speedMultiplier)
                .multiply(base.pushForceMultiplier)
            entity.velocity = direction
            entity.damage(event.damage, Constants.collisionDamageCause)
            base.pushSound.playAt(entity.location)
            entity.noDamageTicks = base.pushNoDamageTicks
        }
        if(entities.isNotEmpty()) {
            forwardSpeed -= forwardSpeed * base.pushDecelerationPercentage
        }
    }

    override fun onLateTick() {
        engineSound()
        callAnimationEvents()
    }

    private fun Double.square() = this * this
    private fun engineSound() {
        if(abs(forwardSpeed) + abs(upSpeed) + abs(naturalDownSpeed) > 0) {
            val percentage = ((forwardSpeed.square() + upSpeed.square() + naturalDownSpeed.square()) / (base.maxSpeed.square())).toFloat()
            base.engineSound.playAt(location,
                volume = base.engineSoundVolumeRange.lerp(percentage),
                pitch = base.engineSoundPitchRange.lerp(percentage),
            )
        }
    }

    private fun callAnimationEvents() {
        if(oldBoatState != BoatNMSUtil.BoatState.ON_LAND && boatState == BoatNMSUtil.BoatState.ON_LAND) {
            callAnimation("onLand")
        }
        // 기존 속도가 총합 0이 아니었고, 어쨌든 지금 속도가 총합 0이 된 경우
//        if((lastForwardSpeed != 0.0 || lastUpSpeed != 0.0) && forwardSpeed == 0.0 && upSpeed == 0.0 && boatState == BoatNMSUtil.BoatState.ON_LAND) {
//            callAnimation("onStopWhileLand")
//        }
        
        // 아예 멈춰있다가 위로든 앞으로든 일단 움직인 경우
        if(lastForwardSpeed == 0.0 && lastUpSpeed == 0.0 && (forwardSpeed != 0.0 || upSpeed != 0.0)) {
            callAnimation("onStartMove")
        }
    }

    private var forwardSpeed = 0.0
    private var upSpeed = 0.0
    private var naturalDownSpeed = 0.0
    private var steering = 0.0


    private fun input() {
        val driver = driverSeat.passenger?.vehiclePlayer
        // 운전자가 없을 경우 속도 서서히 감소
        if(driver == null) {
            forwardSpeed = (forwardSpeed - base.naturalDeceleration).coerceAtLeast(0.0)
            steering = if(steering > 0) {
                (steering - base.steerRecoverInRadian).coerceAtLeast(0.0)
            }else if(steering < 0){
                (steering + base.steerRecoverInRadian).coerceAtMost(0.0)
            }else 0.0
            if(base.canVerticalMove){
                upSpeed = if(upSpeed > 0) {
                    (upSpeed - base.upDeceleration).coerceAtLeast(0.0)
                }
                // 상승 감속
                else if(upSpeed < 0) {
                    (upSpeed + base.downDeceleration).coerceAtMost(0.0)
                }
                else 0.0
                naturalDownSpeed = base.naturalDownMaxSpeed
            }
        }else{
            if(base.canVerticalMove) {
                // 상승
                if(driver.isSpace) {
                    // 상승 가속
                    upSpeed = if(upSpeed >= 0) {
                        (upSpeed + base.upAcceleration).coerceAtMost(base.upMaxSpeed)
                    }
                    // 하강 감속
                    else {
                        (upSpeed + base.downDeceleration).coerceAtMost(0.0)
                    }
                    // 자동 추락 속도는 0으로 수렴
                    naturalDownSpeed = (naturalDownSpeed - base.naturalDownDeceleration).coerceAtLeast(0.0)
                }
                // 하강
                else if(driver.isShift) {
                    // 하강 가속
                    upSpeed = if(upSpeed <= 0) {
                        (upSpeed - base.downAcceleration).coerceAtLeast(-base.downMaxSpeed)
                    }
                    // 상승 감속
                    else {
                        (upSpeed - base.upDeceleration).coerceAtLeast(0.0)
                    }
                    // 자동 추락 속도는 0으로 수렴
                    naturalDownSpeed = (naturalDownSpeed - base.naturalDownDeceleration).coerceAtLeast(0.0)
                }
                // 아무것도 안 눌렀으면
                else {
                    upSpeed = if(upSpeed > 0) {
                        (upSpeed - base.upNaturalDeceleration).coerceAtLeast(0.0)
                    }else {
                        (upSpeed + base.downNaturalDeceleration).coerceAtMost(0.0)
                    }
                }
            }
            // 전진 또는 정지중일 때
            if(forwardSpeed >= 0) {
                // 가속 페달
                forwardSpeed = if(driver.isForward) {
                    (forwardSpeed + base.acceleration).coerceAtMost(base.maxSpeed)
                }
                // 브레이크 페달
                else if(driver.isBackward) {
                    (forwardSpeed - base.brake).coerceAtLeast(0.0)
                }
                // 속도 자연 감속
                else {
                    (forwardSpeed - base.naturalDeceleration).coerceAtLeast(0.0)
                }
                // 속도가 일정 이상이면 자동 추락 속도는 0으로 수렴
                naturalDownSpeed = if(forwardSpeed > base.naturalDownRequireForwardSpeed) {
                    (naturalDownSpeed - base.naturalDownDeceleration).coerceAtLeast(0.0)
                }
                // 미만이면 자동 추락 속도 가속
                else {
                    (naturalDownSpeed + base.naturalDownAcceleration).coerceAtMost(base.naturalDownMaxSpeed)
                }
            }
            // 후진중일 때
            if(forwardSpeed <= 0) {
                // (후진) 가속 페달
                forwardSpeed = if(driver.isBackward) {
                    (forwardSpeed - base.accelerationBackward).coerceAtLeast(-base.maxSpeedBackward)
                }
                // (후진) 브레이크 페달
                else if(driver.isForward) {
                    (forwardSpeed + base.brake).coerceAtMost(0.0)
                }
                // 속도 자연 감속
                else{
                    (forwardSpeed + base.naturalDeceleration).coerceAtMost(0.0)
                }
                // 속도가 일정 이상이면 자동 추락 속도는 0으로 수렴
                naturalDownSpeed = if(forwardSpeed < -base.naturalDownRequireBackwardSpeed) {
                    (naturalDownSpeed - base.naturalDownDeceleration).coerceAtLeast(0.0)
                }
                // 미만이면 자동 추락 속도 가속
                else {
                    (naturalDownSpeed + base.naturalDownAcceleration).coerceAtMost(base.naturalDownMaxSpeed)
                }
            }
            val steerPower = (
                if (forwardSpeed > 0)
                    forwardSpeed / base.maxSpeed
                else if (forwardSpeed < 0)
                    forwardSpeed / base.maxSpeedBackward
                else 0.0
            ).let {
                    // 최소 스티어링, 제자리 회전도 가능하도록
                val minimumPower = base.steerMinimumSteeringPower
                if(minimumPower > 0) {
                    if (it < 0 && it > -minimumPower) {
                        -minimumPower
                    } else if (it >= 0 && it < minimumPower) {
                        minimumPower
                    } else it
                } else it
            }
            if(steerPower != 0.0 && driver.isLeft) {
                steering += base.steerAccelerationInRadian * steerPower
            }
            else if(steerPower != 0.0 && driver.isRight) {
                steering += -base.steerAccelerationInRadian * steerPower
            }else{
                if(steering > 0) {
                    steering = (steering - base.steerRecoverInRadian).coerceAtLeast(0.0)
                }else if(steering < 0){
                    steering = (steering + base.steerRecoverInRadian).coerceAtMost(0.0)
                }
            }
            steering = steering.clamp(base.steerMaxAngleRangeInRadian)
        }
    }

    private fun updateRotation() {
        val oldRotation = transform.eulerRotation

        val targetPitch = if(forwardSpeed > 0)
            (forwardSpeed.coerceAtLeast(0.0) / base.maxSpeed) * base.forwardPitchAngleInRadian
        else if(forwardSpeed < 0){
            -(forwardSpeed.coerceAtMost(0.0) / base.maxSpeedBackward) * base.backPitchAngleInRadian
        }else 0.0
        val pitch =
            if(base.pitchLerpSpeed >= 1.0) targetPitch
            else lerp(oldRotation.eulerPitch, targetPitch, base.pitchLerpSpeed)

        val speedMultiplier =
            if(forwardSpeed > 0) forwardSpeed / base.maxSpeed
            else if(forwardSpeed < 0) forwardSpeed / base.maxSpeedBackward
            else 0.0
        val targetRoll = (steering / base.steerMaxAngleInRadian) * base.steerRollAngleInRadian * speedMultiplier
        val roll =
            if(base.steerRollLerpSpeed >= 1.0) targetRoll
            else lerp(oldRotation.eulerRoll, targetRoll, base.steerRollLerpSpeed)

        val newRotation = Vector(
            pitch,
            oldRotation.y + steering,
            roll,
        )
        transform.eulerRotation = newRotation
        /*
        if(steering == 0.0) return
        val oldRotation = transform.eulerRotation
        val newRotation = Vector(
            0.0,
            oldRotation.y + steering,
            0.0,
        )
        val oldTransform = transform.clone()
        transform.eulerRotation = newRotation

        for((info, entity) in kinematicEntities) {
            // 이 회전으로 인한 kinematicEntity의 이동
            val velocity = transform.transform(info).subtract(entity.location.toVector())
            val newVelocity = entity.tryCollideAndGetModifiedVelocity(velocity)
            val diff = (newVelocity - velocity)
            val xCollision = diff.x != 0.0
            val zCollision = diff.z != 0.0
            val horizontalCollision = xCollision || zCollision
            // 회전으로 인해 충돌이 발생할 경우, 회전을 취소함
            if(horizontalCollision) {
                steering = 0.0
                transform.copyRotation(oldTransform)
                break
            }
        }

         */
    }

    private var oldBoatState: BoatNMSUtil.BoatState? = null
    var boatState: BoatNMSUtil.BoatState = BoatNMSUtil.BoatState.ON_LAND
    private var boatWaterLevel: Double = 0.0
    var boatLandFriction: Double = 0.0
    private var boatLastDeltaY = 0.0
    private fun updateBoatState(): BoatNMSUtil.BoatState {
        val (_, entity) = kinematicEntities.entries.first()
        entity.entity.isUnderWaterAndGetBoatState()?.let {
            boatWaterLevel = entity.boundingBox.maxY + 0.001
            return it
        }
        val (waterLevel, isInWater) = entity.entity.getWaterLevel(0.1)
        boatWaterLevel = waterLevel
        if(isInWater) {
            return BoatNMSUtil.BoatState.IN_WATER
        }
        val friction = entity.entity.getGroundFriction(0.01)
        if(friction > 0f) {
            boatLandFriction = friction.toDouble()
            return BoatNMSUtil.BoatState.ON_LAND
        }
        return BoatNMSUtil.BoatState.IN_AIR
    }
    private fun floatBoat() {
        val (_, entity) = kinematicEntities.entries.first()
        val info = entity.info
        oldBoatState = boatState
        boatState = updateBoatState()

        val y = entity.location.y
        var invFriction = 0.05
        var verticalMove = velocity.y
        var upwardMove = 0.0
        /* if(oldBoatState == BoatNMSUtil.BoatState.IN_AIR
            && boatState != BoatNMSUtil.BoatState.IN_AIR
            && boatState != BoatNMSUtil.BoatState.ON_LAND
        ) {
            boatWaterLevel = y + info.hitbox.height
            val waterLevelAbove = entity.entity.getWaterLevelAbove(boatLastDeltaY)
            val moveY = ((waterLevelAbove - info.hitbox.height) + 0.101 - y)
            transform.position.y = transform.position.y + moveY
            velocity.y = 0.0
            naturalDownSpeed = 0.0
            boatLastDeltaY = 0.0
            boatState = BoatNMSUtil.BoatState.IN_WATER
            verticalMove = 0.0
            console("air to water =================")
            console("boatWaterLevel: ${y} + ${info.hitbox.height} = ${boatWaterLevel}")
            console("transform.position.y: (${waterLevelAbove} - ${info.hitbox.height} + 0.101) = ${transform.position.y}")
        }else */ when(boatState) {
            BoatNMSUtil.BoatState.IN_WATER -> {
                upSpeed = upSpeed.coerceAtLeast(0.0)
                naturalDownSpeed = 0.0
                verticalMove = upSpeed + naturalDownSpeed
                upwardMove = (boatWaterLevel - y) * 0.06
                invFriction = base.boatWaterFriction
            }
            BoatNMSUtil.BoatState.UNDER_FLOWING_WATER -> {
                upSpeed = upSpeed.coerceAtLeast(0.0)
                naturalDownSpeed = 0.0
                verticalMove = upSpeed + naturalDownSpeed
                upwardMove = 1.0
                invFriction = 0.9
            }
            BoatNMSUtil.BoatState.UNDER_WATER -> {
                upSpeed = upSpeed.coerceAtLeast(0.0)
                naturalDownSpeed = 0.0
                verticalMove = upSpeed + naturalDownSpeed
                upwardMove = 1.0
                invFriction = 0.45
            }
            BoatNMSUtil.BoatState.IN_AIR -> {
                invFriction = 1.0
            }
            BoatNMSUtil.BoatState.ON_LAND -> {
                invFriction = boatLandFriction * base.boatLandFrictionMultiplier
                if(base.boatLandFrictionMultiplier <= 0){
                    forwardSpeed = 0.0
                }
                upSpeed = upSpeed.coerceAtLeast(0.0)
                naturalDownSpeed = 0.0
                verticalMove = upSpeed + naturalDownSpeed
            }
        }

        velocity.x *= invFriction
        velocity.z *= invFriction
        velocity.y = verticalMove + upwardMove
//        console("boatState: $boatState, waterLevel: $boatWaterLevel, verticalMove: $verticalMove, upwardMove: $upwardMove, invFriction: $invFriction, velocity: ${velocity.getDisplayString()}")
    }

    // 최대높이 하드코딩
    var maxAltitudeGetter = { 324.0 }
    private fun move() {
        val entities = kinematicEntitiesSortedByZ.let {
            if(forwardSpeed >= 0) it
            else it.asReversed()
        }

        upSpeed -= base.gravityFactor
        velocity = transform.forward.modifiedY(0.0).normalize().multiply(forwardSpeed)
        velocity.y = upSpeed - if(base.canVerticalMove) naturalDownSpeed else 0.0
//        console("naturalDownSpeed: ${naturalDownSpeed}, velocity.y: ${velocity.y}")

        if(base.floatOnWater) {
            floatBoat()
        }else{
            for(kinematic in kinematicEntities.values) {
                val state = kinematic.entity.isUnderWaterAndGetBoatState()
                if(state == BoatNMSUtil.BoatState.IN_WATER
                    || state == BoatNMSUtil.BoatState.UNDER_WATER
                    || state == BoatNMSUtil.BoatState.UNDER_FLOWING_WATER
                ){
                    damage(1.0, EntityDamageEvent.DamageCause.DROWNING)
                    break
                }
            }
        }

        var finalStepUp = 0.0
        var finalGravity = velocity.y
//        console("start finalGravity: ${finalGravity}")
        var collision = false
        var land = false
        for((_, entity) in entities) {
            // NMS 충돌 처리를 통해 바뀐 velocity 가져오기
            val newVelocity = entity.entity.tryCollideAndGetModifiedVelocity(velocity)
            val diff = (newVelocity - velocity)
            val xCollision = abs(diff.x) > 0.0000001
            val zCollision = abs(diff.z) > 0.0000001
            val verticalCollision = abs(diff.y) > 0
            val horizontalCollision = xCollision || zCollision

//            if(verticalCollision)
//                console("&eold=${velocity.toVectorString()}&r, &anew=${newVelocity.toVectorString()}&r")
            // 바닥하고 충돌한 경우 (수직 변화가 있었고, 원래 의도한 velocity는 중력 감소였다면)
            if(verticalCollision && velocity.y < 0.0) {
//                console("- reset gravity by land on ground")
                finalGravity = newVelocity.y
                velocity.y = newVelocity.y
                upSpeed = upSpeed.coerceAtLeast(0.0)
                naturalDownSpeed = 0.0
                entities.forEach { it.value.entity.setIsOnGround(true) }
                land = true
            }

            // 경사 올랐으면 모든 엔티티 중 최대 경사 오름으로 설정
            // 또한 중력 초기화
            if(newVelocity.y > finalStepUp) {
                finalStepUp = newVelocity.y
            }

            // 중력으로 내려간 엔티티 중 가장 최댓값으로 설정
            // 즉, 바닥이 있어서 안 내려간 게 있으면 전부 내려가지 않음
            if(newVelocity.y <= 0 && newVelocity.y > finalGravity) {
//                console("- set greater gravity: ${newVelocity.y} (finalGravity: ${finalGravity})")
                finalGravity = newVelocity.y
            }

            // 수평 충돌 시 수평 속도 0으로 설정
            if(horizontalCollision) {
                // xz 속도 변경, 다른 엔티티에도 적용함
                if(xCollision) {
                    velocity.x = -velocity.x * 2
                }
                if(zCollision) {
                    velocity.z = -velocity.z * 2
                }
//                forwardSpeed = -forwardSpeed / 2.0
                forwardSpeed = 0.0
                steering = 0.0
                collision = true
            }
        }
        if(finalStepUp > 0) {
            velocity.y = finalStepUp
        }else{
            velocity.y = finalGravity
            if(!land && finalGravity < 0) {
                entities.forEach { it.value.entity.setIsOnGround(false) }
            }
        }
//        console("final velocity: ${velocity} (finalStepUp: ${finalStepUp}, finalGravity: ${finalGravity})")
        transform.position.add(velocity)
        transform.position.y = transform.position.y.coerceAtMost(maxAltitudeGetter())
        boatLastDeltaY = velocity.y
        for((_, entity) in kinematicEntities) {
            entity.tick()
            entity.entity.setPosition(entity.worldRawPosition)
        }

        if(collision && primaryEntity.noDamageTicks <= 0) {
            damage(base.collisionDamagePerSpeed * abs(lastForwardSpeed), Constants.collisionDamageCause)
            base.collisionSound.playAt(location)
        }
    }

}