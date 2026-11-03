"""构建 Android APK：aapt2 -> javac -> d8 -> zipalign -> apksigner。"""

import glob
import os
import shutil
import subprocess
import sys
import zipfile

SDK = r'D:/Programs/Android-app/Sdk'
BT = os.path.join(SDK, 'build-tools', '36.1.0')
PLATFORM = os.path.join(SDK, 'platforms', 'android-36', 'android.jar')
JDK = r'C:/Program Files/Microsoft/jdk-21.0.12.101-hotspot'
OUT = 'build/apk'
DIST = 'android/dist'


def rm_tree(path):
    """删除目录：优先 cmd rmdir（Windows trash 对大目录常失败），失败再退回 shutil。"""
    if not os.path.isdir(path):
        return
    env = dict(os.environ)
    env['MSYS_NO_PATHCONV'] = '1'
    try:
        subprocess.run(['cmd', '/c', 'rmdir', '/s', '/q', path],
                       check=False, capture_output=True, env=env)
    except Exception:
        pass
    if os.path.isdir(path):
        try:
            shutil.rmtree(path, ignore_errors=True)
        except Exception:
            pass



def run(args, **kw):
    print('>', ' '.join(args) if isinstance(args, list) else args)
    r = subprocess.run(args, capture_output=True, text=True, encoding='utf-8', errors='replace', **kw)
    out = (r.stdout or '') + (r.stderr or '')
    if out.strip():
        print(out.strip()[:4000])
    if r.returncode != 0:
        print('FAILED rc=%d' % r.returncode)
        sys.exit(1)
    return out


def bat(path):
    return ['cmd', '/c', path]


def main():
    if os.path.isdir(OUT):
        rm_tree(OUT)
    for d in ('compiled', 'classes', 'dex', 'gen'):
        os.makedirs(os.path.join(OUT, d), exist_ok=True)

    # 1. 编译资源
    run([os.path.join(BT, 'aapt2.exe'), 'compile', '--dir', 'android/res',
         '-o', os.path.join(OUT, 'compiled', 'res.zip')])

    # 2. 链接资源，生成 R.java 与基础 APK
    gen = os.path.join(OUT, 'gen')
    run([os.path.join(BT, 'aapt2.exe'), 'link',
         '-o', os.path.join(OUT, 'base.apk'),
         '-I', PLATFORM,
         '--manifest', 'android/AndroidManifest.xml',
         '-R', os.path.join(OUT, 'compiled', 'res.zip'),
         '--java', gen,
         '--min-sdk-version', '24',
         '--target-sdk-version', '33',
         '--version-code', '1',
         '--version-name', '1.0',
         '--auto-add-overlay'])

    # 3. 编译 Java
    sources = []
    for root in ('android/src', 'shared'):
        for path in glob.glob(root + '/**/*.java', recursive=True):
            sources.append(path)
    sources += glob.glob(gen + '/**/*.java', recursive=True)
    classes = os.path.join(OUT, 'classes')
    run([os.path.join(JDK, 'bin', 'javac.exe'), '-encoding', 'UTF-8',
         '-source', '11', '-target', '11', '-nowarn',
         '-classpath', PLATFORM, '-d', classes] + sources)

    # 4. dex
    class_files = []
    for root, _, files in os.walk(classes):
        for f in files:
            if f.endswith('.class'):
                class_files.append(os.path.join(root, f))
    dexdir = os.path.join(OUT, 'dex')
    run(bat(os.path.join(BT, 'd8.bat')) + ['--lib', PLATFORM, '--min-api', '24',
                                           '--output', dexdir] + class_files)

    # 5. 把 classes.dex 塞进 APK
    apk = os.path.join(OUT, 'base.apk')
    with zipfile.ZipFile(apk, 'a', zipfile.ZIP_DEFLATED) as z:
        z.write(os.path.join(dexdir, 'classes.dex'), 'classes.dex')

    # 6. 对齐
    aligned = os.path.join(OUT, 'aligned.apk')
    run([os.path.join(BT, 'zipalign.exe'), '-f', '4', apk, aligned])

    # 7. 签名
    os.makedirs(DIST, exist_ok=True)
    keystore = os.path.expanduser('~/.android/debug.keystore')
    signed = os.path.join(DIST, 'WlanShare.apk')
    run(bat(os.path.join(BT, 'apksigner.bat')) + [
        'sign', '--ks', keystore, '--ks-pass', 'pass:android',
        '--key-pass', 'pass:android', '--ks-key-alias', 'androiddebugkey',
        '--out', signed, aligned])

    # 8. 校验
    run(bat(os.path.join(BT, 'apksigner.bat')) + ['verify', '--verbose', signed])
    run([os.path.join(BT, 'aapt2.exe'), 'dump', 'badging', signed])

    print('\nAPK 输出:', os.path.abspath(signed),
          '%.2f MB' % (os.path.getsize(signed) / 1048576.0))


if __name__ == '__main__':
    main()
