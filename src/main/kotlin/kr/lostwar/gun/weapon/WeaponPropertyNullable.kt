package kr.lostwar.gun.weapon

import kr.lostwar.gun.weapon.WeaponPropertyType.Companion.get
import kr.lostwar.gun.weapon.WeaponPropertyType.Companion.set
import org.bukkit.persistence.PersistentDataContainer
import kotlin.reflect.KProperty


sealed class WeaponProperty<T : Any, Z : Any>(
    val type: WeaponPropertyType<T, Z>,
    private val defaultValue: Z?,
) {
    var value: Z? = defaultValue

    fun takeOut(container: PersistentDataContainer): Boolean {
        // identifier가 없는 경우는 takeOut 실패
        val value = container[type] ?:
        if(type.identifier) return false
        else defaultValue
        this.value = value
        return true
    }
    fun storeTo(container: PersistentDataContainer) {
        // identifier가 아니고 기본값이랑 같은 경우 굳이 데이터에 포함 안 함
        if(value == null || !type.identifier && defaultValue == value) {
            container.remove(type.namespacedKey)
        }else{
            container[type] = value!!
        }
    }
}

class WeaponPropertyNullable<T : Any, Z : Any>(
    type: WeaponPropertyType<T, Z>,
    defaultValue: Z?,
) : WeaponProperty<T, Z>(type, defaultValue) {

    operator fun getValue(weapon: Weapon, property: KProperty<*>): Z? {
        return value
    }
    operator fun setValue(weapon: Weapon, property: KProperty<*>, value: Z?) {
        this.value = value
    }
}

class WeaponPropertyNotNull<T : Any, Z : Any>(
    type: WeaponPropertyType<T, Z>,
    defaultValue: Z,
) : WeaponProperty<T, Z>(type, defaultValue) {
    operator fun getValue(weapon: Weapon, property: KProperty<*>): Z {
        return value!!
    }
    operator fun setValue(weapon: Weapon, property: KProperty<*>, value: Z) {
        this.value = value
    }
}