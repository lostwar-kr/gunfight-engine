package kr.lostwar.gun.weapon

import com.destroystokyo.paper.event.server.ServerTickEndEvent
import kr.lostwar.gun.weapon.WeaponPlayer.Companion.onInteract
import kr.lostwar.gun.weapon.event.WeaponEndHoldingEvent
import kr.lostwar.gun.weapon.event.WeaponStartHoldingEvent
import net.kyori.adventure.text.Component
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerArmorStandManipulateEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import java.util.UUID

class WeaponPlayer(
    player: Player
) : WeaponHolder {
    var player = player.also { byUUID[it.uniqueId] = this }; private set
    override var weapon: Weapon? = null
        set(value) {
            field?.holder = null
            field = value
            value?.holder = this
        }
    var weaponItem: ItemStack? = null

    companion object : Listener {
        private val byUUID = HashMap<UUID, WeaponPlayer>()

        operator fun get(player: Player): WeaponPlayer {
            return byUUID[player.uniqueId]?.also { if(it.player != player) it.player = player } ?: return WeaponPlayer(player)
        }

        @EventHandler
        fun ServerTickEndEvent.onTickEnd() {
            byUUID.forEach { (_, player) -> player.tick() }
        }

        @EventHandler
        fun PlayerJoinEvent.onJoin() {
            val player = WeaponPlayer[player]
        }

        @EventHandler
        fun PlayerQuitEvent.onQuit() {
            byUUID.remove(player.uniqueId)
        }

        private val emptyItem = ItemStack(Material.AIR)
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        fun PlayerItemHeldEvent.onItemHeld() {
//            GunEngine.log("onItemHeld(${previousSlot} to ${newSlot})")
            val weaponPlayer = WeaponPlayer[player]
            weaponPlayer.updateCurrentWeapon(player.inventory.getItem(newSlot) ?: emptyItem)
        }


        @EventHandler
        fun PlayerInteractEvent.onInteract() {
            if(action == Action.PHYSICAL) return

            val player = player
            val weaponPlayer = WeaponPlayer[player]
            weaponPlayer.weapon?.type?.callEvent(weaponPlayer, this)
        }
        @EventHandler
        fun PlayerInteractEntityEvent.onInteractEntity() {
            val player = player
            val weaponPlayer = WeaponPlayer[player]
            weaponPlayer.weapon?.type?.callEvent(weaponPlayer, this)
        }
        @EventHandler
        fun PlayerInteractAtEntityEvent.onInteractEntity() {
            val player = player
            val weaponPlayer = WeaponPlayer[player]
            weaponPlayer.weapon?.type?.callEvent(weaponPlayer, this)
        }
        @EventHandler
        fun PlayerArmorStandManipulateEvent.onInteractEntity() {
            val player = player
            val weaponPlayer = WeaponPlayer[player]
            weaponPlayer.weapon?.type?.callEvent(weaponPlayer, this)
        }
    }

    private fun tick() {
//        updateCurrentWeapon()
        weapon?.tick()
        player.sendActionBar(Component.text("weapon: $weapon"))
    }

    private fun updateCurrentWeapon(newItem: ItemStack) {
        val oldWeapon = weapon
        val oldWeaponType = oldWeapon?.type
        val pair = WeaponType.of(newItem)
        val newWeaponType = pair?.first
        val newWeaponId = pair?.second
        // 무기 종류가 다르거나, 종류는 같은데 ID가 다른 경우
//        GunEngine.log("oldWeapon: ${oldWeapon}")
//        GunEngine.log("newWeapon: ${newWeaponType}:${newWeaponId}")
        if(oldWeaponType != newWeaponType || oldWeapon?.id != newWeaponId) {
            val newWeapon = Weapon.takeOut(newItem)
            this.weapon = newWeapon
            onChangeWeapon(oldWeapon, newWeapon, newItem)
//            player.colorMessage("changed weapon from &8${oldWeapon}&r to &e${newWeapon}")
        }
    }

    private fun onChangeWeapon(old: Weapon?, new: Weapon?, newItem: ItemStack) {
        if(old == new) return

        // 기존에 들고 있던 무기가 존재할 경우
        val oldItem = weaponItem
        if(old != null) {
            old.type.callEvent(this, WeaponEndHoldingEvent(this, old, oldItem, new, newItem))
            weaponItem = null
        }
        // 새로 드는 무기가 없을 경우
        if(new == null) {

        }
        // 새로 드는 무기가 있을 경우
        else{
            new.type.callEvent(this, WeaponStartHoldingEvent(this, old, oldItem, new, newItem))
            weaponItem = newItem
        }
    }
}