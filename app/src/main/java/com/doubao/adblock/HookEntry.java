package com.doubao.adblock;

import java.lang.reflect.Method;
import java.util.Locale;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 豚豚剧 (com.shizi.tool) 广告拦截 —— LSPosed 模块入口。
 *
 * 设计原则（对应第二轮逆向报告的 G1/G2/G3）：
 *   H1 AdvertisingState.shouldShow(slot)   → 非激励位一律 false（总闸，调用方只有 2 处）
 *   H2 AdSlotVipExemption.exemptReason     → 非激励位一律返回常量（连 SDK 都不请求）
 *   H3 PageAdGateOutcome.getResult()       → 恒 null（等价官方「记忆期内放行」，播放零等待）
 *   H4 AdSdkReadyGate.isSupported()        → false（可选激进模式：广告 SDK 完全不初始化）
 *
 * 约束：TaskReward（激励视频）默认放行 —— 它是下载/投屏/福利/兑换/扫码的解锁货币。
 * 每个 Hook 独立 try/catch：任何一个类名/签名变化只丢一个点，不会让模块整体失效。
 */
public class HookEntry implements IXposedHookLoadPackage {

    private static final String TAG = "[AdBlock] ";
    private static final String TARGET_PKG = "com.shizi.tool";

    // ---- 目标坐标（业务包 com.yy.myuko 未被 R8 混淆，类名跨版本稳定；方法在 classes8.dex）----
    private static final String CLS_SLOT              = "com.yy.myuko.shared.domain.ad.AdSlot";
    private static final String CLS_ADVERTISING_STATE = "com.yy.myuko.shared.domain.ad.AdvertisingState";
    private static final String CLS_VIP_EXEMPTION    = "com.yy.myuko.shared.domain.ad.AdSlotVipExemption";
    private static final String CLS_PAGE_GATE_OUTCOME = "com.yy.myuko.shared.presentation.ad.PageAdGateOutcome";
    private static final String CLS_SDK_READY_GATE   = "com.yy.myuko.shared.domain.ad.AdSdkReadyGate";

    /** 注入的豁免原因字符串（只用于让调用方判定「非空即跳过」，内容无业务含义）。 */
    private static final String EXEMPT_REASON = "adfree";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (!TARGET_PKG.equals(lp.packageName)) {
            return;
        }

        AdBlockConfig cfg = AdBlockConfig.get();
        XposedBridge.log(TAG + "loaded pkg=" + lp.packageName
                + " process=" + lp.processName
                + " enabled=" + cfg.isEnabled()
                + " keepReward=" + cfg.isKeepReward()
                + " aggressive=" + cfg.isAggressive());

