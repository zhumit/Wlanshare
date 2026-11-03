package com.wlanshare.ui;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;

/** 语音录制与播放（16kHz / 16bit / 单声道 WAV，两端通用）。 */
public final class Voice {

    public static final float SAMPLE_RATE = 16000f;

    private Voice() {
    }

    public static AudioFormat format() {
        return new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
    }

    public static boolean canRecord() {
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format());
            return AudioSystem.isLineSupported(info);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 给 PCM 数据加上 44 字节 WAV 头。 */
    public static byte[] toWav(byte[] pcm, int sampleRate) {
        int dataLen = pcm.length;
        byte[] out = new byte[44 + dataLen];
        int byteRate = sampleRate * 2;
        writeAscii(out, 0, "RIFF");
        writeInt(out, 4, 36 + dataLen);
        writeAscii(out, 8, "WAVE");
        writeAscii(out, 12, "fmt ");
        writeInt(out, 16, 16);
        writeShort(out, 20, 1);
        writeShort(out, 22, 1);
        writeInt(out, 24, sampleRate);
        writeInt(out, 28, byteRate);
        writeShort(out, 32, 2);
        writeShort(out, 34, 16);
        writeAscii(out, 36, "data");
        writeInt(out, 40, dataLen);
        System.arraycopy(pcm, 0, out, 44, dataLen);
        return out;
    }

    private static void writeAscii(byte[] b, int off, String s) {
        for (int i = 0; i < s.length(); i++) {
            b[off + i] = (byte) s.charAt(i);
        }
    }

    private static void writeInt(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xff);
        b[off + 1] = (byte) ((v >> 8) & 0xff);
        b[off + 2] = (byte) ((v >> 16) & 0xff);
        b[off + 3] = (byte) ((v >> 24) & 0xff);
    }

    private static void writeShort(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xff);
        b[off + 1] = (byte) ((v >> 8) & 0xff);
    }

    /** 录音器：start() 之后在后台持续采集，stop() 返回 WAV 字节。 */
    public static class Recorder {
        private TargetDataLine line;
        private Thread thread;
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        private volatile boolean running;
        private long startedAt;

        public boolean start() {
            try {
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, format());
                if (!AudioSystem.isLineSupported(info)) {
                    return false;
                }
                line = (TargetDataLine) AudioSystem.getLine(info);
                line.open(format(), 16000 * 4);
                line.start();
                running = true;
                startedAt = System.currentTimeMillis();
                thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        byte[] tmp = new byte[3200];
                        while (running) {
                            int n = line.read(tmp, 0, tmp.length);
                            if (n > 0) {
                                buf.write(tmp, 0, n);
                                if (buf.size() > 16000 * 2 * 120) {
                                    break; // 最长 2 分钟
                                }
                            }
                        }
                    }
                }, "voice-recorder");
                thread.setDaemon(true);
                thread.start();
                return true;
            } catch (Throwable t) {
                return false;
            }
        }

        public int seconds() {
            if (startedAt == 0) {
                return 0;
            }
            return (int) ((System.currentTimeMillis() - startedAt) / 1000);
        }

        /** 停止并返回 WAV（过长或过短返回 null）。 */
        public byte[] stop() {
            running = false;
            try {
                if (line != null) {
                    line.stop();
                    line.close();
                }
            } catch (Throwable ignored) {
            }
            byte[] pcm = buf.toByteArray();
            if (pcm.length < SAMPLE_RATE * 2 / 2) { // 少于 0.5 秒
                return null;
            }
            return toWav(pcm, (int) SAMPLE_RATE);
        }

        public void cancel() {
            running = false;
            try {
                if (line != null) {
                    line.stop();
                    line.close();
                }
            } catch (Throwable ignored) {
            }
            buf.reset();
        }
    }

    /** 播放 WAV；播放结束回调（在音频线程）。 */
    public static Clip play(File file, final Runnable onDone) {
        try {
            final Clip clip = AudioSystem.getClip();
            AudioInputStream in = AudioSystem.getAudioInputStream(file);
            clip.open(in);
            clip.addLineListener(event -> {
                if (event.getType() == javax.sound.sampled.LineEvent.Type.STOP) {
                    try {
                        clip.close();
                    } catch (Exception ignored) {
                    }
                    if (onDone != null) {
                        onDone.run();
                    }
                }
            });
            clip.start();
            return clip;
        } catch (Throwable t) {
            return null;
        }
    }

    public static Clip playBytes(byte[] wav, Runnable onDone) {
        try {
            final Clip clip = AudioSystem.getClip();
            AudioInputStream in = AudioSystem.getAudioInputStream(
                    new ByteArrayInputStream(wav));
            clip.open(in);
            clip.addLineListener(event -> {
                if (event.getType() == javax.sound.sampled.LineEvent.Type.STOP) {
                    try {
                        clip.close();
                    } catch (Exception ignored) {
                    }
                    if (onDone != null) {
                        onDone.run();
                    }
                }
            });
            clip.start();
            return clip;
        } catch (Throwable t) {
            return null;
        }
    }
}
