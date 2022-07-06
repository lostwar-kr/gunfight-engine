package kr.lostwar.gun.weapon

import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.EventPriority

class WeaponPlayerEventListener<T : Event>(
    val clazz: Class<T>,
    val priority: Int = EventPriority.NORMAL.slot,
    val ignoreCancelled: Boolean = false,
    val callEvent: WeaponPlayer.(event: T) -> Unit
) {
    constructor(
        clazz: Class<T>,
        priority: EventPriority,
        ignoreCancelled: Boolean = false,
        callEvent: WeaponPlayer.(event: T) -> Unit
    ) : this(clazz, priority = priority.slot, ignoreCancelled = ignoreCancelled, callEvent)

    companion object {
        val notCancelled = object : Cancellable {
            override fun setCancelled(value: Boolean) {}
            override fun isCancelled(): Boolean = false
        }
    }
}