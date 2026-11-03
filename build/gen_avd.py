"""生成位于项目内的 AVD（避免污染用户目录），用于自动化验证。"""
import os

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "avd")
AVD = os.path.join(BASE, "wls.avd")

os.makedirs(AVD, exist_ok=True)

with open(os.path.join(BASE, "wls.ini"), "w", encoding="utf-8") as f:
    f.write("avd.ini.encoding=UTF-8\n")
    f.write("path=" + AVD.replace("/", "\\") + "\n")
    f.write("path.rel=avd\\wls.avd\n")
    f.write("target=android-31\n")

cfg = [
    "AvdId=wls",
    "avd.ini.displayname=wls",
    "avd.ini.encoding=UTF-8",
    "abi.type=x86_64",
    "hw.cpu.arch=x86_64",
    "hw.cpu.ncore=4",
    "hw.ramSize=2048",
    "hw.lcd.density=420",
    "hw.lcd.width=1080",
    "hw.lcd.height=2280",
    "hw.keyboard=yes",
    "hw.gpu.enabled=yes",
    "hw.gpu.mode=swiftshader_indirect",
    "image.sysdir.1=system-images\\android-31\\google_apis\\x86_64\\",
    "tag.id=google_apis",
    "tag.display=Google APIs",
    "disk.dataPartition.size=2048M",
    "vm.heapSize=256",
    "PlayStore.enabled=false",
    "fastboot.forceColdBoot=yes",
    "",
]
with open(os.path.join(AVD, "config.ini"), "w", encoding="utf-8") as f:
    f.write("\n".join(cfg))

print("avd at", AVD)
