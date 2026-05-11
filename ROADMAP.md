# pet-CAD Roadmap

План развития после MVP. Сгруппировано по фазам в порядке ROI: каждая
следующая фаза опирается на предыдущую и даёт реально новый функционал,
а не «полировку».

---

## Фаза 1 — Реальная геометрия (1–2 недели)

Цель: убрать хардкод-куб, начать строить настоящие меши. Без этого всё
дальнейшее бессмысленно — UI рисует фикцию.

1. **Сборка `manifoldc` под Windows.**
   - `git clone github.com/elalish/manifold`, `cmake -DMANIFOLD_C_API=ON -DCMAKE_BUILD_TYPE=Release`.
   - DLL положить в `src/main/resources/native/windows-x64/`, заголовок в `native/include/`.
   - Риск: зависит от `clipper2` и `glm`; на Windows проще всего через vcpkg.

2. **jextract → биндинги.**
   ```
   jextract --output build/generated/sources/jextract \
            --target-package cad.native_.manifold \
            --header-class-name Manifoldc \
            native/include/manifoldc.h
   ```
   Подключить generated directory в Kotlin sourceSets. Загрузка либы —
   распаковать из ресурсов во `$TMPDIR/cad/native/` и `System.load`.

3. **`ManifoldKernel.extrude` + `boolean` + `tessellate`.**
   - Mapping: `manifold_extrude(polygons, height, n_div=0, twist=0, scale_x=1, scale_y=1)`.
   - `manifold_to_meshgl` → `MeshGL` структура с `vertProperties`/`triVerts` →
     конвертация в `cad.kernel.mesh.Mesh`.
   - Жизненный цикл через `Arena.ofConfined { ... }`.

4. **STL ASCII/Binary export в `cad.io_`.**
   - Дешёво: 50 строк. Зачем сейчас — для валидации `ManifoldKernel` в
     MeshLab/Blender независимо от своего рендера.

**Verification:** «Add Box» создаёт настоящий extrude через Manifold;
экспортнутый STL открывается в MeshLab, выглядит как параллелепипед.

---

## Фаза 2 — Параметризация по-настоящему (2–3 недели)

Цель: сделать «параметрический CAD» — без редактирования sketch'ей и формул
это просто 3D-вьюер.

1. **2D Sketch viewport overlay.**
   - Когда выделен `Sketch` — viewport переключается в «sketch mode»: камера
     фиксируется ортогонально к плоскости sketch'а, отображаются `SketchEntity` в 2D.
   - Compose `Canvas` с собственным mouse-handling (или второй слой поверх GL —
     дешевле первый).
   - Инструменты: «Rectangle», «Circle», «Line». При клике-drag → создаётся
     entity → `Command.UpdateSketchEntities`.
   - Snap to grid + snap to point (минимально, без честных constraints).

2. **`SketchEntity → Profile2D` конверсия в kernel.**
   - Сейчас `StubKernel.extrude` берёт bounding box. После Manifold — собирать
     настоящий polygon из entity-list.
   - Сложность: дырки (внутренние контуры). Manifold принимает массив
     `manifold_polygons` где каждый — список контуров с правилом orientation.
     Начать с single-contour.

3. **Парсер выражений для `Parameter.formula`.**
   - Простой Pratt parser, поддержка: `+ - * / ^`, ссылки на другие параметры
     (`$d1`, `sketch1.width`).
   - Граф зависимостей параметров (отдельный от feature-deps).
   - При `UpdateParameter` — топологический пересчёт всех зависимых,
     обнаружение циклов.
   - В Inspector — текстовое поле для формулы, валидация в реальном времени.

4. **Глобальные параметры проекта.**
   - Контейнер `parameters: Map<String, Parameter>` на уровне `TreeSnapshot`,
     не привязан к фиче.
   - Inspector → отдельная панель «Project parameters».

**Verification:** меняешь параметр `width` у `Sketch` → реально меняется куб
в viewport через настоящий extrude; формула `depth = width * 2` работает.

---

## Фаза 3 — Интерактивный CAD (2–3 недели)

1. **Picking в viewport.**
   - Ray casting из позиции мыши: `unproject` через JOML, intersect с мешами
     через Möller–Trumbore.
   - Клик по поверхности → выделение соответствующей `Feature` (по обратной
     mesh→featureId таблице).
   - Hover-highlight.

