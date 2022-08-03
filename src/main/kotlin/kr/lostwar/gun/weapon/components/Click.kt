package kr.lostwar.gun.weapon.components

import kr.lostwar.gun.weapon.*
import kr.lostwar.gun.weapon.actions.DelayAction
import kr.lostwar.gun.weapon.event.WeaponClickEvent
import kr.lostwar.gun.weapon.event.WeaponPlayerEvent.Companion.callEventOnHoldingWeapon
import org.bukkit.GameMode
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.player.*
import java.util.*
import kotlin.collections.HashSet

class Click(
    config: ConfigurationSection?,
    weapon: WeaponType,
    parent: Click?,
) : WeaponComponent(config, weapon, parent, true) {

    val invert: Boolean = getBoolean("invert", parent?.invert, false)

    private val ignoreLeftClick = HashSet<UUID>()
    override val listeners: List<WeaponPlayerEventListener<out Event>> = listOf(
        // 무기 버릴 시 ArmSwing 발생해서 LEFT_CLICK 처리되는 버그 해결
        WeaponPlayerEventListener(PlayerDropItemEvent::class.java, EventPriority.MONITOR) { event ->
            val weapon = this.weapon ?: return@WeaponPlayerEventListener
            event.isCancelled = true
            ignoreLeftClick.add(weapon.id)
            weapon.addBackgroundAction(object : DelayAction(weapon, 1) {
                override fun onEnd() {
                    ignoreLeftClick.remove(weapon.id)
                }
            })
        },
        WeaponPlayerEventListener(PlayerInteractEvent::class.java) { event ->
            if(player.gameMode == GameMode.SPECTATOR) return@WeaponPlayerEventListener
            val rawClickType = when(event.action) {
                Action.LEFT_CLICK_AIR, Action.LEFT_CLICK_BLOCK -> ClickType.LEFT
                Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK -> ClickType.RIGHT
                else -> return@WeaponPlayerEventListener
            }

            if(rawClickType == ClickType.LEFT && this.weapon?.id in ignoreLeftClick) {
                return@WeaponPlayerEventListener
            }
            val clickType = if(!invert) rawClickType else when(rawClickType) {
                ClickType.LEFT -> ClickType.RIGHT
                ClickType.RIGHT -> ClickType.LEFT
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
        WeaponClickEvent(this, clickType).callEventOnHoldingWeapon()
    }
}