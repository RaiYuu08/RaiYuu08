import java.awt.*;
import java.awt.image.*;
import java.io.File;
import javax.imageio.*;
import javax.imageio.metadata.*;
import javax.imageio.stream.ImageOutputStream;

/** Builds the profile animation from an edited character PNG. Requires Java 11+. */
public class BuildCodingGif {
    static final int W = 480, H = 324;
    static final int[] RG = {0, 25, 60, 110, 180, 255};
    static final int[] B = {0, 18, 45, 80, 130, 195, 255};

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: java tools/BuildCodingGif.java source.png output.gif");
        BufferedImage source = ImageIO.read(new File(args[0]));
        ImageWriter writer = ImageIO.getImageWritersBySuffix("gif").next();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(new File(args[1]))) {
            writer.setOutput(out);
            writer.prepareWriteSequence(null);
            int[] offsets = {1, 1, 0, 0, 1, 1, 2, 2};
            for (int i = 0; i < offsets.length; i++) {
                BufferedImage art = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = art.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g.drawImage(source, 0, offsets[i], W, 320, null);
                // Blink a small terminal cursor inside the simplified monitor.
                if (i < 4) {
                    g.setColor(new Color(30, 210, 240));
                    g.fillRect(294, 119 + offsets[i], 2, 5);
                }
                g.dispose();
                BufferedImage frame = indexed(art);
                IIOMetadata meta = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(frame), writer.getDefaultWriteParam());
                String format = meta.getNativeMetadataFormatName();
                IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree(format);
                IIOMetadataNode control = child(root, "GraphicControlExtension");
                control.setAttribute("disposalMethod", "restoreToBackgroundColor");
                control.setAttribute("userInputFlag", "FALSE");
                control.setAttribute("transparentColorFlag", "TRUE");
                control.setAttribute("delayTime", "14");
                control.setAttribute("transparentColorIndex", "0");
                if (i == 0) {
                    IIOMetadataNode loop = new IIOMetadataNode("ApplicationExtension");
                    loop.setAttribute("applicationID", "NETSCAPE");
                    loop.setAttribute("authenticationCode", "2.0");
                    loop.setUserObject(new byte[]{1, 0, 0});
                    child(root, "ApplicationExtensions").appendChild(loop);
                }
                meta.setFromTree(format, root);
                writer.writeToSequence(new IIOImage(frame, null, meta), writer.getDefaultWriteParam());
            }
            writer.endWriteSequence();
        } finally { writer.dispose(); }
    }

    static BufferedImage indexed(BufferedImage source) {
        byte[] r = new byte[256], g = new byte[256], b = new byte[256];
        for (int ri = 0; ri < 6; ri++) for (int gi = 0; gi < 6; gi++) for (int bi = 0; bi < 7; bi++) {
            int n = 1 + ri * 42 + gi * 7 + bi;
            r[n] = (byte) RG[ri]; g[n] = (byte) RG[gi]; b[n] = (byte) B[bi];
        }
        BufferedImage dest = new BufferedImage(W, H, BufferedImage.TYPE_BYTE_INDEXED, new IndexColorModel(8, 253, r, g, b, 0));
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int pixel = source.getRGB(x, y);
            int n = (pixel >>> 24) < 96 ? 0 : 1 + nearest((pixel >>> 16) & 255, RG) * 42 + nearest((pixel >>> 8) & 255, RG) * 7 + nearest(pixel & 255, B);
            dest.getRaster().setSample(x, y, 0, n);
        }
        return dest;
    }

    static int nearest(int value, int[] levels) {
        int best = 0;
        for (int i = 1; i < levels.length; i++) if (Math.abs(value - levels[i]) < Math.abs(value - levels[best])) best = i;
        return best;
    }

    static IIOMetadataNode child(IIOMetadataNode root, String name) {
        for (int i = 0; i < root.getLength(); i++) if (root.item(i).getNodeName().equals(name)) return (IIOMetadataNode) root.item(i);
        IIOMetadataNode node = new IIOMetadataNode(name);
        root.appendChild(node);
        return node;
    }
}