2. **Composite features: Move / Mirror / Pattern.**
   - Добавить `Feature.Transform` (translate/rotate ref-feature),
     `Feature.LinearPattern`.
   - В Kernel: уже сделанный mesh → matrix-multiply вершин (это уже не
     Manifold, это локально).

3. **Boolean operations через UI.**
   - Выделил 2 фичи → кнопка «Subtract» / «Union» →
     `Command.AddFeature(BooleanFeature(...))`.

4. **Корректный wireframe.**
   - Сейчас рендерим треугольную сетку — выглядит шумно. Для CAD нужны только
     «hard edges» (где нормали соседних фасетов расходятся > N°).
   - Стандартная техника: edge-detection в geometry shader или предвычисление
     списка hard edges на CPU при `tessellate`.

5. **Multiple bodies/parts.**
   - Сейчас все extrude рендерятся «у нуля». Нужна базовая трансформация для
     каждой фичи (как минимум — `Position` в параметрах).

**Verification:** делаешь sketch → extrude → второй sketch на верхней грани →
extrude → выглядит как ступенька, можно subtract другим box'ом.

---

## Фаза 4 — Persistence и качество (1 неделя)

1. **Save/Load проекта.**
   - `TreeSnapshot` уже `@Serializable` (kotlinx.serialization JSON).
   - Меню File → Open / Save / Save As.
   - История undo не сохраняется (это нормально для CAD).
   - Версионирование схемы — поле `schemaVersion: Int` в корне.

2. **Recent files, autosave.**
   - `~/.config/petcad/recent.json`, autosave каждые 60 сек в `*.autosave`.

3. **Перформанс рендера — diff per-mesh.**
   - Сейчас `syncSceneToGpu` пересоздаёт VBO для всей сцены при любом
     изменении. Заменить на: меш hashable, GPU держит карту
     `featureId → (revision, GpuMesh)`, перезаливать только изменённые.

4. **Pack distributable.**
   - `gradlew packageMsi` — уже настроено в `compose.desktop`. Проверить, что
     native libraries для Manifold попадают внутрь пакета.

---

## Фаза 5 — То, что хочется, но не критично

- **Fillet/Chamfer.** Это серьёзный геометрический алгоритм. Manifold их не
  делает «из коробки». Либо CGAL (тяжело), либо OpenCASCADE (тяжелее, но
  canonical для CAD). На пет-проекте лучше отложить.
- **Constraint solver для sketch'ей** (perpendicular/parallel/equal). Очень
  интересная задача (это маленький numerical solver), но это самостоятельный
  проект.
- **Assembly mode** (несколько parts с mate-constraints) — выходит за рамки
  1–2 месяцев.
- **Изометрические виды, шкалы, размеры на чертеже.** Это уже drafting,
  отдельная подсистема.

---

## Сквозные технические долги, которые скопятся

- Сейчас `gpuMeshes` использует `featureId.value.hashCode().toLong()` как
  ключ — это коллизионно. Заменить на `String` или `FeatureId` напрямую.
  **Cheap fix, сделать в Фазе 4.**
- `AppViewModel.rebuildScene` пересчитывает ВСЕ меши при любом изменении —
  игнорирует `dirty`-сет, который сам же и считает в `FeatureTree`.
  **Фаза 2.**
- Нет error-handling для нативного кода: упавший `manifold_extrude` сейчас
  уронит JVM. Нужен try/catch вокруг Panama-вызовов и fallback к `Mesh.EMPTY`
  + лог. **Фаза 1, обязательно.**
- `Camera.orbit` использует фиксированную чувствительность — нужна
  нормализация по размеру viewport'а. **Фаза 3.**

---

## Рекомендуемый порядок прямо с этой недели

1. Сначала `STL export` (1–2 часа) — даже на StubKernel сразу даст рабочий
   feedback loop.
2. Потом сборка `manifoldc` + jextract + минимальный `ManifoldKernel.extrude`.
   Это 3–5 вечеров с учётом возни с CMake/Windows.
3. Только после того как Manifold реально работает — браться за
   sketch-редактор. Иначе sketch будет рисовать данные, которые ядро не умеет
   употреблять.
