package kr.lostwar.vehicle.core.car

import kr.lostwar.util.Config
import kr.lostwar.util.SoundClip
import kr.lostwar.util.math.toDegrees
import kr.lostwar.util.math.toRadians
import kr.lostwar.vehicle.VehicleEngine
import kr.lostwar.vehicle.core.VehicleInfo
import org.bukkit.Location
import org.bukkit.configuration.ConfigurationSection

class CarInfo(
    key: String,
    config: ConfigurationSection,
    configFile: Config,
    parent: CarInfo?
) : VehicleInfo(key, config, configFile, parent) {

    val acceleration: Double = getDouble("car.engine.acceleration", parent?.acceleration, 0.2)
    val brake: Double = getDouble("car.engine.brake", parent?.brake, 0.2)
    val natureDeceleration: Double = getDouble("car.engine.natureDeceleration", parent?.natureDeceleration, 0.01)
    val accelerationBackward: Double = getDouble("car.engine.accelerationBackward", parent?.accelerationBackward, 0.3)
    val maxSpeed: Double = getDouble("car.engine.maxSpeed", parent?.maxSpeed, 1.0)
    val maxSpeedBackward: Double = getDouble("car.engine.maxSpeedBackward", parent?.maxSpeedBackward, 0.3)
    val engineSound: SoundClip = getSoundClip("car.engine.sound", parent?.engineSound)
    val engineSoundPitchRange: ClosedFloatingPointRange<Float> =
        getFloatRange("car.engine.soundPitchRange", parent?.engineSoundPitchRange, 0f..2f, 0f..2f)
    val engineSoundVolumeRange: ClosedFloatingPointRange<Float> =
        getFloatRange("car.engine.soundVolumeRange", parent?.engineSoundVolumeRange, 1f..2f, 0f..Float.MAX_VALUE)

    val steerAccelerationInRadian: Double = getDouble("car.steer.acceleration", parent?.steerAccelerationInRadian?.toDegrees(), 3.0).toRadians()
    val steerRecoverInRadian: Double = getDouble("car.steer.recover", parent?.steerRecoverInRadian?.toDegrees(), 1.0).toRadians()
    val steerMaxAngleInRadian: Double = getDouble("car.steer.maxAngle", parent?.steerMaxAngleInRadian?.toDegrees(), 30.0).toRadians()
    val steerMaxAngleRangeInRadian = -steerMaxAngleInRadian .. steerMaxAngleInRadian

    val gravityFactor: Double = getDouble("car.physics.gravity", parent?.gravityFactor, 0.08)
    val floatOnWater: Boolean = getBoolean("car.physics.canFloatOnWater", parent?.floatOnWater, false)
        .also { floatOnWater ->
            if(floatOnWater && models.count { it.value.isKinematicEntity } != 1) {
                VehicleEngine.logWarn("car ${key}가 물에 뜰 수 있으나 kinematicEntity 갯수가 1이 아님")
            }
        }
    val boatWaterFriction: Double = getDouble("car.physics.waterFriction", parent?.boatWaterFriction, 1.0)
    val boatLandFrictionMultiplier: Double = getDouble("car.physics.landFrictionMultiplier", parent?.boatLandFrictionMultiplier, 1.0)

    override fun spawn(location: Location, decoration: Boolean) = CarEntity(this, location, decoration)

}