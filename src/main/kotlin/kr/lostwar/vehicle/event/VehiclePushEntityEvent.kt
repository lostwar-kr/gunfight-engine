package kr.lostwar.vehicle.event

import kr.lostwar.vehicle.core.VehicleEntity
import org.bukkit.entity.LivingEntity
import org.bukkit.event.HandlerList

class VehiclePushEntityEvent(
    vehicleEntity: VehicleEntity<*>,
    val entity: LivingEntity,
    var damage: Double,
) : VehicleEvent(vehicleEntity), Kancellable {
    override var eventCancelled: Boolean = false
    companion object {
        @JvmStatic val handlerList = HandlerList()
    }
    override fun getHandlers(): HandlerList = handlerList
}