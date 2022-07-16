package kr.lostwar.vehicle.core

import kr.lostwar.util.ExtraUtil.armorStandOffset
import kr.lostwar.util.math.VectorUtil
import kr.lostwar.util.math.VectorUtil.ZERO
import kr.lostwar.util.math.VectorUtil.minus
import kr.lostwar.util.math.VectorUtil.times
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.util.EulerAngle
import org.bukkit.util.Vector
import kotlin.math.cos
import kotlin.math.sin

class VehicleTransform(
    position: Vector,
    eulerRotation: Vector,
) {
    var position: Vector = position
    var eulerRotation: Vector = eulerRotation
        set(value) {
            field = value

            val yaw = value.y
            val pitch = value.x
            val roll = value.z

            val sinYaw = sin(yaw); val sinPitch = sin(pitch); val sinRoll = sin(roll)
            val cosYaw = cos(yaw); val cosPitch = cos(pitch); val cosRoll = cos(roll)

            /*
            forward = Vector(0, 0, 1)
            .rotateAroundX(pitch)
            .rotateAroundY(yaw)
             */
            // direction from yaw and pitch
            forward = Vector(
                cosPitch * sinYaw,
                -sinPitch,
                cosPitch * cosYaw,
            ).normalize()
/*
            right = Vector(1, 0, 0)
                .rotateAroundY(yaw)
                .rotateAroundAxis(forward, roll)

            right = (
                cosYaw,
                0.0,
                -sinYaw
            )

            right = (
                cosYaw * cosRoll + (-forward.z * 0.0 + forward.y * -sinYaw) * sinRoll;
                0.0 * cosTheta + (forward.z * cosYaw - forward.x * -sinYaw) * sinRoll;
                -sinYaw * cosRoll + (-forward.y * cosYaw + forward.x * 0.0) * sinRoll;
            )
*/
            // rotate right(1, 0, 0) vector by yaw, and rotate by forward axis
            right = Vector(
                + cosYaw * cosRoll - (forward.y * sinYaw) * sinRoll,
                + (forward.z * cosYaw + forward.x * sinYaw) * sinRoll,
                - sinYaw * cosRoll - (forward.y * cosYaw) * sinRoll,
            ).normalize()

            // just cross product forward and right
            up = forward.getCrossProduct(right).normalize()

            eulerAngleForPose = EulerAngle(pitch, 0.0, -roll)
        }
    var eulerAngleForPose = EulerAngle.ZERO; private set

    // local Z
    var forward: Vector = VectorUtil.FORWARD; private set
    // local X
    var right: Vector = VectorUtil.RIGHT; private set
    // local Y
    var up: Vector = VectorUtil.UP; private set

    fun localToWorld(localPosition: Vector): Vector {
        val tx = localPosition.x
        val ty = localPosition.y
        val tz = localPosition.z

        val x = right
        val y = up
        val z = forward

        /*
        return  (forward * localPosition.z)
            .add(right * localPosition.x)
            .add(up * localPosition.y)
            .add(position)
         */

        return Vector(
            tx * x.x + ty * y.x + tz * z.x + position.x,
            tx * x.y + ty * y.y + tz * z.y + position.y,
            tx * x.z + ty * y.z + tz * z.z + position.z,
        )

    }

    fun transform(info: VehicleModelInfo): Vector {
        val localPosition = info.localPosition -
                // 히트박스가 없는 경우는 모델 엔티티로 판단함, 아이템 오프셋 적용
                if(info.hitbox.isEmpty()) info.type.armorStandOffset
                // 히트박스가 있으면 일반 엔티티로 판단함, localPosition 만 적용
                else ZERO
        return localToWorld(localPosition)
    }
    fun transform(info: VehicleModelInfo, world: World): Location {
        val localPosition = info.localPosition -
                // 히트박스가 없는 경우는 모델 엔티티로 판단함, 아이템 오프셋 적용
                if(info.hitbox.isEmpty()) info.type.armorStandOffset
                // 히트박스가 있으면 일반 엔티티로 판단함, localPosition 만 적용
                else ZERO
        return localToWorld(localPosition)
            .toLocation(world)
            .setDirection(forward)
    }

    companion object {
        val Vector.eulerYaw; get() = y.toFloat()
        val Vector.eulerPitch; get() = x.toFloat()
        val Vector.eulerRoll; get() = z.toFloat()
    }
}