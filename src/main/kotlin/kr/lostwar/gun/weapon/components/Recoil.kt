package kr.lostwar.gun.weapon.components

import kr.lostwar.gun.weapon.WeaponComponent
import kr.lostwar.gun.weapon.WeaponPlayer
import kr.lostwar.gun.weapon.WeaponPlayerEventListener
import kr.lostwar.gun.weapon.WeaponType
import kr.lostwar.gun.weapon.components.SelectorLever.Companion.selector
import kr.lostwar.gun.weapon.event.WeaponShootPrepareEvent
import kr.lostwar.util.ConfigUtil.getDoubleRangeOrNull
import kr.lostwar.util.DoubleRange
import kr.lostwar.util.math.random
import kr.lostwar.util.nms.PacketUtil.rotateCamera
import org.bukkit.configuration.ConfigurationSection

class Recoil(
    config: ConfigurationSection?,
    weapon: WeaponType,
    parent: Recoil?,
) : WeaponComponent(config, weapon, parent) {

    val vertical: RecoilData = get("vertical", parent?.vertical, RecoilData.getDefault(RecoilType.VERTICAL, weapon)) {
        RecoilData(RecoilType.VERTICAL, getConfigurationSection(it), weapon, parent?.vertical)
    }!!
    val horizontal: RecoilData = get("horizontal", parent?.horizontal, RecoilData.getDefault(RecoilType.HORIZONTAL, weapon)) {
        RecoilData(RecoilType.HORIZONTAL, getConfigurationSection(it), weapon, parent?.horizontal)
    }!!

    override fun onLateInit() {
        vertical.onLateInit()
        horizontal.onLateInit()
    }

    private val shootPrepareListener = WeaponPlayerEventListener(WeaponShootPrepareEvent::class.java) { event ->
        val v = vertical.getRecoil(this)
        val h = horizontal.getRecoil(this)
        val player = player
        player.rotateCamera(h.toFloat(), -v.toFloat())
    }

    override val listeners = listOf(
        shootPrepareListener,
    )


}
typealias SimpleRecoilFunction = (Int, WeaponPlayer.() -> Double) -> WeaponRecoilFunction
enum class RecoilType(private val function: SimpleRecoilFunction) : SimpleRecoilFunction by function {
    VERTICAL(WeaponRecoilFunction.Companion::shift),
    HORIZONTAL(WeaponRecoilFunction.Companion::expand),
    ;
}
class RecoilData(
    val type: RecoilType,
    val config: ConfigurationSection?,
    val weapon: WeaponType,
    val parent: RecoilData?
) {
    val baseRecoil: DoubleRange = config?.getDoubleRangeOrNull("recoil") ?: parent?.baseRecoil ?: 0.0 .. 0.0
    val recoilBySelectorType: Map<SelectorLever.SelectorType, Double> = config?.run {
        val key = "recoilBonus"
        val section = getConfigurationSection(key) ?: return@run emptyMap()
        val selectorKeys = section.getKeys(false)
        buildMap {
            for(selectorKey in selectorKeys) {
                val selector = SelectorLever.SelectorType[selectorKey] ?: continue
                val value = section.getDouble(selectorKey, this@RecoilData.parent?.recoilBySelectorType?.get(selector) ?: 0.0)
                put(selector, value)
            }
        }
    } ?: parent?.recoilBySelectorType ?: emptyMap()

    private val factors = ArrayList<WeaponRecoilFunction>()
    fun registerFactor(order: Int = 0, function: WeaponPlayer.() -> Double) {
        registerFactor(type(order, function))
    }
    fun registerFactor(factor: WeaponRecoilFunction) {
        factors.add(factor)
        factors.sortBy { it.order }
    }
    fun getRecoil(player: WeaponPlayer): Double {
        var value = baseRecoil
        for(factor in factors) {
            value = factor.function(player, value)
        }
        return value.random()
    }

    internal fun onLateInit() {
        registerFactor(type(0) { recoilBySelectorType[weapon?.selector] ?: 0.0 })
    }
    companion object {
        fun getDefault(type: RecoilType, weapon: WeaponType) = RecoilData(type, null, weapon, null)
    }
}
class WeaponRecoilFunction(val order: Int = 0, val function: WeaponPlayer.(DoubleRange) -> DoubleRange) {
    companion object {
        fun shift(order: Int = 0, value: WeaponPlayer.() -> Double) = WeaponRecoilFunction(order) { oldRange ->
            val shift = value()
            (oldRange.start + shift) .. (oldRange.endInclusive + shift)
        }
        fun expand(order: Int = 0, value: WeaponPlayer.() -> Double) = WeaponRecoilFunction(order) { oldRange ->
            val shift = value()
            (oldRange.start - shift) .. (oldRange.endInclusive + shift)
        }
    }
}