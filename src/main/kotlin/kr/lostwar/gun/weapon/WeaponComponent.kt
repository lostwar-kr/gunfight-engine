package kr.lostwar.gun.weapon

import kr.lostwar.gun.GunEngine
import kr.lostwar.gun.weapon.components.*
import kr.lostwar.util.AnimationClip
import kr.lostwar.util.AnimationClip.Companion.getAnimationClipOrNull
import kr.lostwar.util.SoundClip
import kr.lostwar.util.item.ItemData
import kr.lostwar.util.item.ItemData.Companion.getItemData
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.event.Event
import org.jetbrains.annotations.Contract

abstract class WeaponComponent(
    protected val config: ConfigurationSection?,
    protected val weapon: WeaponType,
    protected val parentComponent: WeaponComponent?,
    forceEnable: Boolean = false,
) {

    companion object {
        private val components = mutableListOf(
            Item::class.java,
            Ammo::class.java,
            FullAuto::class.java,
            Burst::class.java,
            SelectorLever::class.java,
            Click::class.java,
            Hit::class.java,
            Spread::class.java,
            Shoot::class.java,
            Hitscan::class.java,
            Zoom::class.java,
        )
        fun register(clazz: Class<out WeaponComponent>) {
            components.add(clazz)
        }
        val registeredComponents by lazy { components.toList() }
        val registeredComponentsWithConstructor by lazy {
            registeredComponents.mapNotNull {
                try {
                    it to it.getConstructor(
                        ConfigurationSection::class.java,
                        WeaponType::class.java,
                        it,
                    )
                } catch (e: Exception) {
                    GunEngine.logWarn("WeaponComponent ${it.simpleName}를 초기화하는 도중 오류 발생")
                    null
                }
            }.toMap()
        }
        val registeredComponentsWithIndex by lazy {
            registeredComponents.associateWith { registeredComponents.indexOf(it) }
        }
        val Class<out WeaponComponent>.index: Int; get() = registeredComponentsWithIndex[this] ?: error("not registered component: ${simpleName}")
    }


    val name = javaClass.simpleName
    val isEnable: Boolean = run {
        // config에 모듈 자체가 없으면 false 처리
        // 모듈만 있고, enable 키가 없으면 true 처리
        // 키가 있으면 해당 키 값 처리
        val enableInConfig = config?.getBoolean("enable", true) ?: false
        // 부모가 enable: true인 경우
        val parentEnable = parentComponent?.isEnable ?: false
        (forceEnable || enableInConfig || parentEnable)
    }.also {
        // 비활성화된 모듈은 로드 과정에서 throw exception
        if(!it) {
            throw WeaponComponentDisableException(this)
        }
    }
    open val listeners: List<WeaponPlayerEventListener<out Event>> = emptyList()

    /////////////////////////
    // config deserializer //
    /////////////////////////

    @Contract("_, _, !null -> !null")
    protected fun <T : Any> get(key: String, parentDef: T?, def: T? = null, getter: ConfigurationSection.(key: String) -> T?): T? {
        return config?.getter(key) ?: parentDef ?: def
    }

    @Contract("_, _, !null -> !null")
    protected inline fun <reified T : Enum<T>> getEnumString(key: String, parentDef: T?, def: T): T {
        val raw = config?.getString(key) ?: return parentDef ?: def
        return try {
            enumValueOf<T>(raw)
        }catch (e: Exception) {
            parentDef ?: def
        }
    }

    @Contract("_, _, !null -> !null")
    protected fun getString(key: String, parentDef: String?, def: String? = null)
        = get(key, parentDef, def, ConfigurationSection::getString)

    protected fun getStringList(key: String, parentDef: List<String>?, def: List<String> = emptyList()): List<String>
        = get(key, parentDef, def) { k -> if(isList(k)) getStringList(k) else null }!!

    protected fun getInt(key: String, parentDef: Int?, def: Int = 0): Int {
        val cfg = config ?: return parentDef ?: def
        return cfg.getInt(key, parentDef ?: def)
    }
    protected fun getBoolean(key: String, parentDef: Boolean?, def: Boolean = false): Boolean {
        val cfg = config ?: return parentDef ?: def
        return cfg.getBoolean(key, parentDef ?: def)
    }
    protected fun getDouble(key: String, parentDef: Double?, def: Double = 0.0): Double {
        val cfg = config ?: return parentDef ?: def
        return cfg.getDouble(key, parentDef ?: def)
    }

    @Contract("_, _, !null -> !null")
    protected fun getItemData(key: String, parentDef: ItemData?, def: ItemData? = null): ItemData? {
        return get(key, parentDef, def) { k -> getItemData(k, null) }
    }
    protected fun getSoundClip(key: String, parentDef: SoundClip?, def: SoundClip = SoundClip.emptyClip): SoundClip {
        return get(key, parentDef, def) { k -> if(isList(k)) SoundClip.parse(getStringList(k)) else null }!!
    }
    protected fun getAnimationClip(key: String, parentDef: AnimationClip?, def: AnimationClip = AnimationClip.emptyClip): AnimationClip {
        return get(key, parentDef, def) { k -> getAnimationClipOrNull(this, k) }!!
    }

    fun lateInit() {
//        GunEngine.log("registering component ${name} listeners ...")
        weapon.registerListeners(listeners)
        onLateInit()
    }
    protected open fun onLateInit() {}
    open fun onInstantiate(weapon: Weapon) {}
}