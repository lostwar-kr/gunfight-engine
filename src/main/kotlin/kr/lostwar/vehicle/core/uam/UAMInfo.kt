package kr.lostwar.vehicle.core.uam

import kr.lostwar.util.Config
import kr.lostwar.util.SoundClip
import kr.lostwar.util.math.toDegrees
import kr.lostwar.util.math.toRadians
import kr.lostwar.vehicle.core.VehicleInfo
import org.bukkit.Location
import org.bukkit.configuration.ConfigurationSection

class UAMInfo(
    key: String,
    config: ConfigurationSection,
    configFile: Config,
    parent: UAMInfo?
) : VehicleInfo(key, config, configFile, parent) {

    override val disableDriverExitVehicleByShiftKey: Boolean = true

    val acceleration: Double = getDouble("uam.engine.forward.acceleration", parent?.acceleration, 0.2)
    val brake: Double = getDouble("uam.engine.forward.brake", parent?.brake, 0.2)
    val naturalDeceleration: Double = getDouble("uam.engine.forward.naturalDeceleration", parent?.naturalDeceleration, 0.01)
    val accelerationBackward: Double = getDouble("uam.engine.forward.accelerationBackward", parent?.accelerationBackward, 0.3)
    val maxSpeed: Double = getDouble("uam.engine.forward.maxSpeed", parent?.maxSpeed, 1.0)
    val maxSpeedBackward: Double = getDouble("uam.engine.forward.maxSpeedBackward", parent?.maxSpeedBackward, 0.3)
    val forwardPitchAngleInRadian: Double = getDouble("uam.engine.forward.pitch.angle", parent?.forwardPitchAngleInRadian?.toDegrees(), 5.0).toRadians()
    val forwardPitchLerpSpeed: Double = getDouble("uam.engine.forward.pitch.lerpSpeed", parent?.forwardPitchLerpSpeed, 1.0)

    val engineSound: SoundClip = getSoundClip("uam.engine.sound", parent?.engineSound)
    val engineSoundPitchRange: ClosedFloatingPointRange<Float> =
        getFloatRange("uam.engine.soundPitchRange", parent?.engineSoundPitchRange, 0f..2f, 0f..2f)
    val engineSoundVolumeRange: ClosedFloatingPointRange<Float> =
        getFloatRange("uam.engine.soundVolumeRange", parent?.engineSoundVolumeRange, 1f..2f, 0f..Float.MAX_VALUE)

    val steerAccelerationInRadian: Double = getDouble("uam.steer.acceleration", parent?.steerAccelerationInRadian?.toDegrees(), 3.0).toRadians()
    val steerRecoverInRadian: Double = getDouble("uam.steer.recover", parent?.steerRecoverInRadian?.toDegrees(), 1.0).toRadians()
    val steerMaxAngleInRadian: Double = getDouble("uam.steer.maxAngle", parent?.steerMaxAngleInRadian?.toDegrees(), 30.0).toRadians()
    val steerMaxAngleRangeInRadian = -steerMaxAngleInRadian .. steerMaxAngleInRadian
    // roll이 반대로 적용되어 , , 부호를 음수로 적용함
    val steerRollAngleInRadian: Double = -getDouble("uam.steer.roll.angle", parent?.steerRollAngleInRadian?.toDegrees(), 5.0).toRadians()
    val steerRollLerpSpeed: Double = getDouble("uam.steer.roll.lerpSpeed", parent?.steerRollLerpSpeed, 1.0)

    val upMaxSpeed: Double = getDouble("uam.engine.up.maxSpeed", parent?.upMaxSpeed, 1.0)
    val upAcceleration: Double = getDouble("uam.engine.up.acceleration", parent?.upAcceleration, 0.05)
    val upDeceleration: Double = getDouble("uam.engine.up.deceleration", parent?.upDeceleration, 0.05)
    val upNaturalDeceleration: Double = getDouble("uam.engine.up.naturalDeceleration", parent?.upNaturalDeceleration, 0.05)

    val downMaxSpeed: Double = getDouble("uam.engine.down.maxSpeed", parent?.downMaxSpeed, 1.0)
    val downAcceleration: Double = getDouble("uam.engine.down.acceleration", parent?.downAcceleration, 0.05)
    val downDeceleration: Double = getDouble("uam.engine.down.deceleration", parent?.downDeceleration, 0.05)
    val downNaturalDeceleration: Double = getDouble("uam.engine.down.naturalDeceleration", parent?.downNaturalDeceleration, 0.05)
    val downMaxSpeedAbandoned: Double = getDouble("uam.engine.down.maxSpeedAbandoned", parent?.downMaxSpeedAbandoned, 0.2)


    val gravityFactor: Double = getDouble("uam.physics.gravity", parent?.gravityFactor, 0.08)
    val boatWaterFriction: Double = getDouble("uam.physics.waterFriction", parent?.boatWaterFriction, 1.0)
    val boatLandFrictionMultiplier: Double = getDouble("uam.physics.landFrictionMultiplier", parent?.boatLandFrictionMultiplier, 0.0)
    
    override fun spawn(location: Location, decoration: Boolean): UAMEntity =
        UAMEntity(this, location, decoration)

}