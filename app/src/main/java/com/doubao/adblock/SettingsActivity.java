package com.doubao.adblock;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 模块设置页（纯系统控件，零第三方依赖）。
 * 写入 SharedPreferences("adblock")，宿主进程内的 Hook 通过 XSharedPreferences 读取。
 */
public class SettingsActivity extends Activity {

    private static final String TARGET_PKG = "com.shizi.tool";

    private SharedPreferences sp;
    private Switch swEnabled;
    private Switch swKeepReward;
    private Switch swAggressive;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sp = getSharedPreferences(AdBlockConfig.PREFS_NAME, MODE_PRIVATE);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- 标题
        TextView title = new TextView(this);
        title.setText("豚豚剧广告拦截");
        title.setTextSize(20f);
        root.addView(title);

        // ---- 说明
        TextView desc = new TextView(this);
        desc.setText("作用域：com.shizi.tool（豚豚剧）\n"
                + "改动开关后建议到 LSPosed 里「强制停止」豚豚剧再启动，使 Hook 重新加载。");
        desc.setTextSize(13f);
        desc.setPadding(0, dp(8), 0, dp(16));
        root.addView(desc);

        // ---- 总开关
        swEnabled = addSwitch(root, "屏蔽广告（总开关）",
                "关闭后本模块完全不做任何拦截，等同未安装。",
                sp.getBoolean(AdBlockConfig.KEY_ENABLED, true));
        swEnabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                sp.edit().putBoolean(AdBlockConfig.KEY_ENABLED, isChecked).apply();
            }
        });

        // ---- 保留激励视频
        swKeepReward = addSwitch(root, "保留激励视频（强烈建议开启）",
                "TaskReward 是下载、投屏、福利、兑换、扫码的解锁货币，关掉会导致这些功能不可用。",
                sp.getBoolean(AdBlockConfig.KEY_KEEP_REWARD, true));
        swKeepReward.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                sp.edit().putBoolean(AdBlockConfig.KEY_KEEP_REWARD, isChecked).apply();
            }
        });

        // ---- 激进模式
        swAggressive = addSwitch(root, "激进模式：完全不初始化广告 SDK",
                "启动更快、内存更低，但激励视频也会失效；且部分「等 SDK 就绪」的按钮可能一直置灰。默认关闭。",
                sp.getBoolean(AdBlockConfig.KEY_AGGRESSIVE, false));
        swAggressive.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                sp.edit().putBoolean(AdBlockConfig.KEY_AGGRESSIVE, isChecked).apply();
                Toast.makeText(SettingsActivity.this,
                        "激进模式需强制停止豚豚剧后生效", Toast.LENGTH_SHORT).show();
            }
        });

        // ---- 打开宿主
        Button open = new Button(this);
        open.setText("打开豚豚剧");
        open.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = getPackageManager().getLaunchIntentForPackage(TARGET_PKG);
                if (i == null) {
                    Toast.makeText(SettingsActivity.this, "未安装 豚豚剧", Toast.LENGTH_SHORT).show();
                    return;
                }
                startActivity(i);
            }
        });
        root.addView(open, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- 生效验证提示
        TextView tip = new TextView(this);
        tip.setText("\n验证是否生效：\n"
                + "1) 冷启动无开屏广告；\n"
                + "2) 首页无 Banner / 信息流广告卡；\n"
                + "3) 点播放 2 秒内起播，切集、暂停均不弹广告；\n"
                + "4) 激励视频仍能完整播放并到账。\n\n"
                + "日志查看（连电脑）：adb logcat -s LSPosed-Bridge | grep AdBlock");
        tip.setTextSize(12f);
        root.addView(tip);

        setContentView(scroll);
    }

    private Switch addSwitch(LinearLayout parent, String title, String subtitle, boolean checked) {
        Switch sw = new Switch(this);
        sw.setText(title);
        sw.setChecked(checked);
        sw.setPadding(0, dp(12), 0, 0);
        parent.addView(sw);

        TextView sub = new TextView(this);
        sub.setText(subtitle);
        sub.setTextSize(12f);
        sub.setPadding(dp(4), 0, 0, dp(6));
        parent.addView(sub);
        return sw;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
