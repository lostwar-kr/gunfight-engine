package kr.lostwar.gun.weapon.components

import kr.lostwar.gun.GunEngine
import kr.lostwar.gun.weapon.Weapon
import kr.lostwar.gun.weapon.WeaponComponent
import kr.lostwar.gun.weapon.WeaponPropertyType
import kr.lostwar.gun.weapon.WeaponType
import kr.lostwar.util.ExtraUtil
import kr.lostwar.util.SoundClip
import kr.lostwar.util.SoundInfo
import org.bukkit.configuration.ConfigurationSection

class SelectorLever(
    config: ConfigurationSection?,
    weapon: WeaponType,
    parent: SelectorLever?,
) : WeaponComponent(config, weapon, parent, true) {


    enum class SelectorType(val displayName: String, val targetComponent: Class<out WeaponComponent>?) {
        SAFE        ("안전", null),
        SINGLE      ("단발", null),
        BURST       ("점사", Burst::class.java),
        FULL_AUTO   ("연발", FullAuto::class.java),
        ;
        companion object {
            val types by lazy { values().toList() }
            private val typesByName by lazy { types.associateBy { it.name } }
            operator fun get(name: String) = typesByName[name]

        }

    }

    val useSelector: Boolean = getBoolean("useSelector", parent?.useSelector, false)
    val selectors: List<SelectorType>
    val defaultSelector: SelectorType
    val leverSound: SoundClip = getSoundClip("leverSound", parent?.leverSound)

    init {
        if(!useSelector && parent?.useSelector != true) {
            if(weapon.hasComponent(FullAuto::class.java)) {
                selectors = listOf(SelectorType.FULL_AUTO)
                defaultSelector = SelectorType.FULL_AUTO
            }else if(weapon.hasComponent(Burst::class.java)) {
                selectors = listOf(SelectorType.BURST)
                defaultSelector = SelectorType.BURST
            }else{
                selectors = listOf(SelectorType.SINGLE)
                defaultSelector = SelectorType.SINGLE
            }
        }else{
            val selectorsRaw = if(config?.isList("selectors") == true) config.getStringList("selectors") else null
            selectors =
                // list 정의가 돼있으면 그대로 파싱
                selectorsRaw?.mapNotNull {
                    val parsed = SelectorType[it]
                    if(parsed == null){
                        GunEngine.logWarn("${parsed} 조정간은 유효하지 않은 조정간입니다.")
                        return@mapNotNull null
                    }
                    if(parsed.targetComponent?.let { clazz -> !weapon.hasComponent(clazz) } == true) {
                        GunEngine.logWarn("${parsed} 조정간에 해당하는 Component(${parsed.targetComponent.simpleName})가 활성화되지 않았습니다.")
                        return@mapNotNull null
                    }
                    parsed
                } ?: (parent?.selectors ?: emptyList()) // 아니면 부모 selector 따름
            defaultSelector = getEnumString("defaultSelector", parent?.defaultSelector, selectors.first())
                .takeIf { it in selectors } ?: selectors.first()


        }
    }

    override fun onInstantiate(weapon: Weapon) {
        weapon.registerNotNull(SELECTOR, defaultSelector)
    }
    companion object {
        private val SELECTOR_TYPE = ExtraUtil.EnumPersistentDataType(SelectorType::class.java)
        private val SELECTOR = WeaponPropertyType("selector", SELECTOR_TYPE)

        var Weapon.selector: SelectorType
            get() = get(SELECTOR)!!
            set(value) { set(SELECTOR, value) }
    }

}