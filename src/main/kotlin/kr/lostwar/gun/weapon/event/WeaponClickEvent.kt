package kr.lostwar.gun.weapon.event

import kr.lostwar.gun.weapon.WeaponPlayer
import org.bukkit.event.HandlerList
import org.bukkit.event.inventory.ClickType

class WeaponClickEvent(
    player: WeaponPlayer,
    val clickType: ClickType
) : WeaponPlayerEvent(player) {

    companion object {
        private val handlers = HandlerList()
        @JvmStatic fun getHandlerList() = Companion.handlers
    }
    override fun getHandlers(): HandlerList = Companion.handlers

}