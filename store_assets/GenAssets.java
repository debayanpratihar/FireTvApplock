import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Generates KidLock TV store assets as PNGs (headless AWT, no external libraries). */
public class GenAssets {

    static final Color INDIGO = new Color(0x5B4BE1);
    static final Color BLUE = new Color(0x0A84FF);
    static final Color KEY = new Color(0x0A84FF);
    static final Color BG_DARK = new Color(0x0B0B0D);
    static final Color BG_DARK2 = new Color(0x16161C);
    static final Color GRAY = new Color(0x98989F);

    public static void main(String[] args) throws Exception {
        String outDir = args.length > 0 ? args[0] : "store_assets";
        File dir = new File(outDir);
        dir.mkdirs();

        write(iconSquare(512), new File(dir, "icon_512x512.png"));
        write(iconSquare(114), new File(dir, "icon_114x114.png"));
        write(promo(1024, 500), new File(dir, "promo_1024x500.png"));
        write(firetv(1280, 720), new File(dir, "firetv_icon_1280x720.png"));
        write(background(1920, 1080), new File(dir, "background_1920x1080.png"));

        System.out.println("Wrote assets to " + dir.getAbsolutePath());
    }

    // ---- Canvases ----------------------------------------------------------

    static BufferedImage iconSquare(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = setup(img);
        g.setPaint(new GradientPaint(0, 0, INDIGO, size, size, BLUE));
        g.fillRect(0, 0, size, size);
        drawLogo(g, size / 2.0, size / 2.0, size * 0.62);
        g.dispose();
        return img;
    }

    static BufferedImage promo(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = setup(img);
        g.setPaint(new GradientPaint(0, 0, BG_DARK, w, h, BG_DARK2));
        g.fillRect(0, 0, w, h);
        // subtle accent panel behind the logo
        g.setColor(new Color(0x0A84FF & 0xFFFFFF | 0x22000000, true));
        drawLogo(g, h * 0.5, h * 0.5, h * 0.6);
        double tx = h * 0.95;
        drawText(g, "KidLock TV", tx, h * 0.42, 92, Font.BOLD, Color.WHITE);
        drawText(g, "Lock any app on your TV with a PIN.", tx, h * 0.60, 34, Font.PLAIN, GRAY);
        g.dispose();
        return img;
    }

    static BufferedImage firetv(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = setup(img);
        RadialGradientPaint rgp = new RadialGradientPaint(
                new Point(w / 2, h / 2), w / 1.4f,
                new float[]{0f, 1f}, new Color[]{BG_DARK2, BG_DARK});
        g.setPaint(rgp);
        g.fillRect(0, 0, w, h);
        drawLogo(g, w / 2.0, h * 0.40, h * 0.34);
        drawTextCentered(g, "KidLock TV", w / 2.0, h * 0.74, 104, Font.BOLD, Color.WHITE);
        drawTextCentered(g, "Parental app lock for Fire TV", w / 2.0, h * 0.86, 36, Font.PLAIN, GRAY);
        g.dispose();
        return img;
    }

    static BufferedImage background(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = setup(img);
        g.setPaint(new GradientPaint(0, 0, BG_DARK, w, h, new Color(0x101018)));
        g.fillRect(0, 0, w, h);
        // large faint watermark lock on the right
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.10f));
        drawLogo(g, w * 0.80, h * 0.52, h * 0.85);
        g.setComposite(old);
        drawText(g, "KidLock TV", w * 0.07, h * 0.60, 96, Font.BOLD, Color.WHITE);
        drawText(g, "Lock apps on your TV. Kids only open what you allow.", w * 0.07, h * 0.70, 38, Font.PLAIN, GRAY);
        g.dispose();
        return img;
    }

    // ---- Drawing helpers ---------------------------------------------------

    static Graphics2D setup(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }

    /** Draws the padlock (108-unit design) centered at (cx,cy) fitted to a box of side [box]. */
    static void drawLogo(Graphics2D gIn, double cx, double cy, double box) {
        Graphics2D g = (Graphics2D) gIn.create();
        g.translate(cx, cy);
        double s = box / 108.0;
        g.scale(s, s);
        g.translate(-54, -54);

        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        // shackle: two verticals + top semicircle (round caps join them)
        g.draw(new Line2D.Double(40, 48, 40, 40));
        g.draw(new Arc2D.Double(40, 26, 28, 28, 0, 180, Arc2D.OPEN));
        g.draw(new Line2D.Double(68, 48, 68, 40));
        // body
        g.fill(new RoundRectangle2D.Double(34, 48, 40, 32, 16, 16));
        // keyhole
        g.setColor(KEY);
        g.fill(new Ellipse2D.Double(49, 55, 10, 10));
        g.fill(new Rectangle2D.Double(51.5, 60, 5, 13));
        g.dispose();
    }

    static void drawText(Graphics2D g, String text, double x, double baselineY, int size, int style, Color c) {
        g.setColor(c);
        g.setFont(new Font(Font.SANS_SERIF, style, size));
        g.drawString(text, (float) x, (float) baselineY);
    }

    static void drawTextCentered(Graphics2D g, String text, double cx, double baselineY, int size, int style, Color c) {
        g.setColor(c);
        Font f = new Font(Font.SANS_SERIF, style, size);
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        double w = fm.stringWidth(text);
        g.drawString(text, (float) (cx - w / 2), (float) baselineY);
    }

    static void write(BufferedImage img, File f) throws Exception {
        ImageIO.write(img, "png", f);
        System.out.println("  " + f.getName() + "  " + img.getWidth() + "x" + img.getHeight());
    }
}
