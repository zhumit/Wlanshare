package com.wlanshare.ui;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.BufferedInputStream;
import java.io.InputStream;

/** 提示音：播放内置的「滴」声（resources/sounds/msg.wav）。 */
public final class Sounds {

    private static Clip clip;
    private static boolean failed;

    private Sounds() {
    }

    /** 新消息提示音（异步播放，不阻塞界面）。 */
    public static void message() {
        play("/sounds/msg.wav");
    }

    private static void play(final String path) {
        if (failed) {
            return;
        }
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (clip == null) {
                        InputStream in = Sounds.class.getResourceAsStream(path);
                        if (in == null) {
                            failed = true;
                            return;
                        }
                        AudioInputStream ais = AudioSystem.getAudioInputStream(
                                new BufferedInputStream(in));
                        clip = AudioSystem.getClip();
                        clip.open(ais);
                    }
                    clip.stop();
                    clip.setFramePosition(0);
                    clip.start();
                } catch (Throwable ex) {
                    // 没有音频设备时静默降级，不打扰使用
                    failed = true;
                }
            }
        }, "wls-sound");
        t.setDaemon(true);
        t.start();
    }
}
