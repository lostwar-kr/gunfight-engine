package kr.lostwar.vehicle.core.car

import kr.lostwar.util.Config
import kr.lostwar.vehicle.core.VehicleInfo
import org.bukkit.configuration.ConfigurationSection

class CarInfo(
    key: String,
    config: ConfigurationSection,
    configFile: Config,
    parent: CarInfo?
) : VehicleInfo(key, config, configFile, parent) {



}