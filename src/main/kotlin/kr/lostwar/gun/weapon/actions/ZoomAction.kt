package kr.lostwar.gun.weapon.actions

import kr.lostwar.gun.weapon.Weapon
import kr.lostwar.gun.weapon.WeaponAction
import kr.lostwar.gun.weapon.components.Zoom.Companion.isZooming

class ZoomAction(
    weapon: Weapon,
    val isZooming: Boolean,
) : WeaponAction(weapon) {
    val zoom = weapon.type.zoom ?: error("ZoomAction created but weapon ${weapon} doesn't have Zoom component")
    var count = if(isZooming) zoom.zoomDuration else zoom.unzoomDuration
    val animation = if(isZooming) zoom.zoomAnimation else zoom.unzoomAnimation
    override fun onStart() {
        animation.play(player, weapon.type)
        if(isZooming && zoom.applyZoomEffectImmediately) {
            weapon.isZooming = true
            with(zoom) { player.zoomEffect(true) }
        }else if(!isZooming && !zoom.applyUnzoomEffectLazy) {
            weapon.isZooming = false
            with(zoom) { player.zoomEffect(false) }
        }
    }
    override fun onTick() {
        if(count > 0){
            --count
            return
        }
        end()
    }

    override fun onEnd() {
        // 중단된 경우에는 효과 적용하지 않음
        if(count > 0) {
            return
        }
        if(isZooming && !zoom.applyZoomEffectImmediately) {
            weapon.isZooming = true
            with(zoom) { player.zoomEffect(true) }
        }else if(!isZooming && zoom.applyUnzoomEffectLazy) {
            weapon.isZooming = false
            with(zoom) { player.zoomEffect(false) }
        }
    }
}