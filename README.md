# 校途 CampusWay

面向职高技校的安卓应用：**课程表管理 + 教学楼外导航 + 教学楼内示意导览**。

设计原则是 **离线优先**：课表、所有平面示意图、楼内路线全部保存在本机，
没网也能完整使用；高德/腾讯地图只是楼外导航的可选增强。

---

## 一、功能与需求对应关系

需求来自 `docs/` 下的三份文档，实现映射如下。

### 项目介绍

| 需求 | 实现位置 | 说明 |
|---|---|---|
| 课程表 + 课程时间表 | `core/model/Timetable.kt`、`ui/screen/TimetableScreen.kt` | 二者分开建模、分开展示 |
| 教学楼外导航（2D 卫星地图 + API 密钥） | `core/map/MapProvider.kt` | 高德 / 腾讯 Web 服务双实现，密钥在设置页填写 |
| 教学楼外导航（3D 模型，半透明可缩放对齐） | `ui/components/PlanCanvas.kt` | 矢量平面图支持缩放拖动；3D 模型开关与透明度已接入设置（见「后续可加」） |
| 教学楼内导航（显示当前楼示意图并高亮教室） | `ui/components/PlanCanvas.kt#FloorPlanCanvas`、`core/model/RoutePlanner.kt` | 高亮目标教室 + 虚线路径 + 分步文字引导 |
| 主要功能可离线运行 | `core/data/`、`core/map/` | 数据全部本地文件；在线服务失败自动回退离线估算 |
| 不做室内实时定位 | `NavigationViewModel` | 起点由课表/用户显式选择，不依赖任何室内传感器 |

### 页面列表与功能逻辑

| 页面 | 实现位置 |
|---|---|
| 底部导航（课表 / 导航 / 设置） | `ui/AppShell.kt`、`ui/components/BottomBar.kt` |
| 页面 1 课表：今日课程 + 当前时段高亮 + 即将到来 | `ui/screen/TimetableScreen.kt`、`app/TimetablePresenter.kt` |
| 页面 1 课表：整周课程表与时间表**分开显示** | 同上（今日/整周二选一，时间表永远是独立卡片） |
| 页面 2 导航：校园平面图，点击教学楼进入楼内示意图 | `ui/screen/NavigationScreen.kt` |
| 页面 2 导航：搜索教学楼/教室并串联楼外+楼内 | `NavigationViewModel.openHit` / `openRoomById` |
| 页面 3 设置：配置与关于 | `ui/screen/SettingsScreen.kt` |
| 页面 4 新用户引导：基础数据与个性化选择 | `ui/screen/OnboardingScreen.kt` |

### UI 风格交互要求

| 要求 | 实现 |
|---|---|
| 风格：鸿蒙 | 大圆角卡片、低阴影、胶囊标签、克制留白（`ui/theme/Color.kt#CampusDimens`） |
| 配色：鸿蒙蓝白 | 主色 `#0A59F7`（深色 `#317AF7`），见 `CampusColors` |
| 浅色 / 深色 | 跟随系统 / 浅色 / 深色三档，设置页可切换，含自绘平面图的深色配色 |
| 动画：性能与效果平衡 | 只用 150–300ms 的淡入与颜色过渡（`CampusMotion`），不做长编排 |
| 字体：鸿蒙字体 | 采用字体回退栈策略，见下文「关于字体」 |

---

## 二、工程结构

```
app/src/main/java/com/campusway/
├─ CampusWayApplication.kt      # 应用入口，装配依赖容器
├─ MainActivity.kt              # 唯一 Activity，Compose 承载全部界面
├─ BuildInfo.kt                 # 版本等静态信息
├─ app/                         # 依赖容器 + ViewModel + 展示层纯逻辑
│   ├─ AppContainer.kt
│   ├─ TimetablePresenter.kt    # 课表页展示逻辑（纯函数，可单测）
│   ├─ TimetableViewModel.kt
│   ├─ NavigationViewModel.kt
│   └─ SettingsViewModel.kt
├─ core/
│   ├─ model/                   # 领域模型，纯 Kotlin，无 Android 依赖
│   │   ├─ Timetable.kt         # 节次/时间表/课程/课程表
│   │   ├─ Schedule.kt          # 校历、今日课表、当前/即将状态判定
│   │   ├─ Campus.kt            # 校园/教学楼/楼层/房间/归一化几何
│   │   ├─ RoutePlanner.kt      # 楼内最短路与引导步骤
│   │   ├─ Navigation.kt        # 楼外路线、引导方案、缓存信息
│   │   ├─ AppSettings.kt       # 全部设置项
│   │   └─ Geo.kt               # Haversine 距离与步行耗时
│   ├─ data/                    # 持久化
│   │   ├─ CampusRepository.kt      # 接口 + 内存实现（单测用）
│   │   ├─ AssetCampusRepository.kt # 文件实现：用户数据 → assets → 内置兜底
│   │   ├─ SettingsStore.kt         # 接口 + 内存实现
│   │   ├─ DataStoreSettingsStore.kt
│   │   ├─ JsonConfig.kt
│   │   └─ SampleData.kt            # 示例学校（同时是单测夹具）
│   └─ map/
│       └─ MapProvider.kt       # 离线/高德/腾讯三实现 + 静态图瓦片缓存
└─ ui/
    ├─ AppShell.kt              # 底部导航 + 引导入口
    ├─ theme/                   # 配色、尺寸、动效、主题
    ├─ components/              # 卡片、顶栏、底栏、平面图画布
    └─ screen/                  # 四个页面
```

---

## 三、构建与运行

### 环境要求

