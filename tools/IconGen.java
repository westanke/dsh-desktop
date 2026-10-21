import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 生成应用图标：深青底 + 白色 DS 字标。
 *
 * 先在 1024 画布上完成设计再降采样，保证 32px 也不糊（直接按小尺寸画，
 * 字距与圆角比例都会走样）。颜色取自 ui/Theme.kt 的品牌青 #0E7C86，
 * 向明暗两端各拉一档做渐变，小尺寸下轮廓才立得住。
 */
public class IconGen {

    private static final int BASE = 1024;
    private static final Color TOP = new Color(0x17, 0xA6, 0xB2);
    private static final Color BOTTOM = new Color(0x0A, 0x5C, 0x65);

    public static void main(String[] args) throws Exception {
        String outRoot = args[0];
        BufferedImage base = draw();

        int[] sizes = {256, 128, 64, 48, 32};
        for (int s : sizes) {
            BufferedImage scaled = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(base, 0, 0, s, s, null);
            g.dispose();

            File dir = new File(outRoot, "hicolor/" + s + "x" + s + "/apps");
            dir.mkdirs();
            File file = new File(dir, "dsh-desktop.png");
            ImageIO.write(scaled, "png", file);
            System.out.println("  " + file.getPath());
        }
        // jpackage / desktop 文件里引用的是这个扁平路径
        File flat = new File(outRoot, "dsh-desktop.png");
        BufferedImage big = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = big.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(base, 0, 0, 256, 256, null);
        g.dispose();
        ImageIO.write(big, "png", flat);
        System.out.println("  " + flat.getPath());
    }

    private static BufferedImage draw() throws Exception {
        BufferedImage img = new BufferedImage(BASE, BASE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 底：圆角正方形，圆角半径 22%（UOS 图标风格的圆润度）
        float radius = BASE * 0.44f;
        RoundRectangle2D plate = new RoundRectangle2D.Float(0, 0, BASE, BASE, radius, radius);
        g.setPaint(new GradientPaint(0, 0, TOP, BASE, BASE, BOTTOM));
        g.fill(plate);

        // 顶部一层极淡的高光，让大图标有体积感；缩小后自然消失
        g.setClip(plate);
        g.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, 46), 0, BASE * 0.55f, new Color(255, 255, 255, 0)));
        g.fillRect(0, 0, BASE, (int) (BASE * 0.55f));

        // 字标：视觉居中（用可视边界，不用基线框，否则 DS 会偏上）
        Font font = Font.createFont(Font.TRUETYPE_FONT, new File("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"))
                .deriveFont(BASE * 0.47f);
        FontRenderContext frc = g.getFontRenderContext();
        GlyphVector glyphs = font.createGlyphVector(frc, "DS");
        // 轻微收紧字距，两个字母才像一个整体
        for (int i = 1; i < glyphs.getNumGlyphs(); i++) {
            Point2D_Position(glyphs, i, -BASE * 0.012f);
        }
        Rectangle2D bounds = glyphs.getVisualBounds();
        double x = (BASE - bounds.getWidth()) / 2 - bounds.getX();
        double y = (BASE - bounds.getHeight()) / 2 - bounds.getY();

        g.setPaint(new Color(0, 0, 0, 40));
        g.translate(0, BASE * 0.008);
        g.fill(glyphs.getOutline((float) x, (float) y));
        g.translate(0, -BASE * 0.008);

        g.setPaint(Color.WHITE);
        g.fill(glyphs.getOutline((float) x, (float) y));

        g.dispose();
        return img;
    }

    /** 把第 i 个字形左移 dx（负数=收紧）。 */
    private static void Point2D_Position(GlyphVector gv, int i, double dx) {
        java.awt.geom.Point2D p = gv.getGlyphPosition(i);
        gv.setGlyphPosition(i, new java.awt.geom.Point2D.Double(p.getX() + dx * i, p.getY()));
    }
}
