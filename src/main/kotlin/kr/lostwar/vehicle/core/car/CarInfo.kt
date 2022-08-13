package kr.lostwar.vehicle.core.car

import kr.lostwar.util.Config
import kr.lostwar.util.SoundClip
import kr.lostwar.util.math.toDegrees
import kr.lostwar.util.math.toRadians
import kr.lostwar.vehicle.VehicleEngine
import kr.lostwar.vehicle.core.VehicleInfo
import org.bukkit.Location
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.util.Vector

open class CarInfo(
    key: String,
    config: ConfigurationSection,
    configFile: Config,
    parent: CarInfo?
) : VehicleInfo(key, config, configFile, parent) {

    val acceleration: Double = getDouble("car.engine.acceleration", parent?.acceleration, 0.2)
    val brake: Double = getDouble("car.engine.brake", parent?.brake, 0.2)
    val naturalDeceleration: Double = getDouble("car.engine.naturalDeceleration", parent?.naturalDeceleration, 0.01)
    val accelerationBackward: Double = getDouble("car.engine.accelerationBackward", parent?.accelerationBackward, 0.3)
    val maxSpeed: Double = getDouble("car.engine.maxSpeed", parent?.maxSpeed, 1.0)
    val maxSpeedBackward: Double = getDouble("car.engine.maxSpeedBackward", parent?.maxSpeedBackward, 0.3)
    val forwardPitchAngleInRadian: Double = getDouble("car.engine.pitch.forwardAngle", parent?.forwardPitchAngleInRadian?.toDegrees(), 0.0).toRadians()
    val backPitchAngleInRadian: Double = getDouble("car.engine.pitch.backAngle", parent?.backPitchAngleInRadian?.toDegrees(), 0.0).toRadians()
    val pitchLerpSpeed: Double = getDouble("car.engine.pitch.lerpSpeed", parent?.pitchLerpSpeed, 1.0)


    val engineSound: SoundClip = getSoundClip("car.engine.sound", parent?.engineSound)
    val engineSoundPitchRange: ClosedFloatingPointRange<Float> =
        getFloatRange("car.engine.soundPitchRange", parent?.engineSoundPitchRange, 0f..2f, 0f..2f)
    val engineSoundVolumeRange: ClosedFloatingPointRange<Float> =
        getFloatRange("car.engine.soundVolumeRange", parent?.engineSoundVolumeRange, 1f..2f, 0f..Float.MAX_VALUE)

    val steerAccelerationInRadian: Double = getDouble("car.steer.acceleration", parent?.steerAccelerationInRadian?.toDegrees(), 3.0).toRadians()
    val steerRecoverInRadian: Double = getDouble("car.steer.recover", parent?.steerRecoverInRadian?.toDegrees(), 1.0).toRadians()
    val steerMaxAngleInRadian: Double = getDouble("car.steer.maxAngle", parent?.steerMaxAngleInRadian?.toDegrees(), 30.0).toRadians()
    val steerMaxAngleRangeInRadian = -steerMaxAngleInRadian .. steerMaxAngleInRadian
    val steerMinimumSteeringPower: Double = getDouble("car.steer.minimumSteeringPower", parent?.steerMinimumSteeringPower, 0.0)
    // roll이 반대로 적용되어 , , 부호를 음수로 적용함
    val steerRollAngleInRadian: Double = -getDouble("car.steer.roll.angle", parent?.steerRollAngleInRadian?.toDegrees(), 5.0).toRadians()
    val steerRollLerpSpeed: Double = getDouble("car.steer.roll.lerpSpeed", parent?.steerRollLerpSpeed, 1.0)

    val gravityFactor: Double = getDouble("car.physics.gravity", parent?.gravityFactor, 0.08)
    val floatOnWater: Boolean = getBoolean("car.physics.canFloatOnWater", parent?.floatOnWater, false)
        .also { floatOnWater ->
            if(floatOnWater && models.count { it.value.isKinematicEntity } != 1) {
                VehicleEngine.logWarn("car ${key}가 물에 뜰 수 있으나 kinematicEntity 갯수가 1이 아님")
            }
        }
    val boatWaterFriction: Double = getDouble("car.physics.waterFriction", parent?.boatWaterFriction, 1.0)
    val boatLandFrictionMultiplier: Double = getDouble("car.physics.landFrictionMultiplier", parent?.boatLandFrictionMultiplier, 1.0)

    val canVerticalMove: Boolean = getBoolean("car.canVerticalMove", parent?.canVerticalMove, false)
    val upMaxSpeed: Double = getDouble("car.engine.up.maxSpeed", parent?.upMaxSpeed, if(canVerticalMove) 1.0 else 0.0)
    val upAcceleration: Double = getDouble("car.engine.up.acceleration", parent?.upAcceleration, if(canVerticalMove) 0.05 else 0.0)
    val upDeceleration: Double = getDouble("car.engine.up.deceleration", parent?.upDeceleration, if(canVerticalMove) 0.05 else 0.0)
    val upNaturalDeceleration: Double = getDouble("car.engine.up.naturalDeceleration", parent?.upNaturalDeceleration, if(canVerticalMove) 0.05 else 0.0)

    val downMaxSpeed: Double = getDouble("car.engine.down.maxSpeed", parent?.downMaxSpeed, if(canVerticalMove) 1.0 else 0.0)
    val downAcceleration: Double = getDouble("car.engine.down.acceleration", parent?.downAcceleration, if(canVerticalMove) 0.05 else 0.0)
    val downDeceleration: Double = getDouble("car.engine.down.deceleration", parent?.downDeceleration, if(canVerticalMove) 0.05 else 0.0)
    val downNaturalDeceleration: Double = getDouble("car.engine.down.naturalDeceleration", parent?.downNaturalDeceleration, if(canVerticalMove) 0.05 else 0.0)

    val naturalDownRequireForwardSpeed: Double = getDouble("car.engine.naturalDown.requireForwardSpeed", parent?.naturalDownRequireForwardSpeed, if(canVerticalMove) 0.2 else 0.0)
    val naturalDownRequireBackwardSpeed: Double = getDouble("car.engine.naturalDown.requireBackwardSpeed", parent?.naturalDownRequireBackwardSpeed, if(canVerticalMove) 0.1 else 0.0)
    val naturalDownAcceleration: Double = getDouble("car.engine.naturalDown.acceleration", parent?.naturalDownAcceleration, if(canVerticalMove) 0.02 else 0.0)
    val naturalDownDeceleration: Double = getDouble("car.engine.naturalDown.deceleration", parent?.naturalDownDeceleration, if(canVerticalMove) 0.02 else 0.0)
    val naturalDownMaxSpeed: Double = getDouble("car.engine.naturalDown.maxSpeed", parent?.naturalDownMaxSpeed, if(canVerticalMove) 0.2 else 0.0)

    val pushHitboxInflateAmount: Double = getDouble("push.hitboxInflateAmount", parent?.pushHitboxInflateAmount, 0.0)
    val pushForceMultiplier: Vector = getVector("push.forceMultiplier", parent?.pushForceMultiplier, Vector(1, 1, 1))
    val pushDamage: Double = getDouble("push.damage", parent?.pushDamage, 0.0)
    val pushSound: SoundClip = getSoundClip("push.sound", parent?.pushSound)
    val pushNoDamageTicks: Int = getInt("push.noDamageTicks", parent?.pushNoDamageTicks, 10)
    val pushMinimumSpeed: Double = getDouble("push.minimumSpeed", parent?.pushMinimumSpeed, 0.01)
    val pushDecelerationPercentage: Double = getDouble("push.decelerationPercentage", parent?.pushDecelerationPercentage, 0.05)
    override fun spawn(location: Location, decoration: Boolean) = CarEntity(this, location, decoration)

}