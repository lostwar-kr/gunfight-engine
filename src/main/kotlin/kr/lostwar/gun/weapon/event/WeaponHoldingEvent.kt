package kr.lostwar.gun.weapon.event

import kr.lostwar.gun.weapon.Weapon
import kr.lostwar.gun.weapon.WeaponPlayer
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.inventory.ItemStack

class WeaponStartHoldingEvent(
    val player: WeaponPlayer,
    val oldWeapon: Weapon?,
    val oldItem: ItemStack?,
    val newWeapon: Weapon,
    val newItem: ItemStack,
) : Event() {

    companion object {
        private val handlers = HandlerList()
    }
    override fun getHandlers(): HandlerList {
        return Companion.handlers
    }

    fun getHandlerList(): HandlerList {
        return Companion.handlers
    }
}
class WeaponEndHoldingEvent(
    val player: WeaponPlayer,
    val oldWeapon: Weapon,
    val oldItem: ItemStack?,
    val newWeapon: Weapon?,
    val newItem: ItemStack?
) : Event() {

    companion object {
        private val handlers = HandlerList()
    }
    override fun getHandlers(): HandlerList {
        return Companion.handlers
    }

    fun getHandlerList(): HandlerList {
        return Companion.handlers
    }
}