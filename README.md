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

### Автоматическая настройка (Windows)

Всё, что описано ниже, делает один скрипт:

```powershell
# из корня репозитория
scripts\setup-manifold.ps1 -ManifoldTag v3.0.1
```

Что он делает по шагам:
1. Клонит и бутстрапит vcpkg в `tools/vcpkg/` (использует существующий
   `$env:VCPKG_ROOT`, если задан).
2. Ставит зависимости: `clipper2`, `glm`, `tbb`.
3. Клонит manifold (фиксированный тег), конфигурит CMake с
   `-DMANIFOLD_C_API=ON -DMANIFOLD_DOWNLOADS=OFF` и собирает `manifoldc`.
4. Копирует все нужные DLL в `src/main/resources/native/windows-x86_64/`:
   - `manifoldc.dll` — C-API wrapper
   - `manifold.dll` — основная либа
   - `tbb12.dll`, `tbbmalloc.dll`, `tbbmalloc_proxy.dll` — runtime-зависимости
5. Копирует заголовки в `native/include/manifold/` и патчит K&R-объявления
   `()` → `(void)`, чтобы jextract не делал из них variadic-инвокеры.
6. Скачивает jextract в `tools/jextract/` (с `https://jdk.java.net/jextract/`).
7. Запускает `gradlew jextract` — генерирует Java-биндинги в
   `build/generated/sources/jextract/java/cad/native_/manifold/`.

**Требования к окружению:** Visual Studio 2022 Build Tools с workload
"Desktop development with C++", `git` и `cmake` в PATH. Запускай из
**x64 Native Tools Command Prompt for VS** (или из PowerShell, открытого
из этой консоли) — иначе CMake может не найти MSVC-компилятор.

Параметры скрипта:
- `-ManifoldTag <tag>` — версия manifold (default: `main`; для воспроизводимости
  лучше фиксированный тег).
- `-VcpkgRoot <path>` — путь к существующему vcpkg.
- `-JextractUrl <url>` — если default-ссылка устарела, актуальную найдёшь
  на <https://jdk.java.net/jextract/>.
- `-Force` — пересобрать manifoldc, даже если DLL уже есть.
- `-SkipJextract` — не качать jextract (если уже стоит в PATH).

После прогона `gradlew run` в логах должно быть:
```
INFO NativeLoader - Manifold loaded: ...\manifoldc.dll
INFO ManifoldKernel - ManifoldKernel ready
INFO cad.app.Kernel - Kernel: ManifoldKernel
```

Если вместо этого `Falling back to StubKernel: ... Can't find dependent libraries` —
не хватает какой-то DLL. Посмотреть зависимости вручную:
```powershell
& "C:\Program Files\Microsoft Visual Studio\2022\Community\VC\Tools\MSVC\*\bin\Hostx64\x64\dumpbin.exe" `
    -dependents src\main\resources\native\windows-x86_64\manifoldc.dll
```
Найди недостающую и закинь в ту же папку.

### Что внутри (на случай ручного шага)

- **Биндинги** — `build/generated/sources/jextract/java/cad/native_/manifold/`.
  Сгенерированные `Manifoldc.java`, `ManifoldVec2.java` и т.д. в репо не
  коммитятся — `gradle clean` их стирает, после клина нужно
  `gradlew jextract`.
- **Реализация** — [ManifoldFfi.kt](src/main/kotlin/cad/kernel/manifold/ManifoldFfi.kt)
  (low-level Panama wrapper) и [ManifoldKernel.kt](src/main/kotlin/cad/kernel/manifold/ManifoldKernel.kt)
  (реализация интерфейса `Kernel`).
- **Fallback** — если DLL/биндинги недоступны, DI откатывается на
  `StubKernel`. Он умеет настоящий extrude по полигону, но `boolean` у
  него no-op.

### Экспорт STL
Выдели фичу в дереве → кнопка **Export STL** → JFileChooser. Открывается
в MeshLab/Blender для валидации.

## Известные ограничения

- На первой инициализации `AWTGLCanvas` может «моргнуть» чёрным — это
  ожидаемо: GL-контекст создаётся лениво.
- `--enable-native-access=ALL-UNNAMED` уже прописан в JVM args для Compose
  Application — на JDK 23 без него Panama-вызовы будут падать с warning'ом.
- LWJGL ставит natives для всех трёх ОС в classpath; runtime сам подберёт
  нужный. Это раздувает classpath, но проще, чем условные классификаторы.
