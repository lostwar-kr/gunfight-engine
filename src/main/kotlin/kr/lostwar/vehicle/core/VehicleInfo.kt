package kr.lostwar.vehicle.core

import kr.lostwar.util.Config
import kr.lostwar.util.ConfigUtil
import kr.lostwar.util.SoundClip
import kr.lostwar.util.SoundInfo
import kr.lostwar.util.item.ItemData
import kr.lostwar.util.item.ItemData.Companion.getItemData
import kr.lostwar.util.math.VectorUtil.getBukkitVector
import kr.lostwar.util.ui.text.consoleWarn
import kr.lostwar.vehicle.VehicleEngine
import kr.lostwar.vehicle.core.VehicleModelInfo.Companion.getModelInfo
import kr.lostwar.vehicle.core.VehicleModelInfo.Companion.getModelInfoList
import kr.lostwar.vehicle.core.animation.VehicleModelAnimation
import kr.lostwar.vehicle.core.animation.VehicleModelAnimation.Companion.getAnimation
import kr.lostwar.vehicle.core.parachute.ParachuteInfo
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.util.Vector
import org.jetbrains.annotations.Contract
import java.io.File
import java.util.LinkedList
import kotlin.collections.HashMap

abstract class VehicleInfo(
    val key: String,
    val config: ConfigurationSection,
    val configFile: Config,
    val parent : VehicleInfo?,
) {
    val type: VehicleType<*> = get("type", parent?.type) {
        VehicleType.getTypeOrNull(getString(it)) ?: error("cannot parse vehicle type: ${getString(it)}")
    }!!
    val displayName: String = getString("displayName", parent?.displayName, key)!!

    val models: Map<String, VehicleModelInfo> = get("model", parent?.models, emptyMap()) { parentKey ->
        val section = getConfigurationSection(parentKey) ?: return@get null
        val parent = this@VehicleInfo.parent
        val parentModels = parent?.models ?: emptyMap()
        section.getKeys(false)
            .mapNotNull { key -> section.getModelInfo(key, parentModels[key]) }
            .associateBy { it.key }
            .also { map ->
                map.forEach { (key, info) ->
                    if(info.parentKey == null) return@forEach
                    info.parent = map[info.parentKey]
                        ?. takeIf { it.parentKey != info.parentKey }
                        ?: run {
                            consoleWarn("failed to assign parent on ${info.key} to ${info.parentKey}")
                            null
                        }
                }
            }
    }!!

    val seats: List<VehicleModelInfo> = get("seat", parent?.seats, emptyList()) {parentKey ->
        val parent = this@VehicleInfo.parent
        getModelInfoList(parentKey, parent?.seats ?: emptyList())
            .onEach { info ->
                if(info.parentKey == null) return@onEach
                info.parent = models[info.parentKey]
                    ?. takeIf { it.parentKey != info.parentKey }
                    ?: run {
                        consoleWarn("failed to assign parent on seat ${info.key} to ${info.parentKey}")
                        null
                    }
            }
    }!!

    val animations: Map<String, VehicleModelAnimation> = get("animation", parent?.animations, emptyMap()) { parentKey ->
        val section = getConfigurationSection(parentKey) ?: return@get null
        val parent = this@VehicleInfo.parent
        val parentAnimations = parent?.animations ?: emptyMap()
        section.getKeys(false)
            .mapNotNull { key -> section.getAnimation(this@VehicleInfo, key, parentAnimations[key]) }
            .associateBy { it.event }
    }!!

    val health: Double = getDouble("entity.health", parent?.health, 100.0)
    val hitSound: SoundClip = getSoundClip("entity.hitSound", parent?.hitSound, SoundClip(listOf(SoundInfo(Sound.ENTITY_ITEM_BREAK))))
    val deathExplosionEnable: Boolean = getBoolean("entity.death.explosion.enable", parent?.deathExplosionEnable, true)
    val deathExplosionDamage: Double = getDouble("entity.death.explosion.damage", parent?.deathExplosionDamage, 20.0)
    val deathExplosionRadius: Double = getDouble("entity.death.explosion.damageRadius", parent?.deathExplosionRadius)
    val deathExplosionDamageDecreasePerDistance: Double = getDouble("entity.death.explosion.damageDecreasePerDistance", parent?.deathExplosionDamageDecreasePerDistance, 2.0)
    val deathExplosionPassengerDamageMultiply: Double = getDouble("entity.death.explosion.damagePassengerMultiply", parent?.deathExplosionPassengerDamageMultiply, 1000.0)

    val upStep: Float = getDouble("general.physics.upStep", parent?.upStep?.toDouble(), 1.0).toFloat()
    val collisionDamagePerSpeed: Double = getDouble("general.physics.collision.damagePerSpeed", parent?.collisionDamagePerSpeed, 1.0)
    val collisionSound: SoundClip = getSoundClip("general.physics.collision.sound", parent?.collisionSound, SoundClip(listOf(SoundInfo(Sound.ITEM_SHIELD_BLOCK))))

    val abandonedRemoveTime: Int = getInt("genera.extra.abandonedRemoveTime", parent?.abandonedRemoveTime, -1)
    open val disableDriverExitVehicleByShiftKey: Boolean = getBoolean("general.extra.disableDriverExitVehicleByShiftKey", parent?.disableDriverExitVehicleByShiftKey, false)

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

    protected fun getVector(key: String, parentDef: Vector?, def: Vector = Vector()): Vector {
        return get(key, parentDef, def) { k -> getBukkitVector(k) }!!
    }
    @Contract("_, _, !null -> !null")
    protected fun getItemData(key: String, parentDef: ItemData?, def: ItemData? = null): ItemData? {
        return get(key, parentDef, def) { k -> getItemData(k, null) }
    }
    protected fun getSoundClip(key: String, parentDef: SoundClip?, def: SoundClip = SoundClip.emptyClip): SoundClip {
        return get(key, parentDef, def) { k -> if(isList(k)) SoundClip.parse(getStringList(k)) else null }!!
    }
    protected fun getFloatRange(
        key: String,
        parentDef: ClosedFloatingPointRange<Float>?,
        def: ClosedFloatingPointRange<Float> = 0f..0f,
        clampRange: ClosedFloatingPointRange<Float>?
    ): ClosedFloatingPointRange<Float> {
        return get(key, parentDef, def) {
            with(ConfigUtil) { getFloatRangeOrNull(key, clampRange) }
        }!!
    }

    abstract fun spawn(location: Location, decoration: Boolean = false): VehicleEntity<out VehicleInfo>


    companion object {
        private val registeredVehicles = HashMap<String, RegisteredVehicleInfo>()
        val byKey = HashMap<String, VehicleInfo>()
        var primaryParachute: ParachuteInfo? = null
        fun load() {
            registeredVehicles.clear()
            primaryParachute = null
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
            updateVehicleEntities()
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
            val childVehicles = HashMap(registeredVehicles.filter { it.key !in byKey })
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
                    try {
                        val vehicle = load(key, registeredInfo.config, registeredInfo.configFile, parentVehicle) ?: return
                        byKey[key] = vehicle
                        ++count
                    } catch (e: Exception) {
                        VehicleEngine.logWarn("차량 ${key} 불러오는 중 예외 발생: ${e.message}")
                        e.stackTrace.forEach { VehicleEngine.logWarn(it.toString()) }
                    }
                }
                ++level
                if(level >= 50) {
                    VehicleEngine.logWarn("50level 이상 차량 상속 중단, 남은 차량 ${childVehicles.size}개")
                    childVehicles.forEach { VehicleEngine.logWarn(" - ${it.key} : ${registeredVehicles[it.key]?.parentKey}") }
                }
            }
        }

        private fun load(key: String, section: ConfigurationSection, configFile: Config, parent: VehicleInfo?): VehicleInfo? {
            val typeKey = section.getString("type")
            val type = ((VehicleType.getTypeOrNull(typeKey) ?: parent?.type) as? VehicleType<VehicleInfo>)
                    // 타입 정의가 없거나, 부모가 없거나 부모하고 타입이 같아야 함
                ?.takeIf { parent == null || it == parent.type }
                ?: return VehicleEngine.logErrorNull("유효하지 않은 VehicleType: ${typeKey}")

            val vehicle = type.create(key, section, configFile, parent)
            VehicleEngine.log("차량 불러옴: &a${key}")
            return vehicle
        }

        private fun updateVehicleEntities() {
            VehicleEntity.byUUID.forEach { id, entity ->
                val key = entity.base.key
                val newBase = byKey[key] ?: return@forEach
                entity.setBaseForced(newBase)
            }
        }

    }

}