package com.doubao.adblock;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

/**
 * 模块配置：由「设置页写」→「宿主进程里的 Hook 读」。
 *
 * 通道：XSharedPreferences（LSPosed 已兼容实现，无需世界可读权限）。
 * 缓存：宿主侧每 3 秒最多重读一次，避免每次广告调用都做 I/O。
 * 兜底：prefs 读不到时按「开 = 拦截、保留激励」的默认值工作，保证装上即生效。
 */
public final class AdBlockConfig {

    public static final String MODULE_PKG = "com.doubao.adblock";
    public static final String PREFS_NAME = "adblock";

    public static final String KEY_ENABLED      = "enabled";       // 总开关
    public static final String KEY_KEEP_REWARD  = "keep_reward";   // 保留激励视频
    public static final String KEY_AGGRESSIVE   = "aggressive";    // 激进模式：不初始化广告 SDK

    private static final String TAG = "[AdBlock] ";
    private static final long RELOAD_INTERVAL_MS = 3000L;

    private static AdBlockConfig sInstance;

    private final XSharedPreferences prefs;
    private long lastReloadMs = 0L;

    private boolean enabled = true;
    private boolean keepReward = true;
    private boolean aggressive = false;

    private AdBlockConfig() {
        XSharedPreferences p = null;
        try {
            p = new XSharedPreferences(MODULE_PKG, PREFS_NAME);
            p.reload();
        } catch (Throwable t) {
            XposedBridge.log(TAG + "XSharedPreferences 不可用，使用默认值: " + t);
            p = null;
        }
        this.prefs = p;
        readNow();
    }

    public static synchronized AdBlockConfig get() {
        if (sInstance == null) {
            sInstance = new AdBlockConfig();
        }
        return sInstance;
    }

    private void readNow() {
        if (prefs == null) {
            return;
        }
        try {
            enabled = prefs.getBoolean(KEY_ENABLED, true);
            keepReward = prefs.getBoolean(KEY_KEEP_REWARD, true);
            aggressive = prefs.getBoolean(KEY_AGGRESSIVE, false);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "读取配置失败，沿用旧值: " + t);
        }
        lastReloadMs = System.currentTimeMillis();
    }

    private void maybeReload() {
        long now = System.currentTimeMillis();
        if (now - lastReloadMs < RELOAD_INTERVAL_MS) {
            return;
        }
        if (prefs != null) {
            try {
                prefs.reload();
            } catch (Throwable ignored) {
                // reload 失败沿用量
            }
        }
        readNow();
    }

    public boolean isEnabled() {
        maybeReload();
        return enabled;
    }

    public boolean isKeepReward() {
        maybeReload();
        return keepReward;
    }

    public boolean isAggressive() {
        maybeReload();
        return aggressive;
    }
}
