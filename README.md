# 豚豚剧广告拦截 · LSPosed 模块

针对 `com.shizi.tool`（豚豚剧 v1.0.15）的**免改包**广告拦截模块。
思路来自《漫剧APP屏蔽广告改造研究报告》：不碰原 APK、不改签名，只 Hook 应用自有的三个广告门控。

---

## 1. 它到底做了什么

| Hook | 目标（classes8.dex） | 动作 | 拦截范围 |
|---|---|---|---|
| **H1 总闸** | `com.yy.myuko.shared.domain.ad.AdvertisingState#shouldShow(AdSlot)` | 非激励位恒 `false` | 开屏、首页插屏、播放器插屏、贴片、切集、暂停、HomeBanner、PlayerBanner、CategoryBanner、HomeFeed、PlayerFeed（11 个） |
| **H2 本地豁免** | `com.yy.myuko.shared.domain.ad.AdSlotVipExemption#exemptReason(AdSlot)` | 非激励位返回 `"adfree"` | 让 `requestOrSkip` 走 `AdError$NotEnabled` 分支：**不发请求、不加载 SDK、不产生无填充上报** |
| **H3 播放不等广告** | `com.yy.myuko.shared.presentation.ad.PageAdGateOutcome#getResult()` | 恒 `null` | 复用官方「记忆期内放行」语义，播放页 0 等待 |
| **H4 激进（可选）** | `com.yy.myuko.shared.domain.ad.AdSdkReadyGate#isSupported()` | 恒 `false` | 9 家广告 SDK 完全不初始化（连激励视频一起废，默认关闭） |

**激励视频 `TaskReward` 默认放行**：它是下载 / 投屏 / 福利 / 兑换 / 扫码的解锁货币，掐掉等于自废功能。

为什么能成立（逆向依据）：

- `shouldShow` 的**全部调用方只有 2 处**（`AdSessionUseCase.invokeTracked`、`warmGateAnswer`），无旁路；
- `exemptReason` 内部对 `TaskReward` 有**前置 `return null`**，所以我们只要不在 H2 里动 TaskReward，就能天然保留激励链路；
- 三个目标方法是普通 Java 方法（不是 native、没被内联进调用方），Hook 命中率高。

---

## 2. 环境要求

- 一台已 root 的 Android 手机（Android 8.0+），已装 **LSPosed**（Zygisk / KernelSU / Magisk 任一方案）
- **Android Studio**（自带 JDK 17，最省事）或本机 JDK 17 + Gradle 8.x
- 不需要反编译原 APK、不需要 apktool / jadx

> 注意：**不要在分身 / 双开 / 虚拟机容器里跑**。App 内置 `AndroidHoneypotEnvironmentDetector`（反射 `com.lody.virtual.client.core.VirtualCore` 取宿主包名、探 `data/` `mnt/` 等 vcontainer 路径、读 `/proc/self/cmdline`），一旦判定为容器环境会把广告后端切到蜜罐分支，行为不可预期。

---

## 3. 编译（三条路，选一条就行）

### 路 A：GitHub 云编译（最省事，本地什么都不装，推荐）
1. 把本目录整个传到你的 GitHub 仓库（网页上传也行）
2. 仓库 → **Actions** → 左侧 **Build APK** → **Run workflow**
3. 等 3～6 分钟，在该次运行页面底部下载 artifact **app-debug**，里面就是 `app-debug.apk`

工作流已内置在 `.github/workflows/build-apk.yml`（JDK 17 + Gradle 8.4 + 上传 APK）。

### 路 B：本地命令行（只装 JDK 17，约 180MB，不用装 IDE）
```powershell
# 工程根目录执行，脚本会自动下载 Gradle 8.4 再编译
pwsh -File build.ps1
```
JDK 17 下载（Adoptium Temurin，安装时勾选“Set JAVA_HOME”/“Add to PATH”）：
`https://adoptium.net/temurin/releases/?version=17&os=windows&arch=x64&package=jdk`

### 路 C：Android Studio（图形界面，适合以后还想改代码）
下载：`https://developer.android.com/studio`（安装包约 1.5GB，首次打开还要下 SDK 几个 GB）
打开本目录 → 等 Gradle Sync 完成 → 菜单 **Build → Build App Bundle(s)/APK(s) → Build APK(s)** → 右下角 “locate” 打开产物目录。

> 三条路产出的是同一个东西：`app/build/outputs/apk/debug/app-debug.apk`。

依赖说明：`compileOnly 'de.robv.android.xposed:api:82'`，仓库 `https://api.xposed.info/`。
**必须是 `compileOnly`**——写成 `implementation` 会把 `XposedBridge` 打进 APK，与框架类冲突导致模块不工作。

编译不出来时优先看两件事：
1. 网络能否拉到 `https://api.xposed.info/`（拉不到就把 `XposedBridgeAPI-82.jar` 放 `app/libs/`，改成 `compileOnly files('libs/XposedBridgeAPI-82.jar')`）；
2. `compileSdk 34` 是否已安装（SDK Manager 里勾 Android 14）。

---

## 4. 安装与启用（关键步骤，缺一步就不生效）

