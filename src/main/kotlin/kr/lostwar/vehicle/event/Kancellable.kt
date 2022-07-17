package kr.lostwar.vehicle.event

import org.bukkit.event.Cancellable

interface Kancellable : Cancellable {

    var eventCancelled: Boolean
    override fun isCancelled() = eventCancelled
    override fun setCancelled(value: Boolean) { eventCancelled = value }
}