package kr.lostwar.gun.weapon

import kr.lostwar.gun.GunEngine
import kr.lostwar.gun.weapon.WeaponComponent.Companion.index
import kr.lostwar.gun.weapon.components.*
import kr.lostwar.gun.weapon.event.WeaponEndHoldingEvent
import kr.lostwar.gun.weapon.event.WeaponStartHoldingEvent
import kr.lostwar.util.Config
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.inventory.ItemStack
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet

class WeaponType(
    val key: String,
    val section: ConfigurationSection,
    val config: Config,
) {
    private val parentWeaponKey =
        if (key != rootWeaponKey) (section.getString("parent") ?: rootWeaponKey)
        else null
    var loaded = false
    var valid = true

    val tags = HashSet<String>()

    //    var storeDataToItemStack: Boolean = false; private set
    val components = Array<WeaponComponent?>(WeaponComponent.registeredComponents.size) { null }
    lateinit var enabledComponents: List<WeaponComponent>
    fun <T : WeaponComponent> getComponent(clazz: Class<T>): T? {
        val component = components[clazz.index] ?: return null
        @Suppress("UNCHECKED_CAST")
        return (component as? T)
    }

    fun hasComponent(clazz: Class<out WeaponComponent>): Boolean {
        return components[clazz.index] != null
    }

    val listenerHandler = HashMap<Class<out Event>, MutableList<WeaponPlayerEventListener<Event>>>()
    fun registerListeners(listeners: List<WeaponPlayerEventListener<out Event>>) {
        listeners.forEach { listener ->
//            GunEngine.log("- ${listener.clazz}")
            val list = listenerHandler
                // 없으면 리스트 생성
                .getOrPut(listener.clazz) { ArrayList() }
            // 리스트에 넣기
            list.add(listener as WeaponPlayerEventListener<Event>)
        }
    }
    private val weaponTypeListeners = listOf(
        WeaponPlayerEventListener(WeaponStartHoldingEvent::class.java, EventPriority.LOWEST) { event ->
            val weapon = event.weapon
            with(weapon) { onStartHolding(event) }
        },
        WeaponPlayerEventListener(WeaponEndHoldingEvent::class.java) {event ->
            val weapon = event.weapon
            with(weapon) { onEndHolding(event) }
        },
    )
    inline fun <reified T : Event> callEvent(weaponPlayer: WeaponPlayer, event: T) {
//        GunEngine.log("callEvent(${weaponPlayer.player}, ${T::class.java.simpleName})")
        val list = listenerHandler[T::class.java] ?: return
//        GunEngine.log("listener list: [${list.size}]")
        val cancellable = event as? Cancellable ?: WeaponPlayerEventListener.notCancelled
        for(listener in list) {
            if(listener.ignoreCancelled && cancellable.isCancelled) {
                continue
            }
            listener.callEvent(weaponPlayer, event)
        }
    }

    lateinit var item: Item; private set
    var ammo: Ammo? = null; private set
    var fullAuto: FullAuto? = null; private set
    var burst: Burst? = null; private set
    lateinit var selectorLever: SelectorLever; private set
    lateinit var hit: Hit; private set
    lateinit var spread: Spread; private set
    var shoot: Shoot? = null; private set
    var zoom: Zoom? = null; private set
    var recoil: Recoil? = null; private set
    var explosion: Explosion? = null; private set
    private fun registerComponentAliases() {
        item = getComponent(Item::class.java)!!
        burst = getComponent(Burst::class.java)
        fullAuto = getComponent(FullAuto::class.java)
        selectorLever = getComponent(SelectorLever::class.java)!!
        hit = getComponent(Hit::class.java)!!
        spread = getComponent(Spread::class.java)!!
        ammo = getComponent(Ammo::class.java)
        shoot = getComponent(Shoot::class.java)
        zoom = getComponent(Zoom::class.java)
        recoil = getComponent(Recoil::class.java)
        explosion = getComponent(Explosion::class.java)
    }

    private fun load(parentWeapon: WeaponType?) {
        parentWeapon?.let { parent ->
            tags.addAll(parent.tags)
        }
        section.getString("tags")?.let { rawTags ->
            tags.addAll(rawTags.split(',').map { it.trim() })
        }
//        storeDataToItemStack = section.getBoolean("storeDataToItemStack", parentWeapon?.storeDataToItemStack ?: false)

        for ((clazz, constructor) in WeaponComponent.registeredComponentsWithConstructor) {
            fun failMessage(e: Exception) {
                GunEngine.logWarn("Component ${clazz.simpleName}를 불러오는 데 실패했습니다.")
                e.printStackTrace()
            }
            try {
                val moduleSection = section.getConfigurationSection(clazz.simpleName)
                val component = constructor.newInstance(moduleSection, this, parentWeapon?.getComponent(clazz))
                components[clazz.index] = component
            } catch (e: InvocationTargetException) {
                if (e.targetException is WeaponComponentDisableException) {
                    continue
                }
                failMessage(e)
                continue
            } catch (e: java.lang.Exception) {
                failMessage(e)
                continue
            }
        }
        registerComponentAliases()
        if (!valid) {
            GunEngine.logWarn("무기 &e${key}&6(parent: &e${parentWeaponKey}&6)&r를 불러오는 데 실패")
            return
        }
//        GunEngine.log("registering weapon type listeners ...")
        registerListeners(weaponTypeListeners)
        enabledComponents = components.filterNotNull()
        enabledComponents.forEach {
            component -> component.lateInit()
        }
        listenerHandler.values.forEach { list -> list.sortBy { it.priority } }
        GunEngine.log("무기 불러옴: &a${key}${if (parentWeaponKey != rootWeaponKey && parentWeaponKey != null) "&2(parent: &a${parentWeaponKey}&2)" else ""}&r")
        loaded = true
    }

    companion object {

        private val rootWeaponKey = "__root__"

        val weaponsByKey = HashMap<String, WeaponType>()
        operator fun get(key: String) = weaponsByKey[key]

        fun load() {
            weaponsByKey.clear()

            val weaponPath = GunEngine.directory + "weapons/"

            val rootWeaponConfig = Config(weaponPath + "root.yml")
            register(rootWeaponKey, rootWeaponConfig, rootWeaponConfig)

            val folder = File(weaponPath)
            val files = folder.listFiles()
            if (files == null || files.isEmpty()) {
                GunEngine.logWarn("불러올 무기가 없습니다. ${weaponPath} 폴더 안에 무기 파일을 추가하세요.")
                return
            }
            registerWeaponFiles(folder)
            loadWeapons()


        }

        private fun registerWeaponFiles(folder: File) {
            val queue = LinkedList<File>()
            queue.addFirst(folder)

            val weaponFiles = arrayListOf<File>()
            while (queue.isNotEmpty()) {
                val file = queue.poll()
                val fileName = file.name
                if(fileName.startsWith("-")) {
                    GunEngine.log("skip loading ${file.path}")
                    continue
                }
                if (file.isDirectory) {
                    val files = file.listFiles() ?: continue
                    queue.addAll(files)
                    continue
                }
                if(fileName.endsWith(".yml")) {
                    weaponFiles.add(file)
                }
            }

            GunEngine.log("${weaponFiles.size}개의 파일 확인 중 ...")
            weaponFiles.forEach { file ->
                val config = Config(file)
                for (key in config.getKeys(false)) {
                    val section = config.getConfigurationSection(key)
                    if (section == null) {
                        GunEngine.logWarn("${config.file.path}: ${key}는 유효한 section이 아님")
                        continue
                    }
                    register(key, section, config)
                }
            }
        }

        private fun register(key: String, section: ConfigurationSection, config: Config): WeaponType? {
            if (key in weaponsByKey) {
                return GunEngine.logErrorNull("중복 무기 key 발생: ${key}, ${config.file.path} 파일 불러오는 중 발생")
            }

            val weapon = WeaponType(key, section, config)
            weaponsByKey[key] = weapon

            if (weapon.parentWeaponKey == null) {
                weapon.load(null)
                return weapon
            }

            return weapon
        }

        private fun loadWeapons(): Int {
            val childWeapons = HashSet(weaponsByKey.values.filter { it.key != rootWeaponKey })
            var count = 0
            var level = 0
            while (childWeapons.size > 0) {
                GunEngine.log("level ${level}:")
                val iterator = childWeapons.iterator()
                while (iterator.hasNext()) {
                    val weapon = iterator.next()
                    val parent = weapon.parentWeaponKey
                    // 부모 무기가 없으면 무시
                    val parentWeapon = parent?.let { weaponsByKey[it] }
                    if (parentWeapon == null) {
                        iterator.remove()
                        GunEngine.logWarn("${weapon.key}의 부모 무기가 존재하지 않음: ${parent}")
                        continue
                    }
                    if (!parentWeapon.loaded) {
                        continue
                    }
                    weapon.load(parentWeapon)
                    iterator.remove()
                    ++count
                }
                ++level
                if (level >= 50) {
                    GunEngine.logWarn("50level 이상 무기 상속 중단, 남은 무기 ${childWeapons.size}개")
                    childWeapons.forEach { GunEngine.logWarn(" - ${it.key} : ${it.parentWeaponKey}") }
                    break
                }
            }
            return count
        }
    }

    fun instantiate(): Pair<ItemStack, Weapon> {
        val item = item.itemStack
        val weapon = Weapon(this)
        weapon.storeTo(item)
        return item to weapon
    }

    override fun toString(): String {
        return key
    }

    override fun hashCode(): Int {
        return key.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other is WeaponType) {
            return hashCode() == other.hashCode()
        }
        return super.equals(other)
    }
}