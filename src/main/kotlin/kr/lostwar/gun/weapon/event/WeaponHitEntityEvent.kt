package kr.lostwar.gun.weapon.event

import kr.lostwar.gun.weapon.WeaponPlayer
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.event.Cancellable
import org.bukkit.event.HandlerList

class WeaponHitEntityEvent(
    player: WeaponPlayer,
    val victim: LivingEntity,
    var damage: Double,
    val damageSource: Entity?,
    val distance: Double,
    val location: Location?,
    val isHeadShot: Boolean,
    val isPiercing: Boolean,
) : WeaponPlayerEvent(player), Cancellable {

    enum class DamageResult(val cancel: Boolean) {
        DAMAGE(false),
        PIERCE_NO_DAMAGE(true),
        IGNORE(true),
    }

    var result: DamageResult = DamageResult.DAMAGE

    override fun isCancelled(): Boolean = result.cancel
    @Deprecated("not recommended", replaceWith = ReplaceWith("result"))
    override fun setCancelled(value: Boolean) {
        result = if(value) DamageResult.IGNORE else DamageResult.DAMAGE
    }

    companion object {
        @JvmStatic val handlerList = HandlerList()
    }
    override fun getHandlers(): HandlerList = handlerList
}