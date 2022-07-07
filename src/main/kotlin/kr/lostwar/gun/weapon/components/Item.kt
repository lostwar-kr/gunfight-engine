package kr.lostwar.gun.weapon.components

import kr.lostwar.gun.weapon.WeaponType
import kr.lostwar.gun.weapon.WeaponComponent
import kr.lostwar.gun.weapon.WeaponPlayerEventListener
import kr.lostwar.gun.weapon.event.WeaponEndHoldingEvent
import kr.lostwar.gun.weapon.event.WeaponStartHoldingEvent
import kr.lostwar.util.item.ItemBuilder
import kr.lostwar.util.item.ItemData
import kr.lostwar.util.item.ItemUtil.applyItemMeta
import kr.lostwar.util.item.ItemUtil.applyMeta
import kr.lostwar.util.ui.text.StringUtil.colored
import kr.lostwar.util.ui.text.StringUtil.mapColored
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.event.Event
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.CrossbowMeta
import org.bukkit.potion.PotionEffectType

class Item(
    config: ConfigurationSection?,
    weapon: WeaponType,
    parent: Item?,
) : WeaponComponent(config, weapon, parent, true) {

    enum class TextType {
        MINI_MESSAGE,
        LEGACY_STRING,
    }
    val textType: TextType = getEnumString("textType", parent?.textType, TextType.MINI_MESSAGE)
    val displayName: String? = getString("displayName", parent?.displayName)
    val lore: List<String> = getStringList("lore", parent?.lore, emptyList())
    val itemData: ItemData = getItemData("type", parent?.itemData, defaultItemType)!!

    val useAnimation: Boolean = getBoolean("useAnimation", parent?.useAnimation, true)
    val useCrossbowMotion: Boolean = getBoolean("useCrossbowMotion", parent?.useCrossbowMotion, false)
    val crossbowData: Int = getInt("crossbowData", itemData.data)


    val itemStack: ItemBuilder; get() = ItemBuilder(itemData).applyItemMeta {
        val displayName = this@Item.displayName
        val lore = this@Item.lore
        when(textType) {
            TextType.MINI_MESSAGE -> {
                val parser = MiniMessage.miniMessage()
                displayName(parser.deserializeOrNull(displayName))
                if(lore.isNotEmpty()) {
                    lore(lore.mapNotNull { parser.deserializeOrNull(it) }.takeIf { it.isNotEmpty() })
                }
            }
            TextType.LEGACY_STRING -> @Suppress("DEPRECATION") {
                setDisplayName(displayName?.colored())
                setLore(lore.mapColored())
            }
        }
        isUnbreakable = true
        addItemFlags(*ItemFlag.values())
    }
    val crossbow: ItemBuilder = ItemBuilder(Material.CROSSBOW, crossbowData).applyMeta<ItemBuilder, CrossbowMeta> {
        addChargedProjectile(ItemStack(Material.ARROW))
    }

    companion object {
        val defaultItemType = ItemData(Material.STONE_PICKAXE, 1)
    }

    private val slowDigging = PotionEffectType.SLOW_DIGGING
        .createEffect(Int.MAX_VALUE, 255)
        .withParticles(false).withIcon(false)
    private val fastDigging = PotionEffectType.FAST_DIGGING
        .createEffect(Int.MAX_VALUE, 10)
        .withParticles(false).withIcon(false)

    private val onStartHold = WeaponPlayerEventListener(WeaponStartHoldingEvent::class.java) { event ->
        val newWeapon = event.newWeapon
        if(newWeapon.type.item.useAnimation) {
            player.addPotionEffect(slowDigging)
            player.addPotionEffect(fastDigging)
        }
    }

    private val onEndHold = WeaponPlayerEventListener(WeaponEndHoldingEvent::class.java) { event ->
        val newWeapon = event.newWeapon
        if(newWeapon?.type?.item?.useAnimation == true) {
            return@WeaponPlayerEventListener
        }
        player.removePotionEffect(PotionEffectType.SLOW_DIGGING)
        player.removePotionEffect(PotionEffectType.FAST_DIGGING)
    }

    override val listeners: List<WeaponPlayerEventListener<out Event>> = listOf(
        onStartHold,
        onEndHold,
    )

}