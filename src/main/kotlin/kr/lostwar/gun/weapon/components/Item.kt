package kr.lostwar.gun.weapon.components

import kr.lostwar.gun.weapon.WeaponType
import kr.lostwar.gun.weapon.WeaponComponent
import kr.lostwar.util.item.ItemBuilder
import kr.lostwar.util.item.ItemData
import kr.lostwar.util.item.ItemUtil.applyItemMeta
import kr.lostwar.util.item.ItemUtil.applyMeta
import kr.lostwar.util.ui.text.StringUtil.colored
import kr.lostwar.util.ui.text.StringUtil.mapColored
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.CrossbowMeta

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
    }
    val crossbow: ItemBuilder = ItemBuilder(Material.CROSSBOW, crossbowData).applyMeta<ItemBuilder, CrossbowMeta> {
        addChargedProjectile(ItemStack(Material.ARROW))
    }

    companion object {
        val defaultItemType = ItemData(Material.STONE_PICKAXE, 1)
    }

}