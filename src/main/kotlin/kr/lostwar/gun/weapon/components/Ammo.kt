package kr.lostwar.gun.weapon.components

import kr.lostwar.gun.weapon.WeaponComponent
import kr.lostwar.gun.weapon.WeaponType
import kr.lostwar.util.SoundInfo
import org.bukkit.configuration.ConfigurationSection

class Ammo(
    config: ConfigurationSection?,
    weapon: WeaponType,
    parent: Ammo?,
) : WeaponComponent(config, weapon, parent) {

    val amount: Int = getInt("amount", parent?.amount, 0)
    val startAmount: Int = getInt("startAmount", parent?.startAmount, amount)

    val canReload: Boolean = getBoolean("reload.enable", parent?.canReload, true)
    val reloadDuration: Int = getInt("reload.duration", parent?.reloadDuration, 1)
    val reloadEmptyAmmoDelay: Int = getInt("reload.emptyAmmoDelay", parent?.reloadEmptyAmmoDelay, 5)
    val reloadIndividually: Boolean = getBoolean("reload.reloadIndividually", parent?.reloadIndividually, false)
    val reloadIndividuallyWhenRemainingBullets: Boolean = getBoolean("reload.reloadIndividuallyWhenRemainingBullets", parent?.reloadIndividuallyWhenRemainingBullets, false)

    val reloadSound: List<SoundInfo> = getSounds("reload.reloadSound", parent?.reloadSound)
    // TODO 아이템 탄약 기능 추가?


}