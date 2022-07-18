package kr.lostwar.vehicle.event

import kr.lostwar.vehicle.core.VehicleEntity
import org.bukkit.entity.LivingEntity
import org.bukkit.event.HandlerList

class VehicleExplodeDamageEvent(
    vehicleEntity: VehicleEntity<*>,
    val victim: LivingEntity,
    var damage: Double,
    val wasPassenger: Boolean,
    val damageEvent: VehicleEntityDamageEvent?,
) : VehicleEvent(vehicleEntity), Kancellable {

    override var eventCancelled = false

    companion object {
        @JvmStatic val handlerList = HandlerList()
    }
    override fun getHandlers(): HandlerList = handlerList
}