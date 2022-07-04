package kr.lostwar.gun.weapon

import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.EventPriority

class WeaponPlayerEventListener<T : Event>(
    val clazz: Class<T>,
    val priority: EventPriority = EventPriority.NORMAL,
    val ignoreCancelled: Boolean = false,
    val callEvent: WeaponPlayer.(event: T) -> Unit
) {
    companion object {
        val notCancelled = object : Cancellable {
            override fun setCancelled(value: Boolean) {}
            override fun isCancelled(): Boolean = false
        }
    }
}