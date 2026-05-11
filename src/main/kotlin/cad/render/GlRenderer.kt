package cad.render

import cad.kernel.mesh.Mesh
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.opengl.GL11C.*
import org.lwjgl.opengl.GL15C.*
import org.lwjgl.opengl.GL20C.*
import org.lwjgl.opengl.GL30C.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.slf4j.LoggerFactory

private data class GpuMesh(
    val vao: Int,
    val vboPos: Int,
    val vboNrm: Int,
    val ebo: Int,
    val indexCount: Int,
)

class GlRenderer {
    private val log = LoggerFactory.getLogger(GlRenderer::class.java)

    private var solidProgram = 0
    private var flatProgram = 0

    private var gridVao = 0
    private var gridVbo = 0
    private var gridVertexCount = 0

    private val gpuMeshes = mutableMapOf<Long, GpuMesh>()
    private var lastSceneRevision = -1L

    fun init() {
        glEnable(GL_DEPTH_TEST)
        glDepthFunc(GL_LEQUAL)
        glEnable(GL_CULL_FACE)
        glCullFace(GL_BACK)
        glFrontFace(GL_CCW)

        solidProgram = buildProgram(Shaders.solidVertex, Shaders.solidFragment)
        flatProgram = buildProgram(Shaders.flatVertex, Shaders.flatFragment)

        buildGrid()
        log.info("GlRenderer initialized")
    }

