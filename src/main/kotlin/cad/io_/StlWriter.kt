package cad.io_

import cad.kernel.mesh.Mesh
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.outputStream
import kotlin.math.sqrt

/**
 * Запись Mesh в STL — стандартный формат для обмена меши с MeshLab/Blender.
 * Binary STL компактнее ASCII в ~6 раз и единственная разумная опция для
 * любого нетривиального меша.
 *
 * Спецификация binary STL:
 *   - 80 байт header (произвольный текст, обычно ASCII)
 *   - uint32 LE — число треугольников
 *   - на треугольник: 3 × float32 (normal) + 3 × 3 × float32 (vertices) +
 *     uint16 attribute (нули). Итого 50 байт.
 */
object StlWriter {

    fun writeBinary(mesh: Mesh, path: Path, name: String = "cad") {
        Files.createDirectories(path.parent ?: path.toAbsolutePath().parent)
        path.outputStream().buffered().use { writeBinary(mesh, it, name) }
    }

    fun writeBinary(mesh: Mesh, out: OutputStream, name: String = "cad") {
        require(mesh.indices.size % 3 == 0) { "indices must be triangles" }
        val triCount = mesh.indices.size / 3

        val header = ByteArray(80)
        val nameBytes = name.toByteArray(Charsets.US_ASCII)
        System.arraycopy(nameBytes, 0, header, 0, nameBytes.size.coerceAtMost(header.size))
        out.write(header)

        val countBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(triCount)
        out.write(countBuf.array())

        // Один общий буфер на треугольник переиспользуется, чтобы не аллоцировать
        // 50 байт на каждый. На больших мешах это даёт заметный выигрыш.
        val triBuf = ByteBuffer.allocate(50).order(ByteOrder.LITTLE_ENDIAN)
        val v = mesh.vertices
        val idx = mesh.indices
        for (t in 0 until triCount) {
            val i0 = idx[3 * t] * 3
            val i1 = idx[3 * t + 1] * 3
            val i2 = idx[3 * t + 2] * 3
            val ax = v[i0];
            val ay = v[i0 + 1];
            val az = v[i0 + 2]
            val bx = v[i1];
            val by = v[i1 + 1];
            val bz = v[i1 + 2]
            val cx = v[i2];
            val cy = v[i2 + 1];
            val cz = v[i2 + 2]
            val ux = bx - ax;
            val uy = by - ay;
            val uz = bz - az
            val vx = cx - ax;
            val vy = cy - ay;
            val vz = cz - az
            var nx = uy * vz - uz * vy
            var ny = uz * vx - ux * vz
            var nz = ux * vy - uy * vx
            val len = sqrt(nx * nx + ny * ny + nz * nz)
            if (len > 0f) {
                nx /= len; ny /= len; nz /= len
            }

            triBuf.clear()
            triBuf.putFloat(nx).putFloat(ny).putFloat(nz)
            triBuf.putFloat(ax).putFloat(ay).putFloat(az)
            triBuf.putFloat(bx).putFloat(by).putFloat(bz)
            triBuf.putFloat(cx).putFloat(cy).putFloat(cz)
            triBuf.putShort(0)
            out.write(triBuf.array(), 0, 50)
        }
    }
}
