package kr.lostwar.util

import kr.lostwar.GunfightEngine.Companion.plugin
import kr.lostwar.gun.GunEngine
import kr.lostwar.gun.weapon.WeaponPlayer
import kr.lostwar.gun.weapon.WeaponType
import kr.lostwar.util.item.ItemBuilder
import kr.lostwar.util.item.ItemData
import kr.lostwar.util.item.ItemUtil.applyMeta
import kr.lostwar.util.nms.PacketUtil.sendEquipmentSelf
import kr.lostwar.util.nms.PacketUtil.sendSlotSelf
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.LeatherArmorMeta
import org.bukkit.scheduler.BukkitRunnable

data class AnimationFrame(
    val item: ItemData?,
    val slot: EquipmentSlot,
    val delay: Int,
    val cooldown: Int? = null,
    val offset: Int = 0,
    val noself: Boolean = false,
) {

    val isCooldownAnimation = cooldown != null && item?.type !in colorable
    val isObjAnimation = cooldown != null && item?.type in colorable

    companion object {
        private val colorable = hashSetOf(
            Material.LEATHER_HELMET,
            Material.LEATHER_CHESTPLATE,
            Material.LEATHER_LEGGINGS,
            Material.LEATHER_BOOTS,
            Material.LEATHER_HORSE_ARMOR,
        )
        private val equipmentSlotByName = EquipmentSlot.values().toList().associateBy { it.name }
        private val dummyKey = "DUMMY"
        fun parse(raw: String?): AnimationFrame? {
            if(raw == null) return null
            val grandSplit = raw.split(';').map { it.trim() }
            if(grandSplit.isEmpty()) {
                return GunEngine.logErrorNull("cannot parse animation: ${raw} (empty)")
            }
            val primary = grandSplit[0]
            val additional = if(grandSplit.size >= 2) grandSplit[1] else ""
            val split = primary.split('-').map { it.trim() }
            if(split.size >= 2 && split[0] == dummyKey){
                val delay = split[1].toIntOrNull() ?: 0
                return AnimationFrame(null, EquipmentSlot.HAND, delay)
            }
            if(split.size < 3) return GunEngine.logErrorNull("cannot parse animation: ${raw} (not enough arguments size)")

            val material = Material.getMaterial(split[0].uppercase())
                ?: return GunEngine.logErrorNull("cannot parse animation: ${raw} (invalid material ${split[0]})")
            val slot = equipmentSlotByName[split[1].uppercase()]
                ?: return GunEngine.logErrorNull("cannot parse animation: ${raw} (invalid slot ${split[1]})")
            val data = split[2].toIntOrNull()
                ?: return GunEngine.logErrorNull("cannot parse animation: ${raw} (invalid data ${split[2]})")
            val delay = split[3].toIntOrNull()
                ?: return GunEngine.logErrorNull("cannot parse animation: ${raw} (invalid delay ${split[3]})")
            val cooldown = if(split.size <= 4) null
            else split[4].toIntOrNull()
                ?: return GunEngine.logErrorNull("cannot parse animation: ${raw} (invalid cooldown(or duration) ${split[4]})")
            val offset = if(split.size <= 5) 0
            else split[5].toIntOrNull()
                ?: return GunEngine.logErrorNull("cannot parse animation: ${raw} (invalid offset ${split[5]})")

            val noself = additional.contains("noself")

            return AnimationFrame(ItemData(material, data), slot, delay, cooldown, offset, noself)
        }


    }

    private fun getItemStack(weaponPlayer: WeaponPlayer, weaponType: WeaponType, needStore: Boolean = false): ItemBuilder {
        val itemStack = weaponType.item.itemStack.materialAndData(item!!)
        if(needStore) {
            weaponPlayer.weapon!!.storeTo(itemStack)
        }
        if(isObjAnimation) {
            itemStack.applyMeta<ItemBuilder, LeatherArmorMeta> {
                val color = if(offset >= 8388608) {
                    offset
                }else{
                    val gameTime = weaponPlayer.player.world.gameTime
                    ((gameTime % 24000L - offset) % cooldown!!).toInt()
                }
                setColor(Color.fromRGB(color))
            }
        }
        return itemStack
    }
    private fun Player.sendItem(itemStack: ItemStack, heldItemSlot: Int) {
        if(slot == EquipmentSlot.HAND) {
            if(noself) {
                inventory.setItem(heldItemSlot, itemStack)
            }else{
                sendSlotSelf(heldItemSlot, itemStack)
                sendEquipmentSelf(itemStack, slot)
            }
        }else{
            if(noself) {
                inventory.setItem(slot, itemStack)
            }else{
                sendEquipmentSelf(itemStack, slot)
            }
        }
    }
    fun play(weaponPlayer: WeaponPlayer, weaponType: WeaponType, heldItemSlot: Int): Material? {
        if(item == null) return null
        val player = weaponPlayer.player
//        console("play animation ${this}")
        val itemStack = getItemStack(weaponPlayer, weaponType, noself)
        if(isCooldownAnimation) {
            val cooldown = cooldown!!
            player.setCooldown(item.material, cooldown)
            if(cooldown > 2)
                recover(weaponPlayer, weaponType, heldItemSlot = heldItemSlot) // 강제 recover 예약
        }
        player.sendItem(itemStack, heldItemSlot)
        weaponPlayer.lastAnimationFrame = this
        return item.material
    }

    fun recover(weaponPlayer: WeaponPlayer, weaponType: WeaponType, after: Long = 2, heldItemSlot: Int = weaponPlayer.player.inventory.heldItemSlot) {
//        console("recovering animation ${this} ...")
        if(item == null) return
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val player = weaponPlayer.player
//            console("send $item")
            val itemStack = getItemStack(weaponPlayer, weaponType)
            player.sendItem(itemStack, heldItemSlot)
        }, after)
    }

    override fun toString(): String {
        return if(item == null) "$dummyKey-$delay"
        else "${item.material}-${slot}-${item.data}-${delay}"
    }



}

