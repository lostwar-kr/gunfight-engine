package kr.lostwar.util

import kr.lostwar.gun.weapon.components.SelectorLever
import org.bukkit.persistence.PersistentDataAdapterContext
import org.bukkit.persistence.PersistentDataType

object ExtraUtil {


    class PrimitivePersistentDataType<T> constructor(
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
}