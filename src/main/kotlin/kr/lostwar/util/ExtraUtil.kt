package kr.lostwar.util

import kr.lostwar.gun.GunEngine
import kr.lostwar.gun.weapon.components.SelectorLever
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.persistence.PersistentDataAdapterContext
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.Vector

object ExtraUtil {

    fun <T> List<T>.joinToString()
            = joinToString(", ", "[", "]") { it.toString() }

    class PrimitivePersistentDataType<T : Any> constructor(
        private val primitiveType: Class<T>
    ) : PersistentDataType<T, T> {
        override fun getPrimitiveType(): Class<T> {
            return primitiveType
        }

        override fun getComplexType(): Class<T> {
            return primitiveType
        }

        override fun toPrimitive(complex: T, context: PersistentDataAdapterContext): T {
            return complex
        }

        override fun fromPrimitive(primitive: T, context: PersistentDataAdapterContext): T {
            return primitive
        }
    }

    class EnumPersistentDataType<T : Enum<T>>(
        private val complexType: Class<T>
    ) : PersistentDataType<Integer, T> {
        private val primitiveType = Integer::class.java
        override fun getPrimitiveType(): Class<Integer> = primitiveType
        override fun getComplexType(): Class<T> = complexType
        private val enumConstants = complexType.enumConstants

        override fun toPrimitive(type: T, context: PersistentDataAdapterContext): Integer {
            return type.ordinal as Integer
        }
        override fun fromPrimitive(index: Integer, context: PersistentDataAdapterContext): T {
            return enumConstants[index.toInt()]
        }
    }

    private val armorStandOffsetMap = mapOf<EquipmentSlot, Vector>(
        EquipmentSlot.HAND to Vector(+0.3125, 1.375, 0.0),
        EquipmentSlot.OFF_HAND to Vector(-0.3125, 1.375, 0.0),
        EquipmentSlot.HEAD to Vector(0.0, 1.4375, 0.0),
    )
    val EquipmentSlot.armorStandOffset: Vector; get() = armorStandOffsetMap[this] ?: Vector()
}