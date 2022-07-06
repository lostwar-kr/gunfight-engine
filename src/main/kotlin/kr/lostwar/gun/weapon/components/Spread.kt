package kr.lostwar.gun.weapon.components

import kr.lostwar.gun.weapon.WeaponComponent
import kr.lostwar.gun.weapon.WeaponPlayer
import kr.lostwar.gun.weapon.WeaponType
import kr.lostwar.gun.weapon.components.SelectorLever.Companion.selector
import org.bukkit.configuration.ConfigurationSection

class Spread(
    config: ConfigurationSection?,
    weapon: WeaponType,
    parent: Spread?,
) : WeaponComponent(config, weapon, parent, true) {

    val baseSpread: Double = getDouble("spread", parent?.baseSpread)
    val spreadBonusBySelectorType: Map<SelectorLever.SelectorType, Double> = get("spreadBonus", parent?.spreadBonusBySelectorType, emptyMap()) getter@{ key ->
        val section = getConfigurationSection(key) ?: return@getter emptyMap()
        val selectorKeys = section.getKeys(false)
        val map = HashMap<SelectorLever.SelectorType, Double>(selectorKeys.size)
        for(selectorKey in selectorKeys) {
            val selector = SelectorLever.SelectorType[selectorKey] ?: continue
            val value = section.getDouble(selectorKey, 0.0)
            map[selector] = value
        }
        map
    }!!

    private val factors = ArrayList<WeaponSpreadFunction>()
    override fun onLateInit() {
        registerFactor(WeaponSpreadFunction { it + (spreadBonusBySelectorType[weapon?.selector] ?: 0.0) })
    }
    fun registerFactor(factor: WeaponSpreadFunction) {
        factors.add(factor)
        factors.sortBy { it.order }
    }
    fun getSpread(player: WeaponPlayer): Double {
        var value = baseSpread
        for(factor in factors) {
            value = factor.function(player, value)
        }
        return value
    }

}

class WeaponSpreadFunction(val order: Int = 0, val function: WeaponPlayer.(Double) -> Double)