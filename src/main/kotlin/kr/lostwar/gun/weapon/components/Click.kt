package kr.lostwar.gun.weapon.components

import kr.lostwar.gun.weapon.WeaponComponent
import kr.lostwar.gun.weapon.WeaponPlayer
import kr.lostwar.gun.weapon.WeaponPlayerEventListener
import kr.lostwar.gun.weapon.WeaponType
import org.bukkit.GameMode
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.event.Event
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.player.PlayerArmorStandManipulateEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent

class Click(
    config: ConfigurationSection?,
    weapon: WeaponType,
    parent: Click?,
) : WeaponComponent(config, weapon, parent) {

    val invert: Boolean = getBoolean("invert", parent?.invert, false)

    override val listeners: List<WeaponPlayerEventListener<out Event>> = listOf(
        WeaponPlayerEventListener(PlayerInteractEvent::class.java) { event ->
            if(player.gameMode == GameMode.SPECTATOR) return@WeaponPlayerEventListener
            val clickType = when(event.action) {
                Action.LEFT_CLICK_AIR, Action.LEFT_CLICK_BLOCK -> if(!invert) ClickType.LEFT else ClickType.RIGHT
                Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK -> if(!invert) ClickType.RIGHT else ClickType.LEFT
                else -> return@WeaponPlayerEventListener
            }
            onClick(clickType)
        },
        WeaponPlayerEventListener(PlayerInteractAtEntityEvent::class.java) {
            if(player.gameMode == GameMode.SPECTATOR) return@WeaponPlayerEventListener
            onClick(ClickType.RIGHT)
        },
        WeaponPlayerEventListener(PlayerInteractEntityEvent::class.java) {
            if(player.gameMode == GameMode.SPECTATOR) return@WeaponPlayerEventListener
            onClick(ClickType.RIGHT)
        },
        WeaponPlayerEventListener(PlayerArmorStandManipulateEvent::class.java) {
            if(player.gameMode == GameMode.SPECTATOR) return@WeaponPlayerEventListener
            onClick(ClickType.RIGHT)
        },
    )

    private fun WeaponPlayer.onClick(clickType: ClickType) {
        val weapon = weapon ?: return

    }
}