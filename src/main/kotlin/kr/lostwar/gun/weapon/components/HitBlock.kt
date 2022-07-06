package kr.lostwar.gun.weapon.components

import kr.lostwar.gun.weapon.WeaponComponent
import kr.lostwar.gun.weapon.WeaponPlayer
import kr.lostwar.gun.weapon.WeaponType
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.configuration.ConfigurationSection

class HitBlock(
    config: ConfigurationSection?,
    weapon: WeaponType,
    parent: HitBlock?,
) : WeaponComponent(config, weapon, parent, true) {

    private val interactionMap: Map<HitBlockInteraction, HitBlockResult> = get("", parent?.interactionMap) { _ ->
        val map = HashMap<HitBlockInteraction, HitBlockResult>(parent?.interactionMap)
        for(key in getKeys(false)) {
            val section = getConfigurationSection(key) ?: continue
        }
        map
    }!!


    fun WeaponPlayer.hit(block: Block): HitBlockResult {
        val type = block.type
        for(interaction in HitBlockInteraction.interactions) {

        }
        return HitBlockInteraction.interactions[""]!!.defaultResult
    }

}

class HitBlockResult(
    val types: Set<Material>,
    val pierce: Boolean,
    val resistance: Double,
) {
}

class HitBlockInteraction(
    val name: String,
    val defaultResult: HitBlockResult,
    val hit: WeaponPlayer.(hitBlock: HitBlock, block: Block, default: HitBlockResult) -> HitBlockResult = { _, _, _ -> defaultResult }
) {

    companion object {
        val interactions = HashMap<String, HitBlockInteraction>()
        init {
            listOf<HitBlockInteraction>(
                HitBlockInteraction("ignore", HitBlockResult(setOf(), true, 0.0)),
                HitBlockInteraction("break", HitBlockResult(setOf(), true, 0.0)) { hitBlock, block, default ->
                    default
                },
            ).forEach { interactions[it.name] = it }
        }
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if(other is HitBlock) {
            return name == other.name
        }
        return super.equals(other)
    }

}