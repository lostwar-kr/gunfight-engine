package kr.lostwar.gun.weapon

import org.bukkit.GameMode
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerInteractEvent

object WeaponListener : Listener {
/*
    // 무기 데미지는 특별한 cause를 사용하여 필터링
    @EventHandler(priority = EventPriority.LOWEST)
    fun EntityDamageEvent.onDamage() {
        if(cause == Constants.weaponDamageCause) {
            isCancelled = true
        }
    }
*/
}