| 项 | 版本 |
|---|---|
| JDK | 17 |
| Android SDK | Platform 36（`compileSdk`/`targetSdk` = 36） |
| Build Tools | 34.0.0 及以上 |
| Gradle | 8.13（wrapper 已配置） |
| AGP / Kotlin | 8.5.2 / 2.1.20 |
| minSdk | 26（Android 8.0） |

### 用 Android Studio

1. `File → Open` 选择本工程根目录；
2. 等待 Gradle Sync（首次会联网下载依赖）；
3. 直接运行 `app` 配置即可。

工程里**没有** `local.properties`（已被 `.gitignore` 忽略），
Android Studio 会自动生成；命令行构建请自行创建：

```properties
sdk.dir=D\:\\path\\to\\Android\\Sdk
```

### 命令行

```bash
# 编译
./gradlew :app:assembleDebug

# 单元测试（纯 JVM，不需要设备/模拟器）
./gradlew :app:testDebugUnitTest

# 安装到已连接设备
./gradlew :app:installDebug
```

> **注意**：如果这台机器上配置过全局代理而代理不可达，Gradle 会一直卡住。
> 此时给构建命令追加 `-Dhttp.proxyHost= -Dhttps.proxyHost= -Dhttp.proxyPort= -Dhttps.proxyPort=`
> 清空代理即可。

### 重新生成随包示例数据（可选）

`app/src/main/assets/data/*.json` 是**可选**的示例学校数据。
应用在缺少它们时会自动回退到代码内置的 `SampleData`，功能完全一致。

```bash
kotlinc tools/generate_assets/main.kt -include-runtime -d build/assets-gen.jar
java -jar build/assets-gen.jar .
```

---

## 四、测试

单元测试位于 `app/src/test/java/`，全部是纯 JVM 测试，不依赖 Android 运行时。

| 测试文件 | 覆盖内容 |
|---|---|
| `core/model/TimeTableTest.kt` | 时间解析、节次校验（重复/重叠/非法）、分组 |
| `core/model/TodayScheduleTest.kt` | 今日课表、当前/即将/已结束的**边界判定**、周次过滤、教室覆盖 |
| `core/model/RoutePlannerTest.kt` | 楼内最短路、入口回退、跨层、空数据不崩、几何换算 |
| `core/model/CampusModelTest.kt` | 教室定位、搜索、点击命中（避开走廊）、Geo 距离 |
| `app/TimetablePresenterTest.kt` | 今日/整周视图、即将上课提示、空状态、周次翻页 |
| `core/data/SettingsStoreTest.kt` | 设置 JSON 往返、容错、离线能力文案、示例数据自洽 |

```bash
./gradlew :app:testDebugUnitTest
```

**验收时的建议检查顺序**（因为这几点最容易在真实数据上出问题）：

1. 课表页：当前时段与即将时段是否正确高亮，时间表与课程表是否分开显示；
2. 导航页：点教学楼能否进入楼内示意图，搜索教室后目标教室是否高亮且有路径；
3. 课表页「带我去这间教室」能否跨页跳到导航页并自动定位；
4. 设置页切换深色模式，平面图配色是否同步变化；
5. 断开网络，确认课表与示意图仍然可用。

---

## 五、设计决策说明

### 为什么不用 Room 和 Navigation-Compose

数据量很小（一份课表 + 一所学校的示意图），用 **JSON 文件 + StateFlow** 足够，
省掉注解处理器与迁移代码；导航只有三个一级页面，用状态机比引入
Navigation-Compose 更直观，页面切换动画也更好控制。两者都可以在需要时再加。

### 为什么楼外导航做成三个可插拔实现

需求要求「高德、腾讯都做，未来可能增加」。因此定义 `MapProvider` 接口：

- `OfflineMapProvider`：Haversine 直线距离 × 1.25 绕行系数，**永远可用**；
- `AmapMapProvider` / `TencentMapProvider`：走各家 Web 服务接口，自带 JSON 解析；
- 任何一个在线实现失败（无 Key / 无网 / 接口报错）都会自动回退到离线估算，
  界面不会出现「导航打不开」的情况。

只依赖 JDK 的 `HttpURLConnection`，没有引入 okhttp/retrofit，减小体积。

### 为什么定位只用系统 API

用 `LocationManager.getLastKnownLocation`，不引入高德定位 SDK：
不绑定服务商、少一个几 MB 的 so，而「到校门口」这个精度系统定位完全够用。

### 关于字体

需求要求鸿蒙字体。HarmonyOS Sans 有版权约束，不适合直接打进 APK，
因此采用**字体回退栈**：若系统已内置 HarmonyOS Sans（鸿蒙及部分国产 ROM）
会自动命中，否则回退到系统默认中文黑体，观感一致。
若学校有授权字体，把 ttf 放进 `res/font/`，改 `ui/theme/Theme.kt` 里的
`CampusFontFamily` 一行即可，无需改动其他代码。

---

## 六、后续可加

- **3D 校园模型**：设置里已有开关与透明度项（`show3dModel` / `model3dOpacity`），
  数据模型里预留了 `Building.model3dPath`。接入方案是在
  `CampusPlanCanvas` 上叠加一层按 `model3dPath` 加载的 glTF/obj 渲染，
  并按 `Building.latitude/longitude` 做对齐缩放。为避免绑定 OpenGL 依赖，
  当前版本只画矢量平面图。
- **课表导入**：目前数据由 `assets` 或用户数据目录的 JSON 提供，
  可增加「从 Excel/教务系统导出文件导入」的解析器。
- **课表提醒通知**：`showUpcomingReminder` / `reminderLeadMinutes` 已就绪，
  接一个 `WorkManager` 或闹钟即可推送真正通知。
