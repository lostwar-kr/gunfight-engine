package kr.lostwar.vehicle.core

import kr.lostwar.vehicle.VehicleEngine
import org.bukkit.event.entity.EntityDamageEvent.DamageCause

object Constants {
    val vehicleEntityKey = "${VehicleEngine.name}_ve"

    val collisionDamageCause = DamageCause.FLY_INTO_WALL
    val vehicleExplosionDamageCause = DamageCause.DRAGON_BREATH
}