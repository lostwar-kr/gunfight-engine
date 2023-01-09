package kr.lostwar.vehicle.core

import kr.lostwar.util.DrawUtil
import kr.lostwar.util.ExtraUtil.armorStandOffset
import kr.lostwar.util.math.VectorUtil
import kr.lostwar.util.math.VectorUtil.ZERO
import kr.lostwar.util.math.VectorUtil.minus
import kr.lostwar.util.math.VectorUtil.modifiedY
import kr.lostwar.util.math.VectorUtil.normalized
import kr.lostwar.util.math.VectorUtil.plus
import kr.lostwar.util.math.VectorUtil.times
import kr.lostwar.util.math.VectorUtil.toYawPitch
import kr.lostwar.util.ui.text.console
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.World
import org.bukkit.util.EulerAngle
import org.bukkit.util.Vector
import kotlin.math.cos
import kotlin.math.sin

class VehicleTransform(
    var position: Vector,
    private var rotation: Vector = Vector(),
) : Cloneable {

    var eulerRotation: Vector
        get() = rotation
        set(value) {
            val oldRotation = rotation
            rotation = value
            if(oldRotation == rotation) return

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
            forwardDirection = Vector(
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
            ).normalize().multiply(-1)

            // just cross product forward and right
            up = right.getCrossProduct(forward).normalize()

            eulerAngleForPose = EulerAngle(pitch, 0.0, -roll)
//            console("angle            : ${"&c%6.3f&r, &a%6.3f&r, &9%6.3f".format(pitch, yaw, roll)}")
//            console("eulerAngleForPose: ${eulerAngleForPose.run { "&c%6.3f&r, &a%6.3f&r, &9%6.3f".format(x, y, z) }}")
//            console("x, y, z : ${"&c%6.3f&r, &a%6.3f&r, &9%6.3f".format(right.length(), up.length(), forward.length())}")
        }
    var eulerAngleForPose = EulerAngle.ZERO; private set

    // local Z
    private var forwardDirection: Vector = Vector(0.0, 0.0, 1.0)
    var forward: Vector
        get() = forwardDirection
        set(value) {
            val forward = value.normalized
            forwardDirection = forward
            val (yaw, pitch) = forward.toYawPitch()
            // 다소 무지성하긴 한데 ... 마인크래프트 회전 좌표계는 yaw가 반대로 설정됨
            // 가슴이웅장해진다...모장은진짜레전드게임사
            rotation = Vector(pitch, -yaw, 0f)
            eulerAngleForPose = EulerAngle(pitch.toDouble(), 0.0, 0.0)
            right = forward.getCrossProduct(VectorUtil.UP).normalize()
            up = right.getCrossProduct(forward).normalize()
//            console("x, y, z(set forward) : ${"&c%6.3f&r, &a%6.3f&r, &9%6.3f".format(right.length(), up.length(), forward.length())}")

//            DrawUtil.drawPoints(
//                DrawUtil.getRay(position, forward, 10)  ,
//                Particle.DustOptions(Color.BLUE   , 0.5f))
//            DrawUtil.drawPoints(
//                DrawUtil.getRay(position, right, 10)    ,
//                Particle.DustOptions(Color.RED    , 0.5f))
//            DrawUtil.drawPoints(
//                DrawUtil.getRay(position, up, 10)       ,
//                Particle.DustOptions(Color.GREEN  , 0.5f))
        }
    // local X
    var right: Vector = VectorUtil.RIGHT; private set
    // local Y
    var up: Vector = VectorUtil.UP; private set

    fun copyRotation(from: VehicleTransform) {
        rotation = from.rotation.clone()
        eulerAngleForPose = from.eulerAngleForPose
        forward = from.forward.clone()
        right = from.right.clone()
        up = from.up.clone()
    }

    fun applyRotation(localPosition: Vector): Vector {
        val tx = localPosition.x
        val ty = localPosition.y
        val tz = localPosition.z

        val x = right
        val y = up
        val z = forward

        return Vector(
            tx * x.x + ty * y.x + tz * z.x,
            tx * x.y + ty * y.y + tz * z.y,
            tx * x.z + ty * y.z + tz * z.z,
        )
    }
    fun applyRotationOnlyYaw(localPosition: Vector): Vector {
        val tx = localPosition.x
        val ty = localPosition.y
        val tz = localPosition.z

        val x = right.modifiedY(0.0).normalize()
        val y = VectorUtil.UP
        val z = forward.modifiedY(0.0).normalize()

        return Vector(
            tx * x.x + ty * y.x + tz * z.x,
            tx * x.y + ty * y.y + tz * z.y,
            tx * x.z + ty * y.z + tz * z.z,
        )
    }
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
    fun localToWorld(childLocal: VehicleTransform, childWorld: VehicleTransform, ignoreRotation: Boolean = false) {
        childWorld.position = localToWorld(childLocal.position)

        if(!ignoreRotation)
            childWorld.eulerRotation = rotation.clone().add(childLocal.rotation)
//        else
//            childWorld.eulerRotation = rotation.clone()
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
        return transform(info)
            .toLocation(world)
            .setDirection(forward)
    }

    fun toLocation(world: World) = position.toLocation(world).setDirection(forward)

    public override fun clone(): VehicleTransform {
        return VehicleTransform(position.clone(), rotation.clone()).also {
            it.eulerAngleForPose = eulerAngleForPose // immutable
            it.forward = forward.clone()
            it.right = right.clone()
            it.up = up.clone()
        }
    }

    companion object {
        val Vector.eulerYaw     ; get() = y
        val Vector.eulerPitch   ; get() = x
        val Vector.eulerRoll    ; get() = z
    }
}