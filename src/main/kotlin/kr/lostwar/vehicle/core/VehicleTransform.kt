package kr.lostwar.vehicle.core

import kr.lostwar.util.ExtraUtil.armorStandOffset
import kr.lostwar.util.math.VectorUtil
import kr.lostwar.util.math.VectorUtil.times
import kr.lostwar.util.math.VectorUtil.plus
import org.bukkit.Location
import org.bukkit.World
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
            )
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
            )

            // just cross product forward and right
            up = forward.getCrossProduct(right)
        }

    var forward: Vector = VectorUtil.FORWARD; private set
    var right: Vector = VectorUtil.RIGHT; private set
    var up: Vector = VectorUtil.UP; private set

    fun localToWorld(localPosition: Vector): Vector {
        return  (forward * localPosition.z)
            .add(right * localPosition.x)
            .add(up * localPosition.y)
            .add(position)
    }

    fun transform(info: VehicleModelInfo, world: World): Location {
        return localToWorld(info.localPosition + info.type.armorStandOffset)
            .toLocation(world)
            .setDirection(forward)
    }

    companion object {
        val Vector.eulerYaw; get() = y.toFloat()
        val Vector.eulerPitch; get() = x.toFloat()
        val Vector.eulerRoll; get() = z.toFloat()
    }
}