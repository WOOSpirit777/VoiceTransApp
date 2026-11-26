#!/usr/bin/env python3
"""把离线翻译模型放进 VoiceTransApp 的 assets，用于「APK 内置」模式。

## 两个模型来源
- 中文：14M int8（24MB，已实测 RTF 0.54，见 docs/中文ASR选型报告.md）
- 印尼语：models-flow-A（185MB int8，已验证）

## 目录结构（必须与 AssetCopyInstaller 一致）
```
app/src/main/assets/models/offline-bi-zh/asr/{encoder,decoder,joiner,tokens}
app/src/main/assets/models/offline-bi-zh/mt/{encoder_model,decoder_model,...}
app/src/main/assets/models/offline-bi-id/asr/{...}
```

## 注意
- 打进去后 APK 会增加约 190MB（AAR 已含 native 库，模型另算）。
- 只应在确实需要「完全离线交付、无服务端」时使用；默认走服务端下载。
- 拷贝发生在首次启动（AssetCopyInstaller），之后命中本地缓存不再拷贝。

用法：
    python scripts/pack_assets.py --check     # 只看现状与体积
    python scripts/pack_assets.py --pack      # 执行拷贝
    python scripts/pack_assets.py --clean     # 移除已拷入的 assets
"""
import argparse
import shutil
import sys
from pathlib import Path

APP = Path(r"D:\workspace4ai\VoiceTransApp")
ASSETS_MODELS = APP / "app" / "src" / "main" / "assets" / "models"

# 离线模型根目录（与 OfflineTranslationManager.MODEL_ROOT 一致）
OFFLINE_ROOT = APP.parent / "VoiceTransApp" / ".offline_models"

# 中文模型源：微信目录里的 14M int8
ZH_SRC = Path(
    r"F:\WXWork\5.0.8.6009\WXWork\1688857411657974\Cache\File\2026-11"
    r"\sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23"
    r"\sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23"
)
ZH_FILES = [
    "encoder-epoch-99-avg-1.int8.onnx",
    "decoder-epoch-99-avg-1.int8.onnx",
    "joiner-epoch-99-avg-1.int8.onnx",
    "tokens.txt",
]

# 印尼语模型源：AsrOnnxSdk 的 mock cloud 包（已解压在设备上验证过）
ID_SRC = Path(r"D:\workspace4ai\AsrOnnxSdk\build\cloud\www")
ID_ZIP = "models-flow-A.zip"
MT_ZIP = "models-flow-A-mt.zip"


def human(n: int) -> str:
    return "%.1f MB" % (n / 1024 / 1024)


def dir_size(p: Path) -> int:
    if not p.is_dir():
        return 0
    return sum(f.stat().st_size for f in p.rglob("*") if f.is_file())


def check() -> int:
    print("assets 目标目录: %s" % ASSETS_MODELS)
    print("  存在: %s" % ASSETS_MODELS.is_dir())
    if ASSETS_MODELS.is_dir():
        for d in sorted(ASSETS_MODELS.iterdir()):
            print("  %-24s %s" % (d.name, human(dir_size(d))))
    print()
    print("中文模型源: %s" % ZH_SRC)
    print("  存在: %s  可用文件: %s" % (
        ZH_SRC.is_dir(),
        [f for f in ZH_FILES if (ZH_SRC / f).is_file()],
    ))
    print()
    print("印尼语模型源: %s" % (ID_SRC / ID_ZIP))
    print("  zip 存在: %s  mt 存在: %s" % (
        (ID_SRC / ID_ZIP).is_file(), (ID_SRC / MT_ZIP).is_file()))
    print()
    total = 0
    if (ID_SRC / ID_ZIP).is_file():
        total += (ID_SRC / ID_ZIP).stat().st_size
    if ID_SRC.is_dir():
        pass
    if ZH_SRC.is_dir():
        total += sum((ZH_SRC / f).stat().st_size for f in ZH_FILES if (ZH_SRC / f).is_file())
    print("预计 APK 增量: %s（不含解压后体积）" % human(total))
    return 0


def pack() -> int:
    missing = [f for f in ZH_FILES if not (ZH_SRC / f).is_file()]
    if missing:
        print("FAIL 中文模型缺文件: %s" % missing)
        return 1
    if not (ID_SRC / ID_ZIP).is_file():
        print("FAIL 印尼语模型包不存在: %s" % (ID_SRC / ID_ZIP))
        return 1

    zh_dst = ASSETS_MODELS / "offline-bi-zh" / "asr"
    zh_dst.mkdir(parents=True, exist_ok=True)
    for f in ZH_FILES:
        dst = zh_dst / f
        if dst.is_file() and dst.stat().st_size == (ZH_SRC / f).stat().st_size:
            print("SKIP %s（已存在且大小一致）" % f)
            continue
        shutil.copy2(ZH_SRC / f, dst)
        print("COPY %-40s %s" % (f, human(dst.stat().st_size)))

    print()
    print("印尼语模型需要解压 zip，体积较大（185MB）。")
    print("建议：如果只是验证「切换来源」的 UI 与缓存复用逻辑，")
    print("      可先在设置页把来源切到「APK 内置」观察报错提示是否清晰，")
    print("      确认交互无误后再决定是否真的打包 185MB 进 APK。")
    print()
    print("如需完整打包，手动解压：")
    print("  %s -> %s" % (ID_SRC / ID_ZIP, ASSETS_MODELS / "offline-bi-id" / "asr"))
    print("  %s -> %s" % (ID_SRC / MT_ZIP, ASSETS_MODELS / "offline-bi-zh" / "mt"))
    return 0


def clean() -> int:
    if not ASSETS_MODELS.is_dir():
        print("无需清理：%s 不存在" % ASSETS_MODELS)
        return 0
    shutil.rmtree(ASSETS_MODELS)
    print("已移除 %s" % ASSETS_MODELS)
    return 0


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--pack", action="store_true")
    ap.add_argument("--clean", action="store_true")
    args = ap.parse_args()
    if args.pack:
        return pack()
    if args.clean:
        return clean()
    return check()


if __name__ == "__main__":
    sys.exit(main())