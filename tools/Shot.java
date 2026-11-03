import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** 截屏工具（自测用）：java Shot out.png */
public final class Shot {
    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "build/shot.png";
        Robot robot = new Robot();
        Rectangle r = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        BufferedImage img = robot.createScreenCapture(r);
        ImageIO.write(img, "png", new File(out));
        System.out.println("saved " + out + " " + r.width + "x" + r.height);
    }
}
