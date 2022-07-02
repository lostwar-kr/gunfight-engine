package kr.lostwar.gun.weapon

import org.bukkit.persistence.PersistentDataContainer
import kotlin.reflect.KProperty

class WeaponProperty<T : Any, Z : Any>(
    val type: WeaponPropertyType<T, Z>,
    private val defaultValue: Z
) {
    private var value: Z = defaultValue
    operator fun getValue(weapon: Weapon, property: KProperty<*>): Z {
        return value
    }
    operator fun setValue(weapon: Weapon, property: KProperty<*>, value: Z) {
        this.value = value
    }
    fun takeOut(container: PersistentDataContainer) {
        val value = type[container] ?: defaultValue
        this.value = value
    }
    fun storeTo(container: PersistentDataContainer) {
        type[container] = value
    }
}