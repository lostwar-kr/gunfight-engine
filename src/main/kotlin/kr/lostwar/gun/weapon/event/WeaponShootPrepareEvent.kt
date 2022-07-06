package kr.lostwar.gun.weapon.event

import kr.lostwar.gun.weapon.WeaponPlayer
import kr.lostwar.gun.weapon.actions.ShootAction
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.HandlerList

typealias RaycastPredicate = (entity: LivingEntity, shooter: Player) -> Boolean
class WeaponShootPrepareEvent(
    player: WeaponPlayer,
    val action: ShootAction,
    var ray: Location,
) : WeaponPlayerEvent(player) {

    var filter: RaycastPredicate = { entity, player -> true }

    override fun getHandlers() = handlerList
    companion object {
        @JvmStatic val handlerList = HandlerList()
    }
}