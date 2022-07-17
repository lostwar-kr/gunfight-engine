package kr.lostwar.vehicle.core.car

import kr.lostwar.util.math.VectorUtil.minus
import kr.lostwar.util.math.VectorUtil.times
import kr.lostwar.util.math.clamp
import kr.lostwar.util.math.lerp
import kr.lostwar.util.nms.BoatNMSUtil
import kr.lostwar.util.nms.BoatNMSUtil.getGroundFriction
import kr.lostwar.util.nms.BoatNMSUtil.getWaterLevel
import kr.lostwar.util.nms.BoatNMSUtil.getWaterLevelAbove
import kr.lostwar.util.nms.BoatNMSUtil.isUnderWater
import kr.lostwar.util.nms.NMSUtil.setIsOnGround
import kr.lostwar.util.nms.NMSUtil.setPosition
import kr.lostwar.util.nms.NMSUtil.tryCollideAndGetModifiedVelocity
import kr.lostwar.vehicle.VehiclePlayer.Companion.vehiclePlayer
import kr.lostwar.vehicle.core.Constants
import kr.lostwar.vehicle.core.VehicleEntity
import org.bukkit.Location
import org.bukkit.util.Vector
import kotlin.math.abs

class CarEntity(
    base: CarInfo,
    location: Location,
    decoration: Boolean = false,
) : VehicleEntity<CarInfo>(base, location, decoration) {

    override fun onEarlyTick() {
        input()
        updateRotation()
        move()
    }

    override fun onTick() {
    }

    override fun onLateTick() {
        engineSound()
    }

    private fun engineSound() {
        if(abs(forwardSpeed) > 0) {
            val percentage = (abs(forwardSpeed) / base.maxSpeed).toFloat()
            base.engineSound.playAt(location,
                volume = base.engineSoundVolumeRange.lerp(percentage),
                pitch = base.engineSoundPitchRange.lerp(percentage),
            )
        }
    }

    private var currentGravity = 0.0
    private var forwardSpeed = 0.0
    private var steering = 0.0

    private fun input() {
        val driver = driverSeat.passenger?.vehiclePlayer
        // 운전자가 없을 경우 속도 서서히 감소
        if(driver == null) {
            forwardSpeed = (forwardSpeed - base.natureDeceleration).coerceAtLeast(0.0)
            steering = if(steering > 0) {
                (steering - base.steerRecoverInRadian).coerceAtLeast(0.0)
            }else if(steering < 0){
                (steering + base.steerRecoverInRadian).coerceAtMost(0.0)
            }else 0.0
        }else{
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
                    (forwardSpeed - base.natureDeceleration).coerceAtLeast(0.0)
                }
            }
            // 후진 또는 정지중일 때
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
                    (forwardSpeed + base.natureDeceleration).coerceAtMost(0.0)
                }
            }
            val steerPower =
                if(forwardSpeed > 0)
                    forwardSpeed / base.maxSpeed
                else if(forwardSpeed < 0)
                    forwardSpeed / base.maxSpeedBackward
                else 0.0
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
        val newRotation = Vector(
            0.0,
            oldRotation.y + steering,
            0.0,
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
    private var boatState: BoatNMSUtil.BoatState = getBoatState()
    private var boatWaterLevel: Double = 0.0
    private var boatLandFriction: Double = 0.0
    private var boatLastDeltaY = 0.0
    private fun getBoatState(): BoatNMSUtil.BoatState {
        val (info, entity) = kinematicEntities.entries.first()
        entity.isUnderWater()?.let {
            boatWaterLevel = entity.boundingBox.maxY + 0.001
            return it
        }
        val (waterLevel, isInWater) = entity.getWaterLevel()
        boatWaterLevel = waterLevel
        if(isInWater) {
            return BoatNMSUtil.BoatState.IN_WATER
        }
        val friction = entity.getGroundFriction()
        if(friction > 0f) {
            boatLandFriction = friction.toDouble()
            return BoatNMSUtil.BoatState.ON_LAND
        }
        return BoatNMSUtil.BoatState.IN_AIR
    }
    private fun floatBoat() {
        val (info, entity) = kinematicEntities.entries.first()
        oldBoatState = boatState
        boatState = getBoatState()

        val y = entity.location.y
        var invFriction = 0.05
        var gravity = currentGravity
        var upwardMove = 0.0
        if(oldBoatState == BoatNMSUtil.BoatState.IN_AIR
            && boatState != BoatNMSUtil.BoatState.IN_AIR
            && boatState != BoatNMSUtil.BoatState.ON_LAND
        ) {
            boatWaterLevel = y + info.hitbox.height
            transform.position.y = (entity.getWaterLevelAbove(boatLastDeltaY) - info.hitbox.height) + 0.101
            velocity.y = 0.0
            boatLastDeltaY = 0.0
            boatState = BoatNMSUtil.BoatState.IN_WATER
        }else when(boatState) {
            BoatNMSUtil.BoatState.IN_WATER -> {
                gravity = -7.0E-4
                currentGravity = 0.0
                upwardMove = (boatWaterLevel - y)
                invFriction = base.boatWaterFriction
            }
            BoatNMSUtil.BoatState.UNDER_FLOWING_WATER -> {
                gravity = -7.0E-4
                currentGravity = 0.0
                upwardMove = 1.0
                invFriction = 0.9
            }
            BoatNMSUtil.BoatState.UNDER_WATER -> {
                gravity = -7.0E-4
                currentGravity = 0.0
                upwardMove = 1.0
                invFriction = 0.45
            }
            BoatNMSUtil.BoatState.IN_AIR -> {
                invFriction = 0.9
            }
            BoatNMSUtil.BoatState.ON_LAND -> {
                invFriction = boatLandFriction * base.boatLandFrictionMultiplier
                currentGravity = 0.0
            }
        }

        velocity.x *= invFriction
        velocity.z *= invFriction
        velocity.y = gravity
        if(upwardMove > 0.0) {
            velocity.y = velocity.y + upwardMove
        }
//        console("boatState: $boatState, gravity: $gravity, upwardMove: $upwardMove, invFriction: $invFriction, velocity: ${velocity.getDisplayString()}")
    }

    private fun move() {
        val lastForwardSpeed = forwardSpeed
        val entities = kinematicEntitiesSortedByZ.let {
            if(forwardSpeed >= 0) it
            else it.asReversed()
        }

        currentGravity -= base.gravityFactor
        velocity = transform.forward.times(forwardSpeed)
        velocity.y = currentGravity

        if(base.floatOnWater) {
            floatBoat()
        }

        var finalStepUp = 0.0
        var finalGravity = currentGravity
        var collision = false
        for((info, entity) in entities) {
            // NMS 충돌 처리를 통해 바뀐 velocity 가져오기
            val newVelocity = entity.tryCollideAndGetModifiedVelocity(velocity)
            val diff = (newVelocity - velocity)
            val xCollision = abs(diff.x) > 0.0000001
            val zCollision = abs(diff.z) > 0.0000001
            val verticalCollision = abs(diff.y) > 0
            val horizontalCollision = xCollision || zCollision

            // 바닥하고 충돌한 경우 (수직 변화가 있었고, 원래 의도한 velocity는 중력 감소였다면)
            if(verticalCollision && velocity.y < 0.0) {
                finalGravity = 0.0
                currentGravity = 0.0
                velocity.y = 0.0
                entities.forEach { it.value.setIsOnGround(true) }
            }

            // 경사 올랐으면 모든 엔티티 중 최대 경사 오름으로 설정
            // 또한 중력 초기화
            if(newVelocity.y > finalStepUp) {
                finalStepUp = newVelocity.y
            }

            // 중력으로 내려간 엔티티 중 가장 최댓값으로 설정
            // 즉, 바닥이 있어서 안 내려간 게 있으면 전부 내려가지 않음
            if(newVelocity.y <= 0 && newVelocity.y > finalGravity) {
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
            if(finalGravity < 0) {
                entities.forEach { it.value.setIsOnGround(true) }
            }
        }
        transform.position.add(velocity)
        boatLastDeltaY = velocity.y
        for((info, entity) in kinematicEntities) {
            entity.setPosition(transform.transform(info))
        }

        if(collision && primaryEntity.noDamageTicks <= 0) {
            damage(base.collisionDamagePerSpeed * abs(lastForwardSpeed), Constants.collisionDamageCause)
            base.collisionSound.playAt(location)
        }
    }

}