package kr.lostwar.gun.weapon.event

import kr.lostwar.gun.weapon.WeaponPlayer
import org.bukkit.event.Cancellable

class WeaponTriggerEvent(
    player: WeaponPlayer,
) : WeaponPlayerEvent(player), Cancellable {
    private var cancel = false
    override fun isCancelled(): Boolean = cancel
    override fun setCancelled(value: Boolean) {
        cancel = value
    }
}