package kr.lostwar.vehicle.core

import kr.lostwar.util.Config
import kr.lostwar.vehicle.core.car.CarInfo
import kr.lostwar.vehicle.core.uam.UAMInfo
import org.bukkit.configuration.ConfigurationSection

class VehicleType<T : VehicleInfo>(
    val name: String,
    val clazz: Class<T>,
//    val constructor: (key: String, config: ConfigurationSection, configFile: Config, parent: T?) -> T
) {
    private val reflectConstructor = clazz.getConstructor(
        String::class.java,
        ConfigurationSection::class.java,
        Config::class.java,
        clazz,
    )
    private val constructor: (String, ConfigurationSection, Config, T?) -> T =
    { key, config, configFile, parent ->
        reflectConstructor.newInstance(key, config, configFile, parent)
    }
    fun create(key: String, config: ConfigurationSection, configFile: Config, parent: T?): T {
        return constructor(key, config, configFile, parent)
    }

    fun cast(info: VehicleInfo): T {
        return info as T
    }

    companion object {
        private val types = mutableListOf<VehicleType<out VehicleInfo>>(
            VehicleType("car", CarInfo::class.java),
            VehicleType("uam", UAMInfo::class.java),
        )
        val registeredTypes by lazy { types.toList() }
        val registeredTypesByName by lazy { registeredTypes.associateBy { it.name } }

        fun getTypeOrNull(rawType: String?): VehicleType<out VehicleInfo>? {
            if(rawType == null) return null
            return registeredTypesByName[rawType]
        }
    }
}