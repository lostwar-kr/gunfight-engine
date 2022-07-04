package kr.lostwar.gun.weapon.event

import kr.lostwar.gun.weapon.Weapon
import kr.lostwar.gun.weapon.WeaponPlayer
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

open class WeaponPlayerEvent(
    val player: WeaponPlayer,
) : Event() {
    open val weapon; get() = player.weapon

    companion object {
        private val handlers = HandlerList()
        @JvmStatic fun getHandlerList() = Companion.handlers

        inline fun <reified T : WeaponPlayerEvent> T.callEventOnHoldingWeapon(): T {
            val weapon = weapon
                ?: error("WeaponPlayerEvent::callEventOnHoldingWeapon() for ${player.player.name} called but invalid WeaponPlayer::weapon detected")
            weapon.type.callEvent(player, this)
            return this
        }
    }
    override fun getHandlers(): HandlerList = Companion.handlers

}