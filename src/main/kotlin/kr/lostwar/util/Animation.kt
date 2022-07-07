package kr.lostwar.util

import kr.lostwar.GunfightEngine.Companion.plugin
import kr.lostwar.gun.GunEngine
import kr.lostwar.gun.weapon.WeaponPlayer
import kr.lostwar.gun.weapon.WeaponPlayer.Companion.weaponPlayer
import kr.lostwar.gun.weapon.WeaponType
import kr.lostwar.util.item.ItemData
import kr.lostwar.util.nms.PacketUtil.sendEquipmentSelf
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.scheduler.BukkitRunnable

data class AnimationFrame(
    val item: ItemData,
    val slot: EquipmentSlot,
    val delay: Int,
) {

    companion object {
        private val equipmentSlotByName = EquipmentSlot.values().toList().associateBy { it.name }

        fun parse(raw: String?): AnimationFrame? {
            if(raw == null) return null
            val split = raw.split('-').map { it.trim() }
            if(split.size < 3) return logErrorNull("cannot parse animation: ${raw} (not enough arguments size)")

            val material = Material.getMaterial(split[0].uppercase())
                ?: return logErrorNull("cannot parse animation: ${raw} (invalid material ${split[0]})")
            val slot = equipmentSlotByName[split[1].uppercase()]
                ?: return logErrorNull("cannot parse animation: ${raw} (invalid slot ${split[1]})")
            val data = split[2].toIntOrNull()
                ?: return logErrorNull("cannot parse animation: ${raw} (invalid data ${split[2]})")
            val delay = split[3].toIntOrNull() ?: 0

            return AnimationFrame(ItemData(material, data), slot, delay)
        }


    }

    fun play(player: Player, weaponType: WeaponType) {
        player.sendEquipmentSelf(weaponType.item.itemStack.materialAndData(item), slot)
    }

    override fun toString(): String {
        return "${item.material}-${slot}-${item.data}-${delay}"
    }



}

class AnimationClip(
    val frames: List<AnimationFrame>
) : List<AnimationFrame> by frames {

    val mostDelayed = frames.maxOfOrNull { it.delay } ?: 0


    fun play(weaponPlayer: WeaponPlayer, weaponType: WeaponType, offset: Int = 0, loop: Boolean = false) {
//        GunEngine.log("AnimationClip::play(${frames.joinToString { it.item.data.toString() }})")
        if(isEmpty()) {
//            GunEngine.log("- isEmpty(), return")
            return
        }
        val player = weaponPlayer.player
        val weapon = weaponPlayer.weapon ?: return
//        GunEngine.log("- weapon: ${weapon}")
        weaponPlayer.stopAnimation()
        if(offset > 0) playAt(player, weaponType, offset)
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
                    current.play(player, weaponType)
                }
                ++count

            }
        }.runTaskTimer(plugin, 0, 1))
    }

    private fun playAt(player: Player, weaponType: WeaponType, offset: Int) {
        var animation: AnimationFrame? = null
        for(frame in this) {
            if(frame.delay < offset) {
                animation = frame
            }else break
        }
        animation?.play(player, weaponType)
    }

    companion object {
        val emptyClip = AnimationClip(emptyList())
        fun ConfigurationSection.getAnimationClip(key: String, def: AnimationClip = emptyClip): AnimationClip {
            if(!isList(key)) return def
            val list = getStringList(key)
            return parse(list)
        }
        fun parse(list: List<String>): AnimationClip {
            return AnimationClip(list.mapNotNull { AnimationFrame.parse(it) })
        }
    }

}