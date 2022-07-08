package kr.lostwar.vehicle.core

import kr.lostwar.util.Config
import kr.lostwar.util.SoundClip
import kr.lostwar.util.item.ItemData
import kr.lostwar.util.item.ItemData.Companion.getItemData
import kr.lostwar.vehicle.VehicleEngine
import org.bukkit.configuration.ConfigurationSection
import org.jetbrains.annotations.Contract
import java.io.File
import java.util.*
import kotlin.collections.HashMap

abstract class VehicleInfo(
    val key: String,
    val config: ConfigurationSection,
    val configFile: Config,
    val parent : VehicleInfo?,
) {
    private val parentVehicleKey = config.getString("parent")
    var loaded = false
    var valid = true

    val type: VehicleType<*> = get("type", parent?.type) {
        VehicleType.getTypeOrNull(getString(it)) ?: error("cannot parse vehicle type: ${getString(it)}")
    }!!
    val displayName: String = getString("displayName", parent?.displayName, key)!!



    @Contract("_, _, !null -> !null")
    protected fun <T : Any> get(key: String, parentDef: T?, def: T? = null, getter: ConfigurationSection.(key: String) -> T?): T? {
        return config.getter(key) ?: parentDef ?: def
    }

    @Contract("_, _, !null -> !null")
    protected inline fun <reified T : Enum<T>> getEnumString(key: String, parentDef: T?, def: T): T {
        val raw = config.getString(key) ?: return parentDef ?: def
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
        return config.getInt(key, parentDef ?: def)
    }
    protected fun getBoolean(key: String, parentDef: Boolean?, def: Boolean = false): Boolean {
        return config.getBoolean(key, parentDef ?: def)
    }
    protected fun getDouble(key: String, parentDef: Double?, def: Double = 0.0): Double {
        return config.getDouble(key, parentDef ?: def)
    }

    @Contract("_, _, !null -> !null")
    protected fun getItemData(key: String, parentDef: ItemData?, def: ItemData? = null): ItemData? {
        return get(key, parentDef, def) { k -> getItemData(k, null) }
    }
    protected fun getSoundClip(key: String, parentDef: SoundClip?, def: SoundClip = SoundClip.emptyClip): SoundClip {
        return get(key, parentDef, def) { k -> if(isList(k)) SoundClip.parse(getStringList(k)) else null }!!
    }

    companion object {
        private val registeredVehicles = HashMap<String, RegisteredVehicleInfo>()
        private val byKey = HashMap<String, VehicleInfo>()
        fun load() {
            registeredVehicles.clear()
            byKey.clear()

            val vehiclePath = VehicleEngine.directory + "vehicles/"

            val folder = File(vehiclePath)
            val files = folder.listFiles()
            if (files == null || files.isEmpty()) {
                VehicleEngine.logWarn("불러올 차량이 없습니다. ${vehiclePath} 폴더 안에 차량 정보 파일을 추가하세요.")
                return
            }

            val queue = LinkedList<File>()
            queue.addFirst(folder)

            val vehicleFiles = arrayListOf<File>()
            while (queue.isNotEmpty()) {
                val file = queue.poll()
                if (file.isDirectory) {
                    val files = file.listFiles() ?: continue
                    queue.addAll(files)
                    continue
                }
                if (file.name.endsWith(".yml")) {
                    vehicleFiles.add(file)
                }
            }

            VehicleEngine.log("${vehicleFiles.size}개의 파일 확인 중 ...")
            vehicleFiles.forEach { file ->
                val config = Config(file)
                for (key in config.getKeys(false)) {
                    val section = config.getConfigurationSection(key)
                    if (section == null) {
                        VehicleEngine.logWarn("${config.file.path}: ${key}는 유효한 section이 아님")
                        continue
                    }
                    register(key, section, config)
                }
            }
            loadVehicles()
        }
        private fun register(key: String, section: ConfigurationSection, configFile: Config) {
            if(key in registeredVehicles) {
                return VehicleEngine.logWarn("중복 차량 ${key}, ${configFile.file.path} 파일 불러오는 중 발생")
            }

            val parentKey = section.getString("parent")
            registeredVehicles[key] = RegisteredVehicleInfo(key, section, configFile, parentKey)
            if(parentKey == null) {
                val vehicle = load(key, section, configFile, null) ?: return
                byKey[key] = vehicle
            }
        }

        private fun loadVehicles() {
            val childVehicles = HashMap(registeredVehicles)
            var count = 0
            var level = 0
            while(childVehicles.size > 0) {
                val iterator = childVehicles.iterator()
                while(iterator.hasNext()) {
                    val (key, registeredInfo) = iterator.next()
                    if(registeredInfo == null){
                        iterator.remove()
                        continue
                    }
                    if(!registeredVehicles.containsKey(registeredInfo.parentKey)) {
                        iterator.remove()
                        VehicleEngine.logWarn("${key}의 부모 차량이 존재하지 않음: ${registeredInfo}")
                        continue
                    }
                    val parentVehicle = byKey[registeredInfo.parentKey] ?: continue
                    val vehicle = load(key, registeredInfo.config, registeredInfo.configFile, parentVehicle) ?: return
                    byKey[key] = vehicle
                }
            }
        }

        private fun load(key: String, section: ConfigurationSection, configFile: Config, parent: VehicleInfo?): VehicleInfo? {
            val typeKey = section.getString("type")
            val type = (VehicleType.getTypeOrNull(typeKey) ?: parent?.type) as? VehicleType<VehicleInfo>
                ?: return VehicleEngine.logErrorNull("유효하지 않은 VehicleType: ${typeKey}")

            return type.create(key, section, configFile, parent)
        }

    }

}