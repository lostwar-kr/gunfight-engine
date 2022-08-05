package kr.lostwar.gun.weapon

import kr.lostwar.GunfightEngine.Companion.plugin
import org.bukkit.NamespacedKey
import org.bukkit.event.entity.EntityDamageEvent.DamageCause

object Constants {
    val weaponContainerKey = NamespacedKey("gunfightengine", "fmj.weapon")
    val weaponDamageCause = DamageCause.MELTING // used on snow golem
}