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
    val engineSoundPitchRange: ClosedFloatingPointRange<Float> = get("car.engine.soundPitchRange", parent?.engineSoundPitchRange, 0f..2f) { key ->
        val raw = getString(key) ?: return@get null
        val split = raw.split("..").map { it.trim() }
        if(split.size != 2) {
            return@get VehicleEngine.logErrorNull("cannot parse range: ${raw}")
        }
        val min = split[0].toFloatOrNull()
            ?: return@get VehicleEngine.logErrorNull("cannot parse range: ${raw} (invalid minimum value: ${split[0]})")
        val max = split[1].toFloatOrNull()
            ?: return@get VehicleEngine.logErrorNull("cannot parse range: ${raw} (invalid maximum value: ${split[1]})")
        Math.min(min, max).coerceAtLeast(0f) .. Math.max(min, max).coerceAtMost(2f)
    }!!

    val steerAccelerationInRadian: Double = getDouble("car.steer.acceleration", parent?.steerAccelerationInRadian?.toDegrees(), 3.0).toRadians()
    val steerRecoverInRadian: Double = getDouble("car.steer.recover", parent?.steerRecoverInRadian?.toDegrees(), 1.0).toRadians()
    val steerMaxAngleInRadian: Double = getDouble("car.steer.maxAngle", parent?.steerMaxAngleInRadian?.toDegrees(), 30.0).toRadians()
    val steerMaxAngleRangeInRadian = -steerMaxAngleInRadian .. steerMaxAngleInRadian

    val gravityFactor: Double = getDouble("car.physics.gravity", parent?.gravityFactor, 0.08)

    override fun spawn(location: Location, decoration: Boolean) = CarEntity(this, location, decoration)

}