package kr.lostwar.vehicle.event

import kr.lostwar.vehicle.core.VehicleEntity
import kr.lostwar.vehicle.core.VehicleEntityDamage
import org.bukkit.event.HandlerList

class VehicleEntityDamageEvent(
    vehicleEntity: VehicleEntity<*>,
    val damageInfo: VehicleEntityDamage,
    val isDead: Boolean,
) : VehicleEvent(vehicleEntity), Kancellable {

    override var eventCancelled = false

    companion object {
        @JvmStatic val handlerList = HandlerList()
    }
    override fun getHandlers(): HandlerList = handlerList
}