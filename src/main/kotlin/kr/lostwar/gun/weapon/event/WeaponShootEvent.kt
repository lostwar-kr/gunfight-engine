package kr.lostwar.gun.weapon.event

import kr.lostwar.gun.weapon.WeaponPlayer
import kr.lostwar.gun.weapon.actions.ShootAction
import org.bukkit.Location
import org.bukkit.event.HandlerList

class WeaponShootEvent(
    player: WeaponPlayer,
    val action: ShootAction,
    private val mutableRay: Location,
    val filter: RaycastPredicate,
) : WeaponPlayerEvent(player) {

    val shootRay; get() = mutableRay.clone()

    override fun getHandlers() = handlerList
    companion object {
        @JvmStatic val handlerList = HandlerList()
    }
}