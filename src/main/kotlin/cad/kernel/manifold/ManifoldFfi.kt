package cad.kernel.manifold

import cad.domain.feature.BoolOp
import cad.domain.feature.Profile2D
import cad.kernel.manifold.ManifoldFfi.deleteManifold
import cad.kernel.mesh.Mesh
import cad.native_.manifold.ManifoldVec2
import cad.native_.manifold.Manifoldc
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

/**
 * Low-level Panama wrapper над сгенерированным jextract'ом `Manifoldc`.
 *
 * Контракт времени жизни:
 *   - Все буферы (mem-аргументы для placement-new) живут в [Arena].
 *   - Сами Manifold-объекты держат геометрию на heap C++, поэтому требуют явного
 *     destructor-вызова `manifold_delete_*` — этим занимается caller через
 *     try/finally или [withManifold].
 *
 * Сторона MeshGL: возвращаемые `vert_properties`/`tri_verts` копируются
 * в Java-arrays и сразу освобождаются.
 */
internal object ManifoldFfi {

    // === Polygons / CrossSection ==========================================

    /** Создаёт CrossSection из одного замкнутого контура. Lifetime — [arena]. */
    fun crossSectionFromSimplePolygon(arena: Arena, profile: Profile2D): MemorySegment {
        require(profile.points.isNotEmpty()) { "empty profile" }
        // ManifoldVec2[points.size]
        val vec2Layout = ManifoldVec2.layout()
        val pts = arena.allocate(vec2Layout, profile.points.size.toLong())
        profile.points.forEachIndexed { i, p ->
            val slot = pts.asSlice(i * vec2Layout.byteSize(), vec2Layout)
            ManifoldVec2.x(slot, p.x)
            ManifoldVec2.y(slot, p.y)
        }

        val polyBuf = arena.allocate(Manifoldc.manifold_simple_polygon_size())
        val simple = Manifoldc.manifold_simple_polygon(polyBuf, pts, profile.points.size.toLong())

        // FillRule = 0 (EvenOdd) — стандарт для простых замкнутых контуров без дыр.
        val csBuf = arena.allocate(Manifoldc.manifold_cross_section_size())
        return Manifoldc.manifold_cross_section_of_simple_polygon(csBuf, simple, 0)
    }

    // === Extrude / Boolean ================================================

    /**
     * Выдавливание 2D-сечения на высоту [depth] вдоль +Y.
     * Возвращает manifold-handle; вызывающий обязан позвать [deleteManifold].
     */
    fun extrude(arena: Arena, cs: MemorySegment, depth: Double): MemorySegment {
        val buf = arena.allocate(Manifoldc.manifold_manifold_size())
        return Manifoldc.manifold_extrude(buf, cs, depth, 0, 0.0, 1.0, 1.0)
    }

    fun boolean(arena: Arena, a: MemorySegment, b: MemorySegment, op: BoolOp): MemorySegment {
        val buf = arena.allocate(Manifoldc.manifold_manifold_size())
        val code = when (op) {
            BoolOp.UNION -> Manifoldc.MANIFOLD_ADD()
            BoolOp.DIFFERENCE -> Manifoldc.MANIFOLD_SUBTRACT()
            BoolOp.INTERSECTION -> Manifoldc.MANIFOLD_INTERSECT()
        }
        return Manifoldc.manifold_boolean(buf, a, b, code)
    }

    /**
     * Mesh → Manifold через MeshGL. Нужен для boolean(Mesh, Mesh, op): сначала
     * загоняем оба меша обратно в нативку, потом делаем CSG.
     */
    fun manifoldOfMesh(arena: Arena, mesh: Mesh): MemorySegment {
        val nVerts = mesh.vertexCount.toLong()
        val nTris = (mesh.indices.size / 3).toLong()
        // MeshGL вершинные пропсы — float[]; первые 3 — позиция. Нормали отдадим тоже,
        // итого numProp = 6. Это формат, который Manifold принимает и возвращает.
        val numProp = if (mesh.normals.isNotEmpty()) 6L else 3L
        val vertProps = arena.allocate(ValueLayout.JAVA_FLOAT, nVerts * numProp)
        for (i in 0 until mesh.vertexCount) {
            val base = i * 3
            vertProps.setAtIndex(ValueLayout.JAVA_FLOAT, i * numProp + 0, mesh.vertices[base])
            vertProps.setAtIndex(ValueLayout.JAVA_FLOAT, i * numProp + 1, mesh.vertices[base + 1])
            vertProps.setAtIndex(ValueLayout.JAVA_FLOAT, i * numProp + 2, mesh.vertices[base + 2])
            if (numProp == 6L) {
                vertProps.setAtIndex(ValueLayout.JAVA_FLOAT, i * numProp + 3, mesh.normals[base])
                vertProps.setAtIndex(ValueLayout.JAVA_FLOAT, i * numProp + 4, mesh.normals[base + 1])
                vertProps.setAtIndex(ValueLayout.JAVA_FLOAT, i * numProp + 5, mesh.normals[base + 2])
            }
        }
        val triVerts = arena.allocate(ValueLayout.JAVA_INT, nTris * 3)
        for (i in mesh.indices.indices) {
            triVerts.setAtIndex(ValueLayout.JAVA_INT, i.toLong(), mesh.indices[i])
        }

        val mglBuf = arena.allocate(Manifoldc.manifold_meshgl_size())
        val meshgl = Manifoldc.manifold_meshgl(mglBuf, vertProps, nVerts, numProp, triVerts, nTris)
        val mBuf = arena.allocate(Manifoldc.manifold_manifold_size())
        val manifold = Manifoldc.manifold_of_meshgl(mBuf, meshgl)
        Manifoldc.manifold_delete_meshgl(meshgl)
        return manifold
    }

