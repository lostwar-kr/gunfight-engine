package kr.lostwar.vehicle.event

import kr.lostwar.vehicle.core.VehicleEntity
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

abstract class VehicleEvent(
    val vehicleEntity: VehicleEntity
) : Event() {

    val base = vehicleEntity.base

    companion object {
        @JvmStatic val handlerList = HandlerList()
    }
    override fun getHandlers(): HandlerList = handlerList
}