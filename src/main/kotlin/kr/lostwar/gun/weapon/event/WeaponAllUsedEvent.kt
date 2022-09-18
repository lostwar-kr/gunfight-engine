package kr.lostwar.gun.weapon.event

import kr.lostwar.gun.weapon.WeaponPlayer
import org.bukkit.event.HandlerList

class WeaponAllUsedEvent(
    player: WeaponPlayer,
) : WeaponPlayerEvent(player) {

    override fun getHandlers() = handlerList
    companion object {
        @JvmStatic val handlerList = HandlerList()
    }
}