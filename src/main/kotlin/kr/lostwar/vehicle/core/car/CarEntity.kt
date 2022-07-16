package kr.lostwar.vehicle.core.car

import kr.lostwar.util.math.VectorUtil.minus
import kr.lostwar.util.math.VectorUtil.plus
import kr.lostwar.util.math.VectorUtil.times
import kr.lostwar.util.math.clamp
import kr.lostwar.util.nms.NMSUtil.setPosition
import kr.lostwar.util.nms.NMSUtil.tryCollideAndGetModifiedVelocity
import kr.lostwar.vehicle.VehiclePlayer.Companion.vehiclePlayer
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
        super.onEarlyTick()
        input()
        updateRotation()
        move()
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
            val steerSign = if(forwardSpeed > 0) 1 else if(forwardSpeed < 0) -1 else 0
            if(driver.isLeft) {
                steering += base.steerAccelerationInRadian * steerSign
            }
            else if(driver.isRight) {
                steering += -base.steerAccelerationInRadian * steerSign
            }
            else{
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
        transform.eulerRotation = Vector(
            0.0,
            transform.eulerRotation.y + steering,
            0.0,
        )
    }

    private fun move() {
        val entities = kinematicEntities.entries.sortedByDescending { (info, entity) ->
            if(forwardSpeed >= 0) info.localPosition.z
            else -info.localPosition.z
        }

        currentGravity -= base.gravityFactor
        velocity = transform.forward.times(forwardSpeed)
        velocity.y = currentGravity

        var finalStepUp = 0.0
        var finalGravity = currentGravity
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
                velocity.x = newVelocity.x
                velocity.z = newVelocity.z
                forwardSpeed = 0.0
                steering = 0.0
            }
        }
        if(finalStepUp > 0) {
            velocity.y = finalStepUp
        }else{
            velocity.y = finalGravity
        }
        transform.position.add(velocity)
        for((info, entity) in kinematicEntities) {
            entity.setPosition(transform.transform(info))
        }
    }

}