package kr.lostwar.vehicle.event

import kr.lostwar.vehicle.core.VehicleEntity
import org.bukkit.entity.LivingEntity
import org.bukkit.event.HandlerList

class VehicleEntityDeathEvent(
    vehicleEntity: VehicleEntity<*>,
    val damageEvent: VehicleEntityDamageEvent?,
) : VehicleEvent(vehicleEntity) {

    companion object {
        @JvmStatic val handlerList = HandlerList()
    }
    override fun getHandlers(): HandlerList = handlerList
}