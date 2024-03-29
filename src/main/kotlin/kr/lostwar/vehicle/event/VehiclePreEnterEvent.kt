package kr.lostwar.vehicle.event

import kr.lostwar.vehicle.core.VehicleEntity
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class VehiclePreEnterEvent(
    vehicleEntity: VehicleEntity<*>,
    val player: Player,
) : VehicleEvent(vehicleEntity), Kancellable {

    override var eventCancelled: Boolean = false

    companion object {
        @JvmStatic val handlerList = HandlerList()
    }
    override fun getHandlers(): HandlerList = handlerList
}