1. `adb install -r app-debug.apk`（或直接点安装）
2. 打开 LSPosed → **模块** → 找到「豚豚剧广告拦截」→ **启用**
3. 点进该模块 → **作用域** → 勾选「豚豚剧」(com.shizi.tool) → 保存
4. **强制停止** 豚豚剧（设置 → 应用 → 豚豚剧 → 强制停止），Hook 只在进程启动时加载
5. 打开模块自己的设置页，确认「屏蔽广告」是开的
6. 启动豚豚剧

改了开关之后同样要**强制停止宿主**（或改完等 3 秒以上，配置有 3 秒缓存）。

---

## 5. 怎么确认真的生效

```bash
adb logcat -s LSPosed-Bridge | findstr AdBlock
```

应当看到：

```
[AdBlock] loaded pkg=com.shizi.tool process=com.shizi.tool enabled=true keepReward=true aggressive=false
[AdBlock] H1 ok: AdvertisingState.shouldShow -> false (except TaskReward)
[AdBlock] H2 ok: AdSlotVipExemption.exemptReason -> "adfree"
[AdBlock] H3 ok: PageAdGateOutcome.getResult -> null
```

再对照行为清单：

| 观察点 | 期望 |
|---|---|
| 冷启动 | 无开屏广告，直接进首页 |
| 首页 | 无 Banner、无信息流广告卡 |
| 点播放 | 2 秒内起播 |
| 切集 / 暂停 | 不弹任何广告 |
| **激励视频** | **仍能完整播放，奖励到账**（下载/投屏/福利/兑换仍可用） |

另外可看宿主自己的日志（App 用 kermit 打日志，tag 里带 `AdSessionUseCase` / `PageAdGate`）：
出现 `PlayerInterstitial 刚加载失败过,还在记忆期内,本次不请求直接放行` 就说明 H3 走通了。

---

## 6. 排错

| 现象 | 原因 / 处理 |
|---|---|
| logcat 完全没有 `[AdBlock]` 行 | 模块没启用、或作用域没勾 `com.shizi.tool`、或没强杀宿主 |
| 只有 `H1/H2 FAIL: java.lang.NoSuchMethodError…` | App 升级后类名/签名变了。用报告第 12 节的 `classes.tsv` + `dump_class.py` 重新核对方法签名，改 `HookEntry` 里的常量 |
| 拦截生效但激励视频点不动 | 确认「保留激励视频」开着。已实测该 slot 的 code 字面量是 `task_reward`（同 dex 里另有 PascalCase 写法 `TaskReward`），模块用「去分隔符 + 转小写后包含 taskreward」判断，两种写法都能命中；若日志里 `H1 pass TaskReward code=` 没出现，说明是别的写法，把 `isTaskReward()` 改成直接打印所有 code 观察真实值 |
| 首页出现留白骨架 | 预期内：广告位容器还在、内容为空。不影响播放 |
| 出现「请使用官方版本」弹窗 | 说明签名校验/安全证明链路被触发 —— 本模块不改 APK，正常不会出现；若出现，检查是否装的是别人改过的包 |
| 广告 SDK 仍被初始化 | H4 需要手动在设置页打开「激进模式」，且要强杀宿主 |

---

## 7. 边界与风险（如实说明）

- **确定**：本模块不修改 APK、不重签名，因此不会触发 `NativeApkSignatureProbe` / `SecurityAttest` / `OfficialGuide` 这条链。
- **确定**：H1/H2/H3 都是纯 Java 层 Hook，不依赖 native。
- **不确定（未真机验证）**：拦截后各页面的留白表现、`warmGateAnswer` 相关首屏逻辑是否有异常等待、服务端是否因「零曝光高活跃」对账号做策略调整（平台黑盒，静态分析无法判断）。
- **不确定**：App 大版本更新后 R8 是否仍保留 `com.yy.myuko.*` 的类名与方法名（v1.0.15 是保留的；后续版本要用脚本复核）。
- **不确定**：root 环境下各广告 SDK 的 `DETECT_ROOT_ENABLED` / `rootDetected` 是否会让填充率异常——但本模块本来就不请求广告，影响面很小。
- **合规**：广告是该 App 唯一变现方式；本模块仅供你自己设备自用，**不要分发、不要打包售卖**。

---

## 8. 工程结构

```
AdBlockLsposed/
├── settings.gradle                 # 依赖仓库（含 api.xposed.info）
├── build.gradle                    # AGP 8.1.4
├── gradle.properties
└── app/
    ├── build.gradle                # compileOnly XposedBridge API 82
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml     # xposedmodule / xposeddescription / xposedminversion / xposedscope
        ├── assets/xposed_init      # 入口类全名
        ├── res/values/
        │   ├── arrays.xml          # 默认作用域 com.shizi.tool
        │   └── strings.xml
        └── java/com/doubao/adblock/
            ├── HookEntry.java      # 4 个 Hook，每个独立 try/catch
            ├── AdBlockConfig.java  # XSharedPreferences + 3 秒缓存 + 默认值兜底
            └── SettingsActivity.java # 纯系统控件设置页（零第三方依赖）
```
