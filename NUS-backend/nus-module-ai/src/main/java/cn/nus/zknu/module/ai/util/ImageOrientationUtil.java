package cn.nus.zknu.module.ai.util;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

@Slf4j
public final class ImageOrientationUtil {

    private ImageOrientationUtil() {}

    /** 按 EXIF Orientation 把图片转正后重新编码为 JPEG；无需旋转或失败时返回原字节 */
    public static byte[] normalize(byte[] original) {
        if (original == null || original.length == 0) return original;
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(original));
            ExifIFD0Directory dir = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            Integer orientation = dir == null ? null : dir.getInteger(ExifIFD0Directory.TAG_ORIENTATION);
            int o = orientation == null ? 1 : orientation;
            if (o == 1) return original; // 方向正常，不动

            BufferedImage src = ImageIO.read(new ByteArrayInputStream(original));
            if (src == null) return original;

            int w = src.getWidth(), h = src.getHeight();
            boolean swap = o >= 5 && o <= 8;
            BufferedImage out = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.translate(out.getWidth() / 2.0, out.getHeight() / 2.0);
            switch (o) {
                case 2 -> g.scale(-1, 1);                       // 水平镜像
                case 3 -> g.rotate(Math.PI);                    // 180°
                case 4 -> g.scale(1, -1);                       // 垂直镜像
                case 5 -> { g.rotate(Math.PI / 2); g.scale(-1, 1); }
                case 6 -> g.rotate(Math.PI / 2);                // 顺时针 90°（手机最常见）
                case 7 -> { g.rotate(-Math.PI / 2); g.scale(-1, 1); }
                case 8 -> g.rotate(-Math.PI / 2);               // 顺时针 270°
                default -> { g.dispose(); return original; }
            }
            g.drawImage(src, -w / 2, -h / 2, w, h, null);
            g.dispose();

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(out, "jpg", bos);
            return bos.toByteArray();
        } catch (Exception e) {
            log.warn("[normalize] EXIF 转正失败，使用原图", e);
            return original; // 出错也不能影响批改主流程
        }
    }
}
