"""一键构建 PC 客户端与 Android APK。

用法：
    python build/build_all.py            # 构建两端
    python build/build_all.py --test     # 先跑协议自测再构建
"""

import glob
import os
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JDK = r'C:/Program Files/Microsoft/jdk-21.0.12.101-hotspot'
ANDROID_SDK = os.environ.get('ANDROID_SDK', r'D:/Programs/Android-app/Sdk')
BT = os.path.join(ANDROID_SDK, 'build-tools', '36.1.0')


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




def run(args, cwd=ROOT):
    print('>', ' '.join(args) if isinstance(args, list) else args)
    r = subprocess.run(args, cwd=cwd, capture_output=True, text=True,
                       encoding='utf-8', errors='replace')
    out = (r.stdout or '') + (r.stderr or '')
    if out.strip():
        print(out.strip()[:3000])
    if r.returncode != 0:
        sys.exit('构建失败 rc=%d' % r.returncode)
    return out


def gen_assets():
    sys.path.insert(0, os.path.join(ROOT, 'build'))
    import gen_assets as g
    g.main()


def build_pc():
    classes = os.path.join(ROOT, 'build', 'pc')
    if os.path.isdir(classes):
        rm_tree(classes)
    os.makedirs(classes, exist_ok=True)
    sources = []
    for d in ('shared', 'pc/src'):
        sources += glob.glob(os.path.join(ROOT, d, '**', '*.java'), recursive=True)
    run([os.path.join(JDK, 'bin', 'javac.exe'), '-encoding', 'UTF-8',
         '--release', '17', '-nowarn', '-d', classes] + sources)

    appinput = os.path.join(ROOT, 'build', 'appinput')
    if os.path.isdir(appinput):
        rm_tree(appinput)
    os.makedirs(appinput, exist_ok=True)
    run([os.path.join(JDK, 'bin', 'jar.exe'), '--create',
         '--file', os.path.join(appinput, 'WlanShare.jar'),
         '--main-class', 'com.wlanshare.Main', '-C', classes, '.',
         '-C', os.path.join(ROOT, 'pc', 'res'), '.'])

    dist = os.path.join(ROOT, 'pc', 'dist')
    if os.path.isdir(dist):
        rm_tree(dist)
    run([os.path.join(JDK, 'bin', 'jpackage.exe'), '--type', 'app-image',
         '--name', 'WlanShare', '--app-version', '1.0',
         '--input', appinput, '--main-jar', 'WlanShare.jar',
         '--main-class', 'com.wlanshare.Main',
         '--runtime-image', os.path.join(ROOT, 'pc', 'env'),
         '--dest', dist,
         '--icon', os.path.join(ROOT, 'build', 'app.ico'),
         '--vendor', 'WlanShare',
         '--description', '局域网文件与协作工具',
         '--java-options', '-Dfile.encoding=UTF-8'])
    print('PC 产物:', os.path.join(dist, 'WlanShare', 'WlanShare.exe'))


def build_apk():
    sys.path.insert(0, os.path.join(ROOT, 'build'))
    os.chdir(ROOT)
    import build_apk
    build_apk.SDK = os.environ.get('ANDROID_SDK', build_apk.SDK)
    build_apk.BT = BT
    build_apk.main()


def run_test():
    classes = os.path.join(ROOT, 'build', 'core')
    if os.path.isdir(classes):
        rm_tree(classes)
    os.makedirs(classes, exist_ok=True)
    sources = glob.glob(os.path.join(ROOT, 'shared', '**', '*.java'), recursive=True)
    run([os.path.join(JDK, 'bin', 'javac.exe'), '-encoding', 'UTF-8',
         '--release', '17', '-nowarn', '-d', classes] + sources)
    test_out = os.path.join(ROOT, 'build', 'test')
    os.makedirs(test_out, exist_ok=True)
    run([os.path.join(JDK, 'bin', 'javac.exe'), '-encoding', 'UTF-8', '--release', '17',
         '-nowarn', '-cp', classes, '-d', test_out,
         os.path.join(ROOT, 'tools', 'SelfTest.java')])
    run([os.path.join(JDK, 'bin', 'java.exe'), '-Dfile.encoding=UTF-8',
         '-cp', classes + os.pathsep + test_out, 'com.wlanshare.selftest.SelfTest'])
    # Markdown 工具栏的插入逻辑自测（需要 PC 端 class）
    pc_out = os.path.join(ROOT, 'build', 'pc')
    if os.path.isdir(pc_out):
        run([os.path.join(JDK, 'bin', 'javac.exe'), '-encoding', 'UTF-8', '-nowarn',
             '-cp', pc_out, '-d', test_out, os.path.join(ROOT, 'tools', 'MdBarTest.java')])
        run([os.path.join(JDK, 'bin', 'java.exe'), '-Dfile.encoding=UTF-8',
             '-Djava.awt.headless=true', '-cp', pc_out + os.pathsep + test_out, 'MdBarTest'])
        run([os.path.join(JDK, 'bin', 'javac.exe'), '-encoding', 'UTF-8', '-nowarn',
             '-cp', pc_out, '-d', test_out, os.path.join(ROOT, 'tools', 'SelTest.java')])
        run([os.path.join(JDK, 'bin', 'java.exe'), '-Dfile.encoding=UTF-8',
             '-Djava.awt.headless=true', '-cp', pc_out + os.pathsep + test_out, 'SelTest'])
    # Markdown 工具栏的插入逻辑自测（需要 PC 端 class）
    pc_out = os.path.join(ROOT, 'build', 'pc')
    if os.path.isdir(pc_out):
        run([os.path.join(JDK, 'bin', 'javac.exe'), '-encoding', 'UTF-8', '-nowarn',
             '-cp', pc_out, '-d', test_out, os.path.join(ROOT, 'tools', 'MdBarTest.java')])
        run([os.path.join(JDK, 'bin', 'java.exe'), '-Dfile.encoding=UTF-8',
             '-Djava.awt.headless=true', '-cp', pc_out + os.pathsep + test_out, 'MdBarTest'])


def main():
    if '--test' in sys.argv:
        run_test()
    gen_assets()
    build_pc()
    build_apk()
    print('\n全部完成：')
    print('  PC  :', os.path.join(ROOT, 'pc', 'dist', 'WlanShare', 'WlanShare.exe'))
    print('  APK :', os.path.join(ROOT, 'android', 'dist', 'WlanShare.apk'))


if __name__ == '__main__':
    main()
