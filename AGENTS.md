# 注意事项

每次改动完成后，都必须创建一个对应的 Git commit，以便后续追踪和回滚。每次改动后，都必须编写或更新相关测试，并在交付给用户前，确保所有测试和验证全部通过。

---

# 工程约定（校途 CampusWay）

以上两条是硬性要求，下面是本工程的具体落地方式。

## 提交约定

- 一次改动一个 commit，提交信息用中文，前缀沿用常见约定：
  - `feat:` 新功能 / `fix:` 修缺陷 / `refactor:` 重构 / `test:` 测试 /
    `docs:` 文档 / `chore:` 构建与杂项
- 示例：`feat(课表): 高亮当前时段并提示即将开始的课程`
- 不要提交 `local.properties`、`.gradle-home/`、`.tools/`、`.build-logs/`，
  这些已在 `.gitignore` 里忽略。

## 测试约定

- 单元测试放 `app/src/test/java/`，**必须是纯 JVM 测试**，不依赖设备与模拟器。
- 纯逻辑要刻意与 Android 解耦，便于测试：
  - 领域模型与算法放 `core/model/`（无 Android 依赖）；
  - 页面的展示计算放 `app/*Presenter.kt`（纯函数）；
  - 需要 Activity/Context 的能力不要写进这些类。
- 数据层通过接口隔离：`CampusRepository` / `SettingsStore` 都有内存实现
  （`InMemoryCampusRepository`、`InMemorySettingsStore`），测试直接用内存版本。
- 每次改动后运行：

  ```bash
  ./gradlew :app:testDebugUnitTest
  ```

- 涉及课表时段判定、楼内路径、几何换算、JSON 往返的改动，**必须**补测试：
  这些地方最容易在大意时悄悄出错，且出错后界面看起来仍然"正常"。

## 构建

```bash
./gradlew :app:assembleDebug          # 编译
./gradlew :app:testDebugUnitTest      # 单元测试
```

- JDK 17；`compileSdk`/`targetSdk` = 36，`minSdk` = 26。
- **本机代理坑**：这台机器若配了不可达的全局代理，Gradle 会静默卡住。
  构建时追加下面这段清空代理：

  ```
  -Dhttp.proxyHost= -Dhttps.proxyHost= -Dhttp.proxyPort= -Dhttps.proxyPort=
  ```

## 依赖约束

- 不要使用 Room 与 Navigation-Compose：本工程刻意用
  「JSON 文件 + StateFlow」和轻量状态机替代，理由见 `README.md` 第五节。
- 新增依赖前先确认体积与必要性，本项目目标是"离线可用、安装包尽量小"。

## 数据

- 内置示例数据在 `core/data/SampleData.kt`，它同时是单元测试夹具，
  **只维护这一份**；`app/src/main/assets/data/*.json` 由
  `tools/generate_assets/main.kt` 生成，属于可选随包数据。
- 应用读取顺序：用户数据目录 → `assets` → `SampleData`，
  所以任何情况下都不会出现空白界面。
