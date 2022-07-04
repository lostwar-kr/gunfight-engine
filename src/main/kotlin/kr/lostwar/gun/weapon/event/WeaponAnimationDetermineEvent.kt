package kr.lostwar.gun.weapon.event

import kr.lostwar.gun.weapon.WeaponPlayer
import kr.lostwar.util.AnimationClip

class WeaponAnimationDetermineEvent(
    player: WeaponPlayer,
    val type: Type,
    var animationClip: AnimationClip,
) : WeaponPlayerEvent(player) {
    enum class Type {
        SINGLE_SHOOT,
        FULL_AUTO_SHOOT_LOOP,
        FULL_AUTO_SHOOT_STOP,
        BOLT_OPEN,
        BOLT_CLOSE,
        RELOAD_START,
        RELOAD,
        RELOAD_END,
        ;
        fun create(player: WeaponPlayer, clip: AnimationClip)
            = WeaponAnimationDetermineEvent(player, this, clip)
    }

    fun callEventAndGetClip(): AnimationClip {
        callEventOnHoldingWeapon()
        return animationClip
    }
}