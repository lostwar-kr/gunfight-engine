package kr.lostwar.gun.weapon

import kr.lostwar.GunfightEngine
import kr.lostwar.gun.weapon.WeaponPropertyType.Companion.get
import kr.lostwar.gun.weapon.components.SelectorLever
import kr.lostwar.util.ExtraUtil
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataAdapterContext
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import java.util.*

class WeaponPropertyType<T : Any, Z : Any> (
    val key: String,
    val type: PersistentDataType<T, Z>,
    val identifier: Boolean = false,
    val saveOnSet: Boolean = true,
) {
    val namespacedKey = NamespacedKey("p", key)
    operator fun get(container: PersistentDataContainer) = container[namespacedKey, type]
    operator fun set(container: PersistentDataContainer, value: Z) {
        container[namespacedKey, type] = value
    }
    companion object {
        val BOOL = object : PersistentDataType<Integer, Boolean> {
            private val primitiveType = Integer::class.java
            override fun getPrimitiveType(): Class<Integer> = primitiveType
            private val complexType = Boolean::class.java
            override fun getComplexType(): Class<Boolean> = complexType

            private val integerTrue: Integer = Integer.valueOf(1) as Integer
            private val integerFalse: Integer = Integer.valueOf(0) as Integer
            override fun fromPrimitive(primitive: Integer, context: PersistentDataAdapterContext): Boolean {
                return primitive != integerFalse
            }
            override fun toPrimitive(bool: Boolean, context: PersistentDataAdapterContext): Integer {
                return if(bool) integerTrue else integerFalse
            }
        }
        private val STATE_TYPE = ExtraUtil.EnumPersistentDataType(Weapon.WeaponState::class.java)
        private val UUID = object : PersistentDataType<IntArray, UUID> {
            private val primitiveType = IntArray::class.java
            override fun getPrimitiveType(): Class<IntArray> = primitiveType
            private val complexType = java.util.UUID::class.java
            override fun getComplexType(): Class<UUID> = complexType

            private val lowerMask: Long = (1 shl 32) - 1
            override fun fromPrimitive(arr: IntArray, context: PersistentDataAdapterContext): UUID {
                val most = (arr[0].toLong() shl 32) or (arr[1].toLong() and lowerMask)
                val least = (arr[2].toLong() shl 32) or (arr[3].toLong() and lowerMask)
                return UUID(most, least)
            }

            override fun toPrimitive(uuid: UUID, p1: PersistentDataAdapterContext): IntArray {
                val most: Long = uuid.mostSignificantBits
                val least: Long = uuid.leastSignificantBits
                return intArrayOf((most shr 32).toInt(), most.toInt(), (least shr 32).toInt(), least.toInt())
            }
        }
        operator fun <T : Any, Z : Any> PersistentDataContainer.get(type: WeaponPropertyType<T, Z>) = type[this]
        operator fun <T : Any, Z : Any> PersistentDataContainer.set(type: WeaponPropertyType<T, Z>, value: Z) {
            type[this] = value
        }

        val KEY = WeaponPropertyType("key", PersistentDataType.STRING, true)
        val ID = WeaponPropertyType("id", UUID, true)
        val STATE = WeaponPropertyType("state", STATE_TYPE)
    }

    override fun equals(other: Any?): Boolean {
        if(other is WeaponPropertyType<*, *>) {
            return key == other.key
        }
        return super.equals(other)
    }

    override fun hashCode(): Int {
        return key.hashCode()
    }
}