    // === Manifold → Mesh ==================================================

    /** Вычитывает MeshGL из manifold-handle и копирует в [Mesh]. */
    fun toMesh(arena: Arena, manifold: MemorySegment): Mesh {
        val mglBuf = arena.allocate(Manifoldc.manifold_meshgl_size())
        val mgl = Manifoldc.manifold_get_meshgl(mglBuf, manifold)
        try {
            val numProp = Manifoldc.manifold_meshgl_num_prop(mgl)
            val numVert = Manifoldc.manifold_meshgl_num_vert(mgl)
            val numTri = Manifoldc.manifold_meshgl_num_tri(mgl)
            if (numVert == 0 || numTri == 0) return Mesh.EMPTY

            val vpLen = Manifoldc.manifold_meshgl_vert_properties_length(mgl)
            val triLen = Manifoldc.manifold_meshgl_tri_length(mgl)

            val vpBuf = arena.allocate(ValueLayout.JAVA_FLOAT, vpLen)
            Manifoldc.manifold_meshgl_vert_properties(vpBuf, mgl)
            val triBuf = arena.allocate(ValueLayout.JAVA_INT, triLen)
            Manifoldc.manifold_meshgl_tri_verts(triBuf, mgl)

            val rawProps = vpBuf.toArray(ValueLayout.JAVA_FLOAT)
            val indices = triBuf.toArray(ValueLayout.JAVA_INT)

            // Из вершинных пропсов выдёргиваем XYZ; нормали либо там же (если numProp >= 6),
            // либо считаем face-normal'ы по граням (грубо, но достаточно для рендера).
            val vertices = FloatArray(numVert * 3)
            val normals = FloatArray(numVert * 3)
            val hasInputNormals = numProp >= 6
            for (v in 0 until numVert) {
                val src = v * numProp
                val dst = v * 3
                vertices[dst] = rawProps[src]
                vertices[dst + 1] = rawProps[src + 1]
                vertices[dst + 2] = rawProps[src + 2]
                if (hasInputNormals) {
                    normals[dst] = rawProps[src + 3]
                    normals[dst + 1] = rawProps[src + 4]
                    normals[dst + 2] = rawProps[src + 5]
                }
            }
            if (!hasInputNormals) computeFaceAveragedNormals(vertices, indices, normals)

            return Mesh(vertices, normals, indices)
        } finally {
            Manifoldc.manifold_delete_meshgl(mgl)
        }
    }

    fun deleteManifold(m: MemorySegment) {
        Manifoldc.manifold_delete_manifold(m)
    }

    private fun computeFaceAveragedNormals(verts: FloatArray, indices: IntArray, out: FloatArray) {
        for (t in 0 until indices.size / 3) {
            val i0 = indices[3 * t] * 3
            val i1 = indices[3 * t + 1] * 3
            val i2 = indices[3 * t + 2] * 3
            val ux = verts[i1] - verts[i0]
            val uy = verts[i1 + 1] - verts[i0 + 1]
            val uz = verts[i1 + 2] - verts[i0 + 2]
            val vx = verts[i2] - verts[i0]
            val vy = verts[i2 + 1] - verts[i0 + 1]
            val vz = verts[i2 + 2] - verts[i0 + 2]
            val nx = uy * vz - uz * vy
            val ny = uz * vx - ux * vz
            val nz = ux * vy - uy * vx
            for (k in 0 until 3) {
                val off = indices[3 * t + k] * 3
                out[off] += nx
                out[off + 1] += ny
                out[off + 2] += nz
            }
        }
        for (v in 0 until out.size / 3) {
            val o = v * 3
            val len = kotlin.math.sqrt(out[o] * out[o] + out[o + 1] * out[o + 1] + out[o + 2] * out[o + 2])
            if (len > 0f) {
                out[o] /= len; out[o + 1] /= len; out[o + 2] /= len
            }
        }
    }
}
