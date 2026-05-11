package cad.render

internal object Shaders {

    val solidVertex = """
        #version 330 core
        layout(location = 0) in vec3 aPos;
        layout(location = 1) in vec3 aNormal;
        uniform mat4 uModel;
        uniform mat4 uView;
        uniform mat4 uProj;
        uniform mat3 uNormalMat;
        out vec3 vNormal;
        out vec3 vWorldPos;
        void main() {
            vec4 worldPos = uModel * vec4(aPos, 1.0);
            vWorldPos = worldPos.xyz;
            vNormal = normalize(uNormalMat * aNormal);
            gl_Position = uProj * uView * worldPos;
        }
    """.trimIndent()

    val solidFragment = """
        #version 330 core
        in vec3 vNormal;
        in vec3 vWorldPos;
        uniform vec3 uLightDir;
        uniform vec3 uColor;
        uniform float uAmbient;
        out vec4 outColor;
        void main() {
            vec3 n = normalize(vNormal);
            float lambert = max(dot(n, normalize(-uLightDir)), 0.0);
            vec3 c = uColor * (uAmbient + (1.0 - uAmbient) * lambert);
            outColor = vec4(c, 1.0);
        }
    """.trimIndent()

    val flatVertex = """
        #version 330 core
        layout(location = 0) in vec3 aPos;
        uniform mat4 uModel;
        uniform mat4 uView;
        uniform mat4 uProj;
        void main() {
            gl_Position = uProj * uView * uModel * vec4(aPos, 1.0);
        }
    """.trimIndent()

    val flatFragment = """
        #version 330 core
        uniform vec3 uColor;
        out vec4 outColor;
        void main() {
            outColor = vec4(uColor, 1.0);
        }
    """.trimIndent()
}