        hookShouldShow(lp, cfg);
        hookExemptReason(lp, cfg);
        hookPageAdOutcome(lp, cfg);
        if (cfg.isAggressive()) {
            hookSdkReadyGate(lp, cfg);
        }
    }

    // =============================================================== H1 总闸
    private void hookShouldShow(XC_LoadPackage.LoadPackageParam lp, final AdBlockConfig cfg) {
        try {
            XposedHelpers.findAndHookMethod(
                    CLS_ADVERTISING_STATE, lp.classLoader, "shouldShow", CLS_SLOT,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (!cfg.isEnabled()) {
                                return;
                            }
                            String code = slotCodeOf(param.args[0]);
                            if (cfg.isKeepReward() && isTaskReward(code)) {
                                XposedBridge.log(TAG + "H1 pass TaskReward code=" + code);
                                return; // 不拦截激励视频
                            }
                            param.setResult(Boolean.FALSE);
                        }
                    });
            XposedBridge.log(TAG + "H1 ok: AdvertisingState.shouldShow -> false (except TaskReward)");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "H1 FAIL: AdvertisingState.shouldShow -> " + t);
        }
    }

    // =============================================================== H2 本地豁免短路
    private void hookExemptReason(XC_LoadPackage.LoadPackageParam lp, final AdBlockConfig cfg) {
        try {
            XposedHelpers.findAndHookMethod(
                    CLS_VIP_EXEMPTION, lp.classLoader, "exemptReason", CLS_SLOT,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (!cfg.isEnabled()) {
                                return;
                            }
                            String code = slotCodeOf(param.args[0]);
                            if (cfg.isKeepReward() && isTaskReward(code)) {
                                // 原方法对 TaskReward 首句就 return null；这里直接交给原逻辑
                                XposedBridge.log(TAG + "H2 pass TaskReward code=" + code);
                                return;
                            }
                            param.setResult(EXEMPT_REASON);
                        }
                    });
            XposedBridge.log(TAG + "H2 ok: AdSlotVipExemption.exemptReason -> \"" + EXEMPT_REASON + "\"");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "H2 FAIL: AdSlotVipExemption.exemptReason -> " + t);
        }
    }

    // =============================================================== H3 播放页不等广告
    private void hookPageAdOutcome(XC_LoadPackage.LoadPackageParam lp, final AdBlockConfig cfg) {
        try {
            XposedHelpers.findAndHookMethod(
                    CLS_PAGE_GATE_OUTCOME, lp.classLoader, "getResult",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (!cfg.isEnabled()) {
                                return;
                            }
                            // 调用方拿到 null 会打「本次不请求直接放行」并继续播放（官方 fail-open 语义）
                            param.setResult(null);
                        }
                    });
            XposedBridge.log(TAG + "H3 ok: PageAdGateOutcome.getResult -> null");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "H3 FAIL: PageAdGateOutcome.getResult -> " + t);
        }

        try {
            // 实测字段名为 waitMs，getter 是 getWaitMs()J（不是 getElapsedMs）
            XposedHelpers.findAndHookMethod(
                    CLS_PAGE_GATE_OUTCOME, lp.classLoader, "getWaitMs",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (!cfg.isEnabled()) {
                                return;
                            }
                            param.setResult(0L);
                        }
                    });
            XposedBridge.log(TAG + "H3 ok: PageAdGateOutcome.getWaitMs -> 0");
        } catch (Throwable t) {
            // 该字段只用于调用方日志/统计，拿不到不影响放行
            XposedBridge.log(TAG + "H3 skip getWaitMs: " + t);
        }
    }

    // =============================================================== H4 可选：SDK 不初始化
    private void hookSdkReadyGate(XC_LoadPackage.LoadPackageParam lp, final AdBlockConfig cfg) {
        try {
            Class<?> clazz = XposedHelpers.findClass(CLS_SDK_READY_GATE, lp.classLoader);
            int hooked = 0;
            for (Method m : clazz.getDeclaredMethods()) {
                if (!"isSupported".equals(m.getName())) {
                    continue;
                }
                if (m.getReturnType() != boolean.class || m.getParameterTypes().length != 0) {
                    continue;
                }
                m.setAccessible(true);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!cfg.isEnabled()) {
                            return;
                        }
                        param.setResult(Boolean.FALSE);
                    }
                });
                hooked++;
            }
            XposedBridge.log(TAG + "H4 ok: AdSdkReadyGate.isSupported hooked=" + hooked
                    + "（激进模式：广告 SDK 不再初始化）");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "H4 FAIL: AdSdkReadyGate.isSupported -> " + t);
        }
    }

    // =============================================================== 工具
    /** 反射取 AdSlot.code；取不到返回空串（此时按「非激励」处理，宁可多拦）。 */
    private static String slotCodeOf(Object slot) {
        if (slot == null) {
            return "";
        }
        try {
            Object code = XposedHelpers.callMethod(slot, "getCode");
            return code instanceof String ? (String) code : "";
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * 兼容 "TaskReward" / "task_reward" / "task-reward" / "TASK_REWARD" 等写法。
     * 逆向时 12 个 slotCode 是 PascalCase（Splash、PlayerPreRoll…），但服务端字段常为 snake_case，
     * 因此这里做去分隔符 + 大小写归一后再判断，避免因拼写差异把激励视频一起掐掉。
     */
    private static boolean isTaskReward(String code) {
        if (code == null || code.isEmpty()) {
            return false;
        }
        String norm = code.replace("_", "").replace("-", "").replace(" ", "")
                .toLowerCase(Locale.ROOT);
        return norm.contains("taskreward");
    }
}
