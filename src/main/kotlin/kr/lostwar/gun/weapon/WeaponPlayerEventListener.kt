package kr.lostwar.gun.weapon

import org.bukkit.event.Event

class WeaponPlayerEventListener<T : Event>(
    val clazz: Class<T>,
    val callEvent: WeaponPlayer.(event: T) -> Unit
) {
}