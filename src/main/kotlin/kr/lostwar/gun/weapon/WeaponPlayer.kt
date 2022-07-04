package kr.lostwar.gun.weapon

import com.destroystokyo.paper.event.server.ServerTickEndEvent
import kr.lostwar.gun.weapon.event.WeaponEndHoldingEvent
import kr.lostwar.gun.weapon.event.WeaponStartHoldingEvent
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.*
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

class WeaponPlayer(
    player: Player
) : WeaponHolder {
    var player = player.also { byUUID[it.uniqueId] = this }; private set
    override var weapon: Weapon? = null
        set(value) {
            field?.player = null
            field = value
            value?.player = this
        }
    var weaponItem: ItemStack? = null

    private val playingAnimations = ArrayList<BukkitTask>()
    private val playingSounds = ArrayList<BukkitTask>()

    fun playAnimation(animationTask: BukkitTask?) = animationTask?.let { playingAnimations.add(it) }
    fun playSound(soundTask: BukkitTask?) = soundTask?.let { playingSounds.add(it) }


    fun stopAnimation() {
        for(task in playingAnimations) {
            if(!task.isCancelled) task.cancel()
        }
        playingAnimations.clear()
    }
    fun stopSound() {
        for(task in playingSounds) {
            if(!task.isCancelled) task.cancel()
        }
        playingSounds.clear()
    }

    companion object : Listener {
        private val byUUID = HashMap<UUID, WeaponPlayer>()

        val Player.weaponPlayer: WeaponPlayer; get() = get(this)
        operator fun get(player: Player): WeaponPlayer {
            return byUUID[player.uniqueId]
                ?. also { if(it.player != player) it.player = player }
                ?: return WeaponPlayer(player)
        }

        @EventHandler
        fun ServerTickEndEvent.onTickEnd() {
            byUUID.forEach { (_, player) -> player.tick() }
        }

        @EventHandler
        fun PlayerJoinEvent.onJoin() {
            val player = player.weaponPlayer
        }

        @EventHandler
        fun PlayerQuitEvent.onQuit() {
            byUUID.remove(player.uniqueId)
        }

        private val emptyItem = ItemStack(Material.AIR)
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        fun PlayerItemHeldEvent.onItemHeld() {
//            GunEngine.log("onItemHeld(${previousSlot} to ${newSlot})")
            val weaponPlayer = player.weaponPlayer
            weaponPlayer.updateCurrentWeapon(player.inventory.getItem(newSlot) ?: emptyItem)
        }
        @EventHandler(priority = EventPriority.MONITOR)
        fun PlayerInteractEvent.onInteract() = callEventOnWeaponPlayer(player)
        @EventHandler
        fun PlayerInteractEntityEvent.onInteractEntity() = callEventOnWeaponPlayer(player)
        @EventHandler
        fun PlayerInteractAtEntityEvent.onInteractEntity() = callEventOnWeaponPlayer(player)
        @EventHandler
        fun PlayerArmorStandManipulateEvent.onInteractEntity() = callEventOnWeaponPlayer(player)
        @EventHandler
        fun PlayerDropItemEvent.onDrop() = callEventOnWeaponPlayer(player)

        private inline fun <reified T : Event> T.callEventOnWeaponPlayer(player: Player) {
            val weaponPlayer = player.weaponPlayer
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
        val newWeapon = Weapon.takeOut(newItem)
//        GunEngine.log("oldWeapon: ${oldWeapon}")
//        GunEngine.log("newWeapon: ${newWeaponType}:${newWeaponId}")
        if(oldWeapon != newWeapon) {
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
            stopAnimation()
            stopSound()
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