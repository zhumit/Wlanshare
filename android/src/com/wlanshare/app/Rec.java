package com.wlanshare.app;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;

import java.io.ByteArrayOutputStream;
import java.io.File;

/** Android 端语音录制与播放（16kHz / 16bit / 单声道 WAV，与 PC 端一致）。 */
public final class Rec {

    public static final int RATE = 16000;

    private Rec() {
    }

    public static byte[] toWav(byte[] pcm, int sampleRate) {
        int dataLen = pcm.length;
        byte[] out = new byte[44 + dataLen];
        writeAscii(out, 0, "RIFF");
        writeInt(out, 4, 36 + dataLen);
        writeAscii(out, 8, "WAVE");
        writeAscii(out, 12, "fmt ");
        writeInt(out, 16, 16);
        writeShort(out, 20, 1);
        writeShort(out, 22, 1);
        writeInt(out, 24, sampleRate);
        writeInt(out, 28, sampleRate * 2);
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

    /** 录音器。 */
    public static class Recorder {
        private AudioRecord ar;
        private Thread thread;
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        private volatile boolean running;
        private long startedAt;

        public boolean start() {
            try {
                int min = AudioRecord.getMinBufferSize(RATE,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                ar = new AudioRecord(MediaRecorder.AudioSource.MIC, RATE,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                        Math.max(min, RATE * 2));
                if (ar.getState() != AudioRecord.STATE_INITIALIZED) {
                    return false;
                }
                ar.startRecording();
                running = true;
                startedAt = System.currentTimeMillis();
                thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        byte[] tmp = new byte[3200];
                        while (running) {
                            int n = ar.read(tmp, 0, tmp.length);
                            if (n > 0) {
                                buf.write(tmp, 0, n);
                                if (buf.size() > RATE * 2 * 120) {
                                    break;
                                }
                            }
                        }
                    }
                }, "rec");
                thread.setDaemon(true);
                thread.start();
                return true;
            } catch (Throwable t) {
                return false;
            }
        }

        public int seconds() {
            return startedAt == 0 ? 0 : (int) ((System.currentTimeMillis() - startedAt) / 1000);
        }

        public byte[] stop() {
            running = false;
            try {
                if (ar != null) {
                    ar.stop();
                    ar.release();
                }
            } catch (Throwable ignored) {
            }
            ar = null;
            byte[] pcm = buf.toByteArray();
            if (pcm.length < RATE / 2) {
                return null;
            }
            return toWav(pcm, RATE);
        }

        public void cancel() {
            running = false;
            try {
                if (ar != null) {
                    ar.stop();
                    ar.release();
                }
            } catch (Throwable ignored) {
            }
            ar = null;
            buf.reset();
        }
    }

    /** 播放 wav；返回 MediaPlayer 便于停止。 */
    public static MediaPlayer play(File file, final Runnable onEnd) {
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setDataSource(file.getAbsolutePath());
            mp.prepare();
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mediaPlayer) {
                    try {
                        mediaPlayer.release();
                    } catch (Throwable ignored) {
                    }
                    if (onEnd != null) {
                        onEnd.run();
                    }
                }
            });
            mp.start();
            return mp;
        } catch (Throwable t) {
            return null;
        }
    }
}
