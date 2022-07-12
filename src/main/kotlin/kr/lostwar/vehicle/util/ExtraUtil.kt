package kr.lostwar.vehicle.util

import kr.lostwar.util.DrawUtil
import org.bukkit.util.BoundingBox
import org.bukkit.util.Vector

object ExtraUtil {
    fun BoundingBox.getOutline(divide: Int) : List<Vector> {
        val list = ArrayList<Vector>()
        val xRange = DrawUtil.getDoubleRange(minX, maxX, divide)
        val yRange = DrawUtil.getDoubleRange(minY, maxY, divide)
        val zRange = DrawUtil.getDoubleRange(minZ, maxZ, divide)
        val xInt = listOf(minX, maxX)
        val yInt = listOf(minY, maxY)
        val zInt = listOf(minZ, maxZ)
        for(y in yInt) {
            for(z in zInt) {
                for(x in xRange) list.add(Vector(x, y, z))
            }
            for(x in xInt) {
                for(z in zRange) list.add(Vector(x, y, z))
            }
        }
        for(x in xInt){
            for(z in zInt){
                for(y in yRange) list.add(Vector(x, y, z))
            }
        }
        return list
    }


}