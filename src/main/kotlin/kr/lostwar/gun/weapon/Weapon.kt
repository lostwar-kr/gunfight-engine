package kr.lostwar.gun.weapon

import kr.lostwar.gun.GunEngine
import kr.lostwar.gun.weapon.WeaponPropertyType.Companion.get
import kr.lostwar.gun.weapon.WeaponPropertyType.Companion.set
import kr.lostwar.gun.weapon.event.WeaponActionEndEvent
import kr.lostwar.gun.weapon.event.WeaponActionStartEvent
import kr.lostwar.gun.weapon.event.WeaponEndHoldingEvent
import kr.lostwar.gun.weapon.event.WeaponPlayerEvent.Companion.callEventOnHoldingWeapon
import kr.lostwar.gun.weapon.event.WeaponStartHoldingEvent
import kr.lostwar.util.ui.ComponentUtil.appendText
import kr.lostwar.util.ui.ComponentUtil.green
import kr.lostwar.util.ui.ComponentUtil.white
import kr.lostwar.util.ui.ComponentUtil.yellow
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.*
import kotlin.collections.HashMap

class Weapon(
    var type: WeaponType
) {
    enum class WeaponState {
        NOTHING,
        SHOOTING,
        LOADING,
    }

    var player: WeaponPlayer? = null

    var primaryAction: WeaponAction? = null
        set(value) {
            val old = field
            val new = if(old?.end() == true) {
                player?.let { player ->
                    WeaponActionEndEvent(player, old, value)
                        .callEventOnHoldingWeapon()
                        .newAction
                }
            }else value

            field = new
            if(new?.start() == true) {
                player?.let { player -> WeaponActionStartEvent(player, old, new).callEventOnHoldingWeapon() }
            }
        }

    private val backgroundActions = mutableListOf<WeaponAction>()
    fun addBackgroundAction(action: WeaponAction) {
        action.start()
        backgroundActions.add(action)
    }

    fun tick() {
        primaryAction?.let {
            it.tick()
            // 이번 틱에서 끝나버린 경우
            if(!it.isRunning) {
                primaryAction = player?.let {
                        player -> WeaponActionEndEvent(player, it, null)
                    .callEventOnHoldingWeapon()
                    .newAction
                }

            }
        }
        for(action in backgroundActions){
            action.tick()
        }
        backgroundActions.removeAll { !it.isRunning }
    }

    private val propertyMap = HashMap<WeaponPropertyType<*, *>, WeaponProperty<*, *>>()
    fun <T : Any, Z : Any> registerNotNull(
        type: WeaponPropertyType<T, Z>,
        defaultValue: Z,
    ): WeaponPropertyNotNull<T, Z> {
        val property = WeaponPropertyNotNull(type, defaultValue)
        propertyMap[type] = property
        return property
    }
    fun <T : Any, Z : Any> registerNullable(
        type: WeaponPropertyType<T, Z>,
        defaultValue: Z? = null,
    ): WeaponPropertyNullable<T, Z> {
        val property = WeaponPropertyNullable(type, defaultValue)
        propertyMap[type] = property
        return property
    }
    private fun <T : Any, Z : Any> getProperty(type: WeaponPropertyType<T, Z>): WeaponProperty<T, Z>? {
//        val rawProperty = propertyMap.computeIfAbsent(type) { registerNullable(type) }
        val rawProperty = propertyMap[type] ?: return null
        @Suppress("UNCHECKED_CAST")
        return rawProperty as WeaponProperty<T, Z>
    }
    operator fun <T : Any, Z : Any> get(type: WeaponPropertyType<T, Z>): Z? {
        return getProperty(type)?.value
    }
    operator fun <T : Any, Z : Any> set(type: WeaponPropertyType<T, Z>, value: Z?) {
        getProperty(type)?.value = value
    }

    var id: UUID by registerNotNull(WeaponPropertyType.ID, UUID.randomUUID()); private set
    var state: WeaponState by registerNotNull(WeaponPropertyType.STATE, WeaponState.NOTHING)

    init {
        type.enabledComponents.forEach { component -> component.onInstantiate(this) }
    }

    // newItem으로부터 이미 데이터는 받은 상태
    fun WeaponPlayer.onStartHolding(event: WeaponStartHoldingEvent) {
        primaryAction = null
    }
    fun WeaponPlayer.onEndHolding(event: WeaponEndHoldingEvent) {
        primaryAction = null
        backgroundActions.forEach { it.end() }
        backgroundActions.clear()
        if(event.oldItem != null) {
            storeTo(event.oldItem)
        }
    }

    fun storeTo(item: ItemStack) {
        item.editMeta { meta ->
            val itemContainer = meta.persistentDataContainer
            // 컨테이너가 있으면 가져오고, 없으면 만들고 넣음
            val weaponContainer = itemContainer.get(Constants.weaponContainerKey, PersistentDataType.TAG_CONTAINER)
                ?: itemContainer.adapterContext.newPersistentDataContainer()
                    .also { itemContainer.set(Constants.weaponContainerKey, PersistentDataType.TAG_CONTAINER, it) }
            weaponContainer[WeaponPropertyType.KEY] = type.key
            for(container in propertyMap.values) {
                container.storeTo(weaponContainer)
            }
            itemContainer.set(Constants.weaponContainerKey, PersistentDataType.TAG_CONTAINER, weaponContainer)
        }
    }

    companion object {
        fun takeOut(item: ItemStack?): Weapon? {
            if(item == null) return null
            if(!item.hasItemMeta()) return null
            val meta = item.itemMeta
            val itemContainer = meta.persistentDataContainer
            val weaponContainer = itemContainer.get(Constants.weaponContainerKey, PersistentDataType.TAG_CONTAINER) ?: return null
            val key = weaponContainer[WeaponPropertyType.KEY]
                ?: return GunEngine.logErrorNull("cannot Weapon::takeOut from $item: container is valid, but cannot find key")
            val info = WeaponType[key]
                ?: return GunEngine.logErrorNull("cannot Weapon::takeOut from $item: find key, but invalid weapon")

            val weapon = Weapon(info)
            for(property in weapon.propertyMap.values) {
                // takeOut에 실패할 수도 있음 (UUID를 못가져온다던가)
                if(!property.takeOut(weaponContainer)) {
                    return GunEngine.logErrorNull("cannot Weapon::takeOut from $item: valid weapon info, invalid identifier")
                }
            }
            return weapon
        }

        private val separator = text(", ").white()
        private val prefix = text("[").white()
        private val suffix = text("]").white()
        private val displayJoinConfiguration = JoinConfiguration.builder()
            .separator(separator)
            .prefix(prefix)
            .suffix(suffix)
            .build()
    }



    override fun equals(other: Any?): Boolean {
        if(other is Weapon) {
            return type == other.type && id == other.id
        }
        return super.equals(other)
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + id.hashCode()
        return result
    }

    override fun toString(): String {
        return "${type.key}${propertyMap.values.filter { it.value != null && it.value != it.defaultValue }.joinToString(",", "{", "}") { it.type.key+"="+it.value }}"
    }

    fun toDisplayComponent(): Component {
        val properties = propertyMap.values
            .filter { it.value != null && it.value != it.defaultValue }
            .map { text(it.type.key).green().appendText("="){white()}.appendText(it.value.toString()){yellow()} }
        return text(type.key).color(NamedTextColor.YELLOW)
            .append(Component.join(displayJoinConfiguration, properties))
    }


}