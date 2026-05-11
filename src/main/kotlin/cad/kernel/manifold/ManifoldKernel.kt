package cad.kernel.manifold

import cad.domain.feature.BoolOp
import cad.domain.feature.Feature
import cad.domain.feature.Profile2D
import cad.domain.tree.TreeSnapshot
import cad.kernel.Kernel
import cad.kernel.mesh.Mesh

/**
 * Будущая реализация Kernel через Manifold (C-API `manifoldc`) и Panama FFI.
 *
 * План интеграции:
 * 1. Собрать `manifoldc` из исходников (https://github.com/elalish/manifold) с флагом
 *    `-DMANIFOLD_C_API=ON`. Получим `manifoldc.dll` / `libmanifoldc.so` /
 *    `libmanifoldc.dylib` + заголовок `manifoldc.h`. Положить их под
 *    `src/main/resources/native/<os>-<arch>/`.
 *
 * 2. Запустить jextract из JDK 23:
 *    ```
 *    jextract --output build/generated/sources/jextract \
 *             --target-package cad.native_.manifold \
 *             --header-class-name Manifoldc \
 *             native/include/manifoldc.h
 *    ```
 *    Добавить сгенерированный каталог в Kotlin sourceSets.
 *
 * 3. Использовать классы Panama:
 *    - `java.lang.foreign.Arena` — лайфтайм нативной памяти (try-with-resources).
 *    - `java.lang.foreign.MemorySegment` — буферы (vertices/indices наружу к manifoldc).
 *    - `java.lang.foreign.SymbolLookup` / `Linker` — найти и связать символы.
 *    - Сгенерированные методы `Manifoldc.manifold_create_*`, `manifold_boolean`,
 *      `manifold_extrude`, `manifold_to_meshgl` уже инкапсулируют downcall handles.
 *
 * 4. Mapping:
 *    - [extrude]  → `manifold_extrude(polygons, height, n_divisions, twist, scale)`
 *    - [boolean]  → `manifold_boolean(a, b, op)` где op ∈ {ADD, SUBTRACT, INTERSECT}
 *    - [tessellate] → `manifold_to_meshgl` затем копирование в [Mesh].
 *
 * 5. Загрузка либы: `System.loadLibrary("manifoldc")` после распаковки
 *    нативки из ресурсов во временный каталог. Опционально — обёртка через
 *    `Linker.nativeLinker().defaultLookup().or(SymbolLookup.libraryLookup(...))`.
 *
 * До завершения этих шагов класс кидает [NotImplementedError]; в DI он не
 * привязан — пока инжектится [cad.kernel.stub.StubKernel].
 */
class ManifoldKernel : Kernel {
    override fun tessellate(feature: Feature, snapshot: TreeSnapshot): Mesh =
        TODO("ManifoldKernel: см. KDoc — нужны jextract-биндинги")

    override fun boolean(a: Mesh, b: Mesh, op: BoolOp): Mesh =
        TODO("ManifoldKernel: см. KDoc — нужны jextract-биндинги")

    override fun extrude(profile: Profile2D, depth: Double): Mesh =
        TODO("ManifoldKernel: см. KDoc — нужны jextract-биндинги")
}