    private fun buildGrid() {
        val verts = GridMesh.build(half = 10, step = 1f)
        gridVertexCount = verts.size / 3
        gridVao = glGenVertexArrays()
        gridVbo = glGenBuffers()
        glBindVertexArray(gridVao)
        glBindBuffer(GL_ARRAY_BUFFER, gridVbo)
        val buf = MemoryUtil.memAllocFloat(verts.size)
        try {
            buf.put(verts).flip()
            glBufferData(GL_ARRAY_BUFFER, buf, GL_STATIC_DRAW)
        } finally {
            MemoryUtil.memFree(buf)
        }
        glEnableVertexAttribArray(0)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0L)
        glBindVertexArray(0)
    }

    fun render(width: Int, height: Int, camera: Camera, scene: Scene) {
        if (width <= 0 || height <= 0) return
        glViewport(0, 0, width, height)
        glClearColor(0.15f, 0.16f, 0.18f, 1f)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

        val aspect = width.toFloat() / height.toFloat()
        val view = camera.viewMatrix()
        val proj = camera.projectionMatrix(aspect)
        val model = Matrix4f()

        // Сетка — flat program, GL_LINES
        glUseProgram(flatProgram)
        setMat4(flatProgram, "uModel", model)
        setMat4(flatProgram, "uView", view)
        setMat4(flatProgram, "uProj", proj)
        setVec3(flatProgram, "uColor", 0.35f, 0.36f, 0.38f)
        glBindVertexArray(gridVao)
        glDrawArrays(GL_LINES, 0, gridVertexCount)
        glBindVertexArray(0)

        if (scene.revision != lastSceneRevision) {
            syncSceneToGpu(scene)
            lastSceneRevision = scene.revision
        }

        // Solid pass
        glUseProgram(solidProgram)
        setMat4(solidProgram, "uView", view)
        setMat4(solidProgram, "uProj", proj)
        setVec3(solidProgram, "uLightDir", -0.4f, -1f, -0.6f)
        setFloat(solidProgram, "uAmbient", 0.25f)

        glEnable(GL_POLYGON_OFFSET_FILL)
        glPolygonOffset(1f, 1f)
        for (sm in scene.meshes) {
            val gpu = gpuMeshes[sm.featureId.value.hashCode().toLong()] ?: continue
            val color = if (sm.featureId == scene.selected) Vector3f(0.95f, 0.7f, 0.25f)
                        else Vector3f(0.75f, 0.78f, 0.82f)
            setMat4(solidProgram, "uModel", model)
            setMat3(solidProgram, "uNormalMat", Matrix3f(model).invert().transpose())
            setVec3(solidProgram, "uColor", color.x, color.y, color.z)
            glBindVertexArray(gpu.vao)
            glDrawElements(GL_TRIANGLES, gpu.indexCount, GL_UNSIGNED_INT, 0L)
        }
        glDisable(GL_POLYGON_OFFSET_FILL)

        // Wireframe overlay
        glUseProgram(flatProgram)
        setMat4(flatProgram, "uView", view)
        setMat4(flatProgram, "uProj", proj)
        setMat4(flatProgram, "uModel", model)
        setVec3(flatProgram, "uColor", 0.08f, 0.08f, 0.1f)
        glPolygonMode(GL_FRONT_AND_BACK, GL_LINE)
        for (sm in scene.meshes) {
            val gpu = gpuMeshes[sm.featureId.value.hashCode().toLong()] ?: continue
            glBindVertexArray(gpu.vao)
            glDrawElements(GL_TRIANGLES, gpu.indexCount, GL_UNSIGNED_INT, 0L)
        }
        glPolygonMode(GL_FRONT_AND_BACK, GL_FILL)
        glBindVertexArray(0)
    }

    private fun syncSceneToGpu(scene: Scene) {
        // Простой подход: всё пересоздаём при изменении сцены.
        // Когда станет узким местом — добавим diff по revision per-mesh.
        gpuMeshes.values.forEach { releaseGpuMesh(it) }
        gpuMeshes.clear()
        for (sm in scene.meshes) {
            gpuMeshes[sm.featureId.value.hashCode().toLong()] = uploadMesh(sm.mesh)
        }
    }

    private fun uploadMesh(mesh: Mesh): GpuMesh {
        val vao = glGenVertexArrays()
        val vboPos = glGenBuffers()
        val vboNrm = glGenBuffers()
        val ebo = glGenBuffers()
        glBindVertexArray(vao)

        val posBuf = MemoryUtil.memAllocFloat(mesh.vertices.size)
        val nrmBuf = MemoryUtil.memAllocFloat(mesh.normals.size)
        val idxBuf = MemoryUtil.memAllocInt(mesh.indices.size)
        try {
            posBuf.put(mesh.vertices).flip()
            nrmBuf.put(mesh.normals).flip()
            idxBuf.put(mesh.indices).flip()

            glBindBuffer(GL_ARRAY_BUFFER, vboPos)
            glBufferData(GL_ARRAY_BUFFER, posBuf, GL_STATIC_DRAW)
            glEnableVertexAttribArray(0)
            glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0L)

            glBindBuffer(GL_ARRAY_BUFFER, vboNrm)
            glBufferData(GL_ARRAY_BUFFER, nrmBuf, GL_STATIC_DRAW)
            glEnableVertexAttribArray(1)
            glVertexAttribPointer(1, 3, GL_FLOAT, false, 0, 0L)

            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo)
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, idxBuf, GL_STATIC_DRAW)
        } finally {
            MemoryUtil.memFree(posBuf)
            MemoryUtil.memFree(nrmBuf)
            MemoryUtil.memFree(idxBuf)
        }
        glBindVertexArray(0)
        return GpuMesh(vao, vboPos, vboNrm, ebo, mesh.indices.size)
    }

    private fun releaseGpuMesh(g: GpuMesh) {
        glDeleteBuffers(g.vboPos)
        glDeleteBuffers(g.vboNrm)
        glDeleteBuffers(g.ebo)
        glDeleteVertexArrays(g.vao)
    }

    fun dispose() {
        gpuMeshes.values.forEach { releaseGpuMesh(it) }
        gpuMeshes.clear()
        if (gridVbo != 0) glDeleteBuffers(gridVbo)
        if (gridVao != 0) glDeleteVertexArrays(gridVao)
        if (solidProgram != 0) glDeleteProgram(solidProgram)
        if (flatProgram != 0) glDeleteProgram(flatProgram)
    }

    private fun buildProgram(vsSrc: String, fsSrc: String): Int {
        val vs = compileShader(GL_VERTEX_SHADER, vsSrc)
        val fs = compileShader(GL_FRAGMENT_SHADER, fsSrc)
        val program = glCreateProgram()
        glAttachShader(program, vs)
        glAttachShader(program, fs)
        glLinkProgram(program)
        val status = glGetProgrami(program, GL_LINK_STATUS)
        if (status == GL_FALSE) {
            val log = glGetProgramInfoLog(program)
            error("Program link failed: $log")
        }
        glDeleteShader(vs)
        glDeleteShader(fs)
        return program
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = glCreateShader(type)
        glShaderSource(shader, src)
        glCompileShader(shader)
        val status = glGetShaderi(shader, GL_COMPILE_STATUS)
        if (status == GL_FALSE) {
            val log = glGetShaderInfoLog(shader)
            error("Shader compile failed: $log\nSource:\n$src")
        }
        return shader
    }

    private fun setMat4(program: Int, name: String, m: Matrix4f) {
        MemoryStack.stackPush().use { stack ->
            val buf = stack.mallocFloat(16)
            m.get(buf)
            glUniformMatrix4fv(glGetUniformLocation(program, name), false, buf)
        }
    }

    private fun setMat3(program: Int, name: String, m: Matrix3f) {
        MemoryStack.stackPush().use { stack ->
            val buf = stack.mallocFloat(9)
            m.get(buf)
            glUniformMatrix3fv(glGetUniformLocation(program, name), false, buf)
        }
    }

    private fun setVec3(program: Int, name: String, x: Float, y: Float, z: Float) {
        glUniform3f(glGetUniformLocation(program, name), x, y, z)
    }

    private fun setFloat(program: Int, name: String, v: Float) {
        glUniform1f(glGetUniformLocation(program, name), v)
    }
}
