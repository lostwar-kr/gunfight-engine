package kr.lostwar.gun.weapon

import kr.lostwar.gun.weapon.components.SelectorLever
import kr.lostwar.gun.weapon.event.WeaponEndHoldingEvent
import kr.lostwar.gun.weapon.event.WeaponStartHoldingEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.*

class Weapon(
    var type: WeaponType
) {
    enum class WeaponState {
        NOTHING,
        SHOOTING,
        RELOADING,
        NEED_OPEN,
        OPENING,
        CLOSING,
        PUSHING,
    }

    var holder: WeaponHolder? = null

    var primaryAction: WeaponAction? = null
        set(value) {
            field?.end()
            field = value
            value?.start()
        }

    private val backgroundActions = mutableListOf<WeaponAction>()
    fun addBackgroundAction(action: WeaponAction) {
        action.start()
        backgroundActions.add(action)
    }

    fun tick() {
        primaryAction?.tick()
        if(primaryAction?.isRunning == false) {
            primaryAction = null
        }
        for(action in backgroundActions){
            action.tick()
        }
        backgroundActions.removeAll { !it.isRunning }
    }

    private val properties = ArrayList<WeaponProperty<*, *>>()
    private fun <T : Any, Z : Any> register(
        type: WeaponPropertyType<T, Z>,
        defaultValue: Z,
    ): WeaponProperty<T, Z> {
        val container = WeaponProperty(type, defaultValue)
        properties.add(container)
        return container
    }

    var id: UUID by register(WeaponPropertyType.ID, UUID.randomUUID()); private set
    var ammo: Int by register(WeaponPropertyType.AMMO, type.ammo?.startAmount ?: 0)
    var aiming: Boolean by register(WeaponPropertyType.AIMING, false)
    var selector: SelectorLever.SelectorType by register(WeaponPropertyType.SELECTOR, type.selectorLever.defaultSelector)
    var state: WeaponState by register(WeaponPropertyType.STATE, WeaponState.NOTHING)


    fun WeaponPlayer.onStartHolding(event: WeaponStartHoldingEvent) {

    }
    fun WeaponPlayer.onEndHolding(event: WeaponEndHoldingEvent) {
        if(event.oldItem != null) {
            storeTo(event.oldItem)
        }
        primaryAction?.end()
        primaryAction = null
        backgroundActions.forEach { it.end() }
        backgroundActions.clear()
    }

    fun storeTo(item: ItemStack) {
        item.editMeta {
            val itemContainer = it.persistentDataContainer
            itemContainer[Constants.weaponKey, PersistentDataType.STRING] = type.key
            for(container in properties) {
                container.storeTo(itemContainer)
            }
        }
    }

    companion object {
        private var identifierCounter = 0
        fun takeOut(item: ItemStack?): Weapon? {
            if(item == null) return null
            if(!item.hasItemMeta()) return null
            val meta = item.itemMeta
            val itemContainer = meta.persistentDataContainer
            val key = itemContainer.get(Constants.weaponKey, PersistentDataType.STRING) ?: return null
            val info = WeaponType[key] ?: return null

            val weapon = Weapon(info)
            for(property in weapon.properties) {
//                try {
                    property.takeOut(itemContainer)
//                }catch (e: java.lang.Exception) {
//                    GunEngine.logWarn("exception occurred while take out ${property.type.key}:")
//                    e.printStackTrace()
//                }
            }
            return weapon
        }
    }



    override fun equals(other: Any?): Boolean {
        if(other is Weapon) {
            return hashCode() == other.hashCode()
        }
        return super.equals(other)
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + id.hashCode()
        return result
    }

    override fun toString(): String {
        return "${type.key}:${id}"
    }
}