"""
verify_targets.py —— 校验 LSPosed 模块里写死的 Hook 坐标是否存在于目标 APK 中。
用途：App 升级后重跑一遍，确认类名/方法签名没变（变了就改 HookEntry 常量）。
用法：python verify_targets.py <apk路径>
依赖：androguard 4.x（本机已装在 workspace/pylib，需设 PYTHONPATH）
"""
import sys, zipfile

try:
    from loguru import logger
    logger.remove()
except Exception:
    pass

from androguard.core.dex import DEX

TARGETS = [
    # (类, 方法名, 期望描述符关键字, 说明)
    ("Lcom/yy/myuko/shared/domain/ad/AdvertisingState;", "shouldShow",
     "(Lcom/yy/myuko/shared/domain/ad/AdSlot;)Z", "H1 总闸"),
    ("Lcom/yy/myuko/shared/domain/ad/AdSlotVipExemption;", "exemptReason",
     "(Lcom/yy/myuko/shared/domain/ad/AdSlot;)Ljava/lang/String;", "H2 本地豁免"),
    ("Lcom/yy/myuko/shared/presentation/ad/PageAdGateOutcome;", "getResult",
     None, "H3 播放放行"),
    ("Lcom/yy/myuko/shared/presentation/ad/PageAdGateOutcome;", "getWaitMs",
     None, "H3 等待耗时字段(可选)"),
    ("Lcom/yy/myuko/shared/domain/ad/AdSdkReadyGate;", "isSupported",
     None, "H4 激进模式(可选)"),
    ("Lcom/yy/myuko/shared/domain/ad/AdSlot;", "getCode",
     None, "slotCode 取值"),
]


def main(apk):
    zf = zipfile.ZipFile(apk)
    found = {i: [] for i in range(len(TARGETS))}
    for name in [n for n in zf.namelist() if n.endswith(".dex")]:
        try:
            d = DEX(zf.read(name))
        except Exception as e:
            print(f"!! {name} 解析失败: {e}")
            continue
        for c in d.get_classes():
            cn = c.get_name()
            for i, (cls, meth, desc, note) in enumerate(TARGETS):
                if cn != cls:
                    continue
                for m in c.get_methods():
                    if m.get_name() != meth:
                        continue
                    md = m.get_descriptor()
                    if desc and desc not in md:
                        continue
                    found[i].append((name, md))

    ok_all = True
    for i, (cls, meth, desc, note) in enumerate(TARGETS):
        hits = found[i]
        if hits:
            for dex, md in hits:
                print(f"[OK]   {note:16s} {cls[1:-1].replace('/', '.')}#{meth}{md}   ({dex})")
        else:
            ok_all = False
            print(f"[MISS] {note:16s} {cls[1:-1].replace('/', '.')}#{meth}   <-- 该类/方法不存在，Hook 会走 FAIL 分支")

    print("\n结论:", "全部命中，模块坐标有效" if ok_all else "存在 MISS，请按报告 dump 脚本复核后更新 HookEntry")
    return 0 if ok_all else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
