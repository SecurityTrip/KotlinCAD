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
├── kernel/     — Kernel interface + StubKernel + ManifoldKernel (skeleton, needs jextract)
├── render/     — LWJGL: Camera, GlRenderer, Shaders, SceneState
├── io_/        — StlWriter (binary). OBJ + project JSON — TODO
└── native_/    — NativeLoader (распаковка manifoldc.dll). jextract bindings — генерируются
```

Принципы:
- Domain не зависит от Compose/LWJGL/Manifold — pure Kotlin, immutable.
- Kernel подменяем (StubKernel сейчас, ManifoldKernel потом).
- Команды → dirty-tracking → kernel.tessellate → SceneState → GL upload.

## Заглушки и TODO

| Где | Что |
|---|---|
| `cad.kernel.manifold.ManifoldKernel.extrude/boolean` | каркас + integration plan в KDoc; реальные Panama-вызовы появятся после jextract |
| `cad.kernel.stub.StubKernel.boolean` | no-op, возвращает `a` |
| `cad.domain.parameter.Parameter.formula` | поле есть, но парсер/граф формул не написан |
| `cad.io_` | STL writer есть; OBJ + project JSON — TODO |

## Phase 1 — реальная геометрия

Полный roadmap — в [ROADMAP.md](ROADMAP.md). Phase 1 = подключить
[elalish/manifold](https://github.com/elalish/manifold) через Panama FFI.

### 1. Собрать `manifoldc`
Под Windows проще всего через vcpkg (он притянет `glm`, `clipper2`, `tbb`):

```powershell
git clone https://github.com/elalish/manifold.git
cd manifold
cmake -B build -DMANIFOLD_C_API=ON -DCMAKE_BUILD_TYPE=Release `
      -DCMAKE_TOOLCHAIN_FILE="$env:VCPKG_ROOT/scripts/buildsystems/vcpkg.cmake"
cmake --build build --config Release --target manifoldc
```

Положи:
- `build/bin/Release/manifoldc.dll` → `src/main/resources/native/windows-x86_64/manifoldc.dll`
- `bindings/c/include/manifold/manifoldc.h` → `native/include/manifoldc.h`

### 2. Установить jextract
Отдельная тулза из проекта Panama: <https://jdk.java.net/jextract/>. Распаковать,
добавить в `PATH` или прописать `JEXTRACT_HOME`.

### 3. Сгенерировать биндинги
```
gradlew jextract
```
Таск автоматически вызывает `jextract` с правильными аргументами. Если заголовок
или сам `jextract` не найдены — таск пишет warning и пропускается, билд
продолжается на StubKernel.

### 4. Дореализовать `ManifoldKernel`
В файле [ManifoldKernel.kt](src/main/kotlin/cad/kernel/manifold/ManifoldKernel.kt)
методы `extrude`/`boolean` пока кидают `TODO`. После генерации биндингов
вписать вызовы `Manifoldc.manifold_extrude(...)` по KDoc-плану.

DI сам подхватит `ManifoldKernel`, если конструктор не упадёт (т.е. DLL
загрузилась И jextract-биндинги на classpath). Иначе — StubKernel.

### 5. Экспорт STL
Уже работает: выдели фичу в дереве → кнопка **Export STL**. На StubKernel
получишь куб; после Phase 1 — настоящий extrude.

## Известные ограничения

- На первой инициализации `AWTGLCanvas` может «моргнуть» чёрным — это
  ожидаемо: GL-контекст создаётся лениво.
- `--enable-native-access=ALL-UNNAMED` уже прописан в JVM args для Compose
  Application — на JDK 23 без него Panama-вызовы будут падать с warning'ом.
- LWJGL ставит natives для всех трёх ОС в classpath; runtime сам подберёт
  нужный. Это раздувает classpath, но проще, чем условные классификаторы.
