package kr.lostwar.gun.weapon.event

import kr.lostwar.gun.weapon.WeaponPlayer
import kr.lostwar.gun.weapon.components.Grenade
import kr.lostwar.vehicle.event.Kancellable
import org.bukkit.Location
import org.bukkit.entity.LivingEntity
import org.bukkit.event.HandlerList

class LandmineDetonationEvent(
    player: WeaponPlayer,
    val grenade: Grenade,
    val location: Location,
    val target: LivingEntity,
) : WeaponPlayerEvent(player), Kancellable {
    override var eventCancelled: Boolean = false

    companion object {
        @JvmStatic val handlerList = HandlerList()
    }
    override fun getHandlers(): HandlerList = handlerList
}