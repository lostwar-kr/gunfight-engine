package kr.lostwar.vehicle.core.parachute

import kr.lostwar.util.Config
import kr.lostwar.util.CustomMaterialSet
import kr.lostwar.util.SoundClip
import kr.lostwar.util.math.toDegrees
import kr.lostwar.util.math.toRadians
import kr.lostwar.vehicle.core.VehicleInfo
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerToggleSneakEvent
import kotlin.math.max
import kotlin.math.min

open class ParachuteInfo(
    key: String,
    config: ConfigurationSection,
    configFile: Config,
    parent: ParachuteInfo?
) : VehicleInfo(key, config, configFile, parent) {

    val acceleration: Double = getDouble("parachute.engine.acceleration", parent?.acceleration, 0.2)
    val brake: Double = getDouble("parachute.engine.brake", parent?.brake, 0.2)
    val minSpeed: Double = getDouble("parachute.engine.minSpeed", parent?.minSpeed, 0.3)
    val maxSpeed: Double = getDouble("parachute.engine.maxSpeed", parent?.maxSpeed, 1.0)
    val speedRange = minSpeed .. maxSpeed
    val minDownSpeed: Double = getDouble("parachute.engine.minDownSpeed", parent?.minDownSpeed, 0.1)

    val pitchRotateSpeedInRadianPerTick: Double = getDouble("parachute.engine.pitchRotateSpeed", parent?.pitchRotateSpeedInRadianPerTick?.toDegrees(), 1.0).toRadians()
    val minPitchInRadian: Double = getDouble("parachute.engine.minPitchAngle", parent?.minPitchInRadian?.toDegrees(), 0.0).toRadians()
    val maxPitchInRadian: Double = getDouble("parachute.engine.maxPitchAngle", parent?.maxPitchInRadian?.toDegrees(), 45.0).toRadians()
    val pitchRange = min(maxPitchInRadian, minPitchInRadian) .. max(maxPitchInRadian, minPitchInRadian)

    val engineSound: SoundClip = getSoundClip("parachute.engine.sound", parent?.engineSound)
    val engineSoundPitchRange: ClosedFloatingPointRange<Float> =
        getFloatRange("parachute.engine.soundPitchRange", parent?.engineSoundPitchRange, 0f..2f, 0f..2f)
    val engineSoundVolumeRange: ClosedFloatingPointRange<Float> =
        getFloatRange("parachute.engine.soundVolumeRange", parent?.engineSoundVolumeRange, 1f..2f, 0f..Float.MAX_VALUE)

    val steerAccelerationInRadian: Double = getDouble("parachute.steer.acceleration", parent?.steerAccelerationInRadian?.toDegrees(), 3.0).toRadians()
    val steerRecoverInRadian: Double = getDouble("parachute.steer.recover", parent?.steerRecoverInRadian?.toDegrees(), 1.0).toRadians()
    val steerMaxAngleInRadian: Double = getDouble("parachute.steer.maxAngle", parent?.steerMaxAngleInRadian?.toDegrees(), 30.0).toRadians()
    val steerMaxAngleRangeInRadian = -steerMaxAngleInRadian .. steerMaxAngleInRadian
    // roll이 반대로 적용되어 , , 부호를 음수로 적용함
    val steerRollAngleInRadian: Double = -getDouble("parachute.steer.roll.angle", parent?.steerRollAngleInRadian?.toDegrees(), 5.0).toRadians()
    val steerRollLerpSpeed: Double = getDouble("parachute.steer.roll.lerpSpeed", parent?.steerRollLerpSpeed, 1.0)

    val disableExitTicks: Int = getInt("parachute.extra.disableExitTicks", parent?.disableExitTicks, 10)

    override val deathExplosionTryStretchParachute: Boolean = false

    init {
        if(primaryParachute == null) {
            primaryParachute = this
        }
    }
    override fun spawn(location: Location, decoration: Boolean) = ParachuteEntity(this, location, decoration)

    fun stretch(player: Player) {
        val spawnLocation = player.location
        spawnLocation.pitch = 0f

        val entity = spawn(spawnLocation)
        entity.ride(player, true)
    }

    companion object : Listener {
        private val horizontalCheckRadius = 1
        private val horizontalCheckRange = -horizontalCheckRadius .. horizontalCheckRadius
        private val verticalCheckHeight = 5
        private val verticalCheckRange = 0 downTo -verticalCheckHeight
        private val verticalOnlyCheckHeight = 10
        private val verticalOnlyCheckRange = -verticalCheckHeight downTo -verticalOnlyCheckHeight
        private val Block.isAir; get() = CustomMaterialSet.completelyPassable.contains(type)
        @EventHandler
        fun PlayerToggleSneakEvent.onSneak() {
            if(!isSneaking) return
            val player = player
            tryStretchParachute(player)
        }

        fun tryStretchParachute(player: Player, parachute: ParachuteInfo? = primaryParachute): Boolean {
            if(parachute == null) return false
            if(player.gameMode == GameMode.SPECTATOR || player.gameMode == GameMode.CREATIVE) return false
            if(player.vehicle != null) return false

            val standing = player.location.block
            if(!standing.isAir) return false
            for(dx in horizontalCheckRange) {
                for(dz in horizontalCheckRange) {
                    for(dy in verticalCheckRange) {
                        if(!standing.getRelative(dx, dy, dz).isAir){
                            return false
                        }
                    }
                }
            }
            for(dy in verticalOnlyCheckRange) {
                if(!standing.getRelative(0, dy, 0).isAir){
                    return false
                }
            }
            parachute.stretch(player)
            return true
        }

    }

}