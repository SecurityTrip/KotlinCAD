# pet-CAD

Minimal parametric CAD MVP: Compose Desktop UI + LWJGL 3D viewport + pluggable
geometry kernel. Pet-проект.

## Стек

- Kotlin 2.1.21, JDK 23 toolchain (для стабильного Panama FFI)
- Compose Multiplatform Desktop 1.8.2
- LWJGL 3.3.4 + JOML 1.10.8 (OpenGL 3.3 core через `AWTGLCanvas` в `SwingPanel`)
- Koin 4.0.0 (DI)
- kotlinx.serialization 1.7.3
- JUnit 5 + kotlin.test

## Сборка и запуск

Требования: установленный JDK 23 (toolchain Gradle подтянет сам, если есть provisioning;
иначе укажи путь через `org.gradle.java.installations.paths`).

```bash
./gradlew test           # юнит-тесты на FeatureTree
./gradlew run            # запустить приложение
./gradlew packageDistributionForCurrentOS   # собрать инсталлятор (msi/dmg/deb)
```

## Что работает в этом MVP

- Окно Compose с четырьмя зонами: toolbar / feature tree / 3D viewport / inspector.
- 3D viewport: сетка XZ (Y вверх), Lambert + ambient освещение, wireframe overlay
  поверх solid.
- Орбитальная камера: ЛКМ — orbit, СКМ/ПКМ — pan, колесо — zoom.
- Toolbar:
  - **Add Box** — создаёт `Sketch` (XZ-плоскость, прямоугольник) + `Extrude` со
    ссылкой на этот sketch и параметрами `width/height/depth`.
  - **Undo / Redo** — по истории команд.
- Feature tree: список фич с выделением.
- Inspector: редактирование числовых параметров выделенной фичи. Изменение
  диспатчит `UpdateParameter`.
- `FeatureTree` с event-sourced history (снапшоты), dirty-propagation по графу
  зависимостей (`Sketch → Extrude`).
- `Kernel` — интерфейс. В DI инжектится `StubKernel`, который для любого
  `Extrude` возвращает куб по `width × height × depth`.

## Архитектура

```
cad/
├── app/        — Compose Application, DI, ViewModel
├── ui/         — Compose UI (viewport / tree / inspector / toolbar)
├── domain/     — чистая модель (Feature, Parameter, Command, FeatureTree)
├── kernel/     — Kernel interface + StubKernel + ManifoldKernel (TODO)
├── render/     — LWJGL: Camera, GlRenderer, Shaders, SceneState
├── io_/        — TODO (STL/OBJ export, project format)
└── native_/    — TODO (jextract bindings для manifoldc)
```

Принципы:
- Domain не зависит от Compose/LWJGL/Manifold — pure Kotlin, immutable.
- Kernel подменяем (StubKernel сейчас, ManifoldKernel потом).
- Команды → dirty-tracking → kernel.tessellate → SceneState → GL upload.

## Заглушки и TODO

| Где | Что |
|---|---|
| `cad.kernel.manifold.ManifoldKernel` | пустой класс с большим KDoc-планом интеграции |
| `cad.kernel.stub.StubKernel.boolean` | no-op, возвращает `a` |
| `cad.domain.parameter.Parameter.formula` | поле есть, но парсер/граф формул не написан |
| `cad.io_` | пусто; нужны STL/OBJ export и JSON-сериализация проекта |
| `cad.native_` | пусто; нужны jextract-биндинги для `manifoldc.h` |

## Следующие шаги

1. **Собрать `manifoldc`.**
   ```
   git clone https://github.com/elalish/manifold.git
   cmake -S manifold -B manifold/build -DMANIFOLD_C_API=ON -DCMAKE_BUILD_TYPE=Release
   cmake --build manifold/build --config Release
   ```
   Получишь `manifoldc.dll` / `libmanifoldc.so` + заголовок `manifoldc.h`.
   Положи бинарники в `src/main/resources/native/<os>-<arch>/`, заголовок — в
   `native/include/` (вне sourceSets).

2. **Запустить jextract** (входит в JDK 22+, отдельная тулза в `jextract/bin`):
   ```
   jextract \
     --output build/generated/sources/jextract \
     --target-package cad.native_.manifold \
     --header-class-name Manifoldc \
     native/include/manifoldc.h
   ```
   Подключить сгенерированный каталог в Kotlin sourceSets (`build.gradle.kts`).

3. **Реализовать `ManifoldKernel`** поверх биндингов: `extrude`, `boolean`,
   `tessellate` (вычитать `manifold_to_meshgl` → `Mesh`). Перевязать DI:
   `single<Kernel> { ManifoldKernel(...) }`.

4. **2D sketch-редактор**: оверлей на плоскости sketch'а, отображение
   `SketchEntity`, добавление прямоугольников/кругов мышью, привязки —
   простейшие (snap to grid).

5. **Парсер формул для `Parameter`**: топологический пересчёт через граф
   зависимостей (отдельный от feature-deps).

6. **STL/OBJ export** в `cad.io_`. Это даст возможность тестировать ядро
   против стороннего viewer (например, MeshLab) до того, как ManifoldKernel
   полностью оживёт.

## Известные ограничения

- На первой инициализации `AWTGLCanvas` может «моргнуть» чёрным — это
  ожидаемо: GL-контекст создаётся лениво.
- `--enable-native-access=ALL-UNNAMED` уже прописан в JVM args для Compose
  Application — на JDK 23 без него Panama-вызовы будут падать с warning'ом.
- LWJGL ставит natives для всех трёх ОС в classpath; runtime сам подберёт
  нужный. Это раздувает classpath, но проще, чем условные классификаторы.