class AnimationClip(
    val frames: List<AnimationFrame>
) : List<AnimationFrame> by frames {

    val mostDelayed = frames.maxOfOrNull { it.delay } ?: 0
    val hasCooldownAnimation = frames.any { it.isCooldownAnimation }


    fun play(weaponPlayer: WeaponPlayer, weaponType: WeaponType, offset: Int = 0, loop: Boolean = false) {
//        GunEngine.log("AnimationClip::play(${frames.joinToString { it.item.data.toString() }})")
        if(isEmpty()) {
//            GunEngine.log("- isEmpty(), return")
            return
        }
        val player = weaponPlayer.player
        val weapon = weaponPlayer.weapon ?: return
        val heldItemSlot = weaponPlayer.player.inventory.heldItemSlot
//        GunEngine.log("- weapon: ${weapon}")
        weaponPlayer.stopAnimation(!hasCooldownAnimation)
        if(offset > 0) playAt(weaponPlayer, player, weaponType, offset, heldItemSlot)
        if(frames.size == 1 && frames[0].delay == 0) {
            frames[0].play(weaponPlayer, weaponType, heldItemSlot)?.let {
                weaponPlayer.registerCooldownMaterial(it)
            }
            return
        }
        weaponPlayer.playAnimation(object : BukkitRunnable() {
            var iterator = this@AnimationClip.iterator()
            var current = iterator.next()
            var currentDelay = current.delay
            var count = offset
            override fun run() {
                if(weapon != weaponPlayer.weapon || player.isDead || !player.isOnline) {
//                    GunEngine.log("- cancelled by invalidation")
                    cancel()
                    return
                }
                while(count > currentDelay) {
                    if(!iterator.hasNext()) {
                        if(!loop) {
//                            GunEngine.log("- stop by end of animation")
                            cancel()
                            return
                        }else{
                            iterator = this@AnimationClip.iterator()
                            count = 0
                        }
                    }
                    current = iterator.next()
                    currentDelay = current.delay
                }
                if(currentDelay == count) {
                    current.play(weaponPlayer, weaponType, heldItemSlot)?.let {
                        weaponPlayer.registerCooldownMaterial(it)
                    }
                }
                ++count

            }
        }.runTaskTimer(plugin, 0, 1))
    }

    private fun playAt(weaponPlayer: WeaponPlayer, player: Player, weaponType: WeaponType, offset: Int, heldItemSlot: Int) {
        var animation: AnimationFrame? = null
        for(frame in this) {
            if(frame.delay < offset) {
                animation = frame
            }else break
        }
        animation?.play(weaponPlayer, weaponType, heldItemSlot)?.let {
            weaponPlayer.registerCooldownMaterial(it)
        }
    }

    companion object {
        val emptyClip = AnimationClip(emptyList())
        fun ConfigurationSection.getAnimationClip(key: String, def: AnimationClip = emptyClip): AnimationClip
            = getAnimationClipOrNull(this, key) ?: def
        fun getAnimationClipOrNull(section: ConfigurationSection, key: String): AnimationClip? {
            if(section.isString(key)) {
                return AnimationClip(listOfNotNull(AnimationFrame.parse(section.getString(key))))
            }
            if(!section.isList(key)) return null
            val list = section.getStringList(key)
            return parse(list)
        }
        fun parse(list: List<String>): AnimationClip {
            return AnimationClip(list.mapNotNull { AnimationFrame.parse(it) })
        }
    }

}