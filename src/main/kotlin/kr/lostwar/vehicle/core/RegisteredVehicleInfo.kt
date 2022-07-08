package kr.lostwar.vehicle.core

import kr.lostwar.util.Config
import org.bukkit.configuration.ConfigurationSection

class RegisteredVehicleInfo(
    val key: String,
    val config: ConfigurationSection,
    val configFile: Config,
    val parentKey: String?
)