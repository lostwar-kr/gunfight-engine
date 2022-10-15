package kr.lostwar.vehicle.core.parachute

import kr.lostwar.util.math.VectorUtil.minus
import kr.lostwar.util.math.VectorUtil.modifiedY
import kr.lostwar.util.math.VectorUtil.times
import kr.lostwar.util.math.clamp
import kr.lostwar.util.math.lerp
import kr.lostwar.util.nms.BoatNMSUtil
import kr.lostwar.util.nms.BoatNMSUtil.getGroundFriction
import kr.lostwar.util.nms.BoatNMSUtil.getWaterLevel
import kr.lostwar.util.nms.BoatNMSUtil.getWaterLevelAbove
import kr.lostwar.util.nms.NMSUtil.setIsOnGround
import kr.lostwar.util.nms.NMSUtil.setPosition
import kr.lostwar.util.nms.NMSUtil.tryCollideAndGetModifiedVelocity
import kr.lostwar.vehicle.VehiclePlayer.Companion.vehiclePlayer
import kr.lostwar.vehicle.core.Constants
import kr.lostwar.vehicle.core.VehicleEntity
import kr.lostwar.vehicle.core.VehicleTransform.Companion.eulerPitch
import kr.lostwar.vehicle.core.VehicleTransform.Companion.eulerRoll
import org.bukkit.Location
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import kotlin.math.abs

class ParachuteEntity(
    base: ParachuteInfo,
    location: Location,
    decoration: Boolean = false,
) : VehicleEntity<ParachuteInfo>(base, location, decoration) {

    override fun onEarlyTick() {
        input()
        updateRotation()
        move()
    }

    override fun onLateTick() {
    }

    private var forwardSpeed = 0.0
    private var pitch = 0.0
    private var steering = 0.0

    private fun input() {
        val driver = driverSeat.passenger?.vehiclePlayer
        // 낙하산이 운전자가 없으면 아무것도 안함
        if(driver == null) {
            if(!isDead) death()
            return
        }else{
            // 기수 내리기
            if(driver.isForward) {
                forwardSpeed += base.acceleration
                pitch += base.pitchRotateSpeedInRadianPerTick
            }
            // 감속
            else if(driver.isBackward) {
                forwardSpeed -= base.brake
                pitch -= base.pitchRotateSpeedInRadianPerTick
            }
            // 수평 유지
            else{
                forwardSpeed += base.acceleration
                pitch -= base.pitchRotateSpeedInRadianPerTick
            }
            forwardSpeed = forwardSpeed.clamp(base.speedRange)
            pitch = pitch.clamp(base.pitchRange)

            if(driver.isLeft) {
                steering += base.steerAccelerationInRadian
            }
            else if(driver.isRight) {
                steering += -base.steerAccelerationInRadian
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

        val targetRoll = (steering / base.steerMaxAngleInRadian) * base.steerRollAngleInRadian
        val roll =
            if(base.steerRollLerpSpeed >= 1.0) targetRoll
            else lerp(oldRotation.eulerRoll, targetRoll, base.steerRollLerpSpeed)

        val newRotation = Vector(
            pitch,
            oldRotation.y + steering,
            roll,
        )
        transform.eulerRotation = newRotation
    }

    private fun move() {
        val entities = kinematicEntitiesSortedByZ.let {
            if(forwardSpeed >= 0) it
            else it.asReversed()
        }

        velocity = transform.forward * forwardSpeed
        // 최소 하강 속도
        if(velocity.y > -base.minDownSpeed) {
            velocity.y = -base.minDownSpeed
        }

        for((info, entity) in entities) {
            // NMS 충돌 처리를 통해 바뀐 velocity 가져오기
            val newVelocity = entity.entity.tryCollideAndGetModifiedVelocity(velocity)
            val diff = (newVelocity - velocity)
            val xCollision = abs(diff.x) > 0.0000001
            val zCollision = abs(diff.z) > 0.0000001
            val verticalCollision = abs(diff.y) > 0
            val horizontalCollision = xCollision || zCollision

            // 충돌 발생 시 그 자리에서 그냥 개같이 파괴
            if(verticalCollision || horizontalCollision) {
                death()
                break
            }
        }
        transform.position.add(velocity)
        for((info, entity) in kinematicEntities) {
            entity.tick()
            entity.entity.setPosition(entity.worldRawPosition)
        }
    }

    override fun exit(riding: ArmorStand, player: Player, forced: Boolean): Boolean {
        if(!forced && livingTicks < base.disableExitTicks) {
            return false
        }
        return super.exit(riding, player, forced)
    }

}