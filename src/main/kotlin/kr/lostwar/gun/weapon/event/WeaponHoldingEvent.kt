package kr.lostwar.gun.weapon.event

import kr.lostwar.gun.weapon.Weapon
import kr.lostwar.gun.weapon.WeaponPlayer
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.inventory.ItemStack

class WeaponStartHoldingEvent(
    player: WeaponPlayer,
    val oldWeapon: Weapon?,
    val oldItem: ItemStack?,
    val newWeapon: Weapon,
    val newItem: ItemStack,
) : WeaponPlayerEvent(player) {

    override val weapon: Weapon
        get() = newWeapon
    companion object {
        private val handlers = HandlerList()
        @JvmStatic fun getHandlerList() = Companion.handlers
    }
    override fun getHandlers(): HandlerList = Companion.handlers

}
class WeaponEndHoldingEvent(
    player: WeaponPlayer,
    val oldWeapon: Weapon,
    val oldItem: ItemStack?,
    val newWeapon: Weapon?,
    val newItem: ItemStack?
) : WeaponPlayerEvent(player) {
    override val weapon: Weapon
        get() = oldWeapon
    companion object {
        private val handlers = HandlerList()
        @JvmStatic fun getHandlerList() = Companion.handlers
    }
    override fun getHandlers(): HandlerList = Companion.handlers
}