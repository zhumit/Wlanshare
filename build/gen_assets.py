"""生成图标资源：Android 各密度 launcher PNG + Windows .ico（纯 Python，无第三方依赖）。"""

import os
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

SIZES = {
    'mipmap-mdpi': 48,
    'mipmap-hdpi': 72,
    'mipmap-xhdpi': 96,
    'mipmap-xxhdpi': 144,
    'mipmap-xxxhdpi': 192,
}


def rounded_mask(x, y, r):
    if x < r and y < r:
        return (r - x) ** 2 + (r - y) ** 2 <= r * r
    if x > 1 - r and y < r:
        return (x - (1 - r)) ** 2 + (r - y) ** 2 <= r * r
    if x < r and y > 1 - r:
        return (r - x) ** 2 + (y - (1 - r)) ** 2 <= r * r
    if x > 1 - r and y > 1 - r:
        return (x - (1 - r)) ** 2 + (y - (1 - r)) ** 2 <= r * r
    return True


def sample(px, py, size):
    x = px / size
    y = py / size
    if not rounded_mask(x, y, 0.235):
        return (0, 0, 0, 0)
    t = (x + y) / 2.0
    r = int(0x3D + (0x6D - 0x3D) * t)
    g = int(0x7B + (0x5E - 0x7B) * t)
    b = int(0xFF + (0xF8 - 0xFF) * t)
    for yy in (0.335, 0.5, 0.665):
        if abs(y - yy) < 0.045 and 0.26 <= x <= 0.585:
            return (255, 255, 255, 255)
        if (x - 0.685) ** 2 + (y - yy) ** 2 < 0.028 ** 2:
            return (255, 255, 255, 255)
    return (r, g, b, 255)


def render(size, ss=3):
    out = bytearray()
    for y in range(size):
        out.append(0)
        for x in range(size):
            ar = ag = ab = aa = 0
            for sy in range(ss):
                for sx in range(ss):
                    r, g, b, a = sample(x + (sx + 0.5) / ss, y + (sy + 0.5) / ss, size)
                    ar += r * a
                    ag += g * a
                    ab += b * a
                    aa += a
            n = ss * ss
            if aa == 0:
                out += bytes((0, 0, 0, 0))
            else:
                out += bytes((ar // aa, ag // aa, ab // aa, aa // n))
    return bytes(out)


def png_bytes(size, ss=3):
    raw = render(size, ss)

    def chunk(tag, data):
        return (struct.pack('>I', len(data)) + tag + data
                + struct.pack('>I', zlib.crc32(tag + data) & 0xFFFFFFFF))

    out = b'\x89PNG\r\n\x1a\n'
    out += chunk(b'IHDR', struct.pack('>IIBBBBB', size, size, 8, 6, 0, 0, 0))
    out += chunk(b'IDAT', zlib.compress(raw, 9))
    out += chunk(b'IEND', b'')
    return out


def write_ico(path, png, size):
    header = struct.pack('<HHH', 0, 1, 1)
    entry = struct.pack('<BBBBHHII', 0 if size >= 256 else size, 0 if size >= 256 else size,
                        0, 0, 1, 32, len(png), 22)
    with open(path, 'wb') as f:
        f.write(header + entry + png)


def main():
    for folder, size in SIZES.items():
        d = os.path.join(ROOT, 'android', 'res', folder)
        os.makedirs(d, exist_ok=True)
        with open(os.path.join(d, 'ic_launcher.png'), 'wb') as f:
            f.write(png_bytes(size))
    os.makedirs(os.path.join(ROOT, 'build'), exist_ok=True)
    write_ico(os.path.join(ROOT, 'build', 'app.ico'), png_bytes(256, ss=2), 256)
    print('图标已生成：android/res/mipmap-*, build/app.ico')


if __name__ == '__main__':
    main()
