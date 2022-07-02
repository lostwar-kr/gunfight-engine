package kr.lostwar.gun.weapon

import kr.lostwar.GunfightEngine.Companion.plugin
import org.bukkit.NamespacedKey

object Constants {
    val weaponKeyPrefix = "fmj.weapon."
    val weaponKey = NamespacedKey(plugin, weaponKeyPrefix+"key")
}