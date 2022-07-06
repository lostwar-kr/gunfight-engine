package kr.lostwar.gun

import kr.lostwar.Engine
import kr.lostwar.gun.weapon.WeaponType
import kr.lostwar.gun.weapon.WeaponListener
import kr.lostwar.gun.weapon.WeaponPlayer
import org.bukkit.command.Command
import org.bukkit.event.Listener

object GunEngine : Engine("full-metal-jacket") {
    override val listeners: List<Listener> = listOf(
        WeaponListener,
        WeaponPlayer.Companion,
    )
    override val commands: List<Command> = listOf(
        WeaponCommand,
    )
    override fun onLoad(reload: Boolean) {
        if(reload) {
            log("무기 불러오기 ...")
            loadWeapons()
        }
    }

    override fun onLateInit() {
        log("무기 불러오기 ...")
        loadWeapons()
    }

    fun loadWeapons() {
        WeaponPlayer.byUUID.values.forEach {
            val weapon = it.weapon ?: return
            weapon.storeTo(it.player.inventory.itemInMainHand)
        }

        WeaponType.load()

        WeaponPlayer.byUUID.values.forEach {
            val weapon = it.weapon ?: return
            weapon.type = WeaponType[weapon.type.key] ?: run {
                it.updateCurrentWeapon()
                return@forEach
            }
        }
    }

}