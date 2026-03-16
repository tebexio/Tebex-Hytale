package io.tebex.hytale.plugin.qr;

import javax.annotation.Nonnull;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class QrCodePngRenderer {
    private static final int BLACK = 0x000000;
    private static final int WHITE = 0xFFFFFF;
    private static final int MIN_TEXTURE_SIZE = 32;
    private static final int TEXTURE_SIZE_MULTIPLE = 32;

    private QrCodePngRenderer() {
    }

    @Nonnull
    public static BufferedImage render(@Nonnull QrCode qrCode, int modulePixels, int quietZoneModules) {
        if (qrCode == null) {
            throw new NullPointerException("qrCode");
        }
        if (modulePixels <= 0) {
            throw new IllegalArgumentException("modulePixels must be > 0");
        }
        if (quietZoneModules < 0) {
            throw new IllegalArgumentException("quietZoneModules must be >= 0");
        }

        int qrSize = qrCode.getSize();
        int qrWithQuietZone = qrSize + quietZoneModules * 2;
        int rawImageSize = qrWithQuietZone * modulePixels;
        int imageSize = normalizeTextureSize(rawImageSize);
        int offset = (imageSize - rawImageSize) / 2;
        BufferedImage image = new BufferedImage(imageSize, imageSize, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < imageSize; y++) {
            for (int x = 0; x < imageSize; x++) {
                image.setRGB(x, y, WHITE);
            }
        }

        for (int y = 0; y < qrSize; y++) {
            for (int x = 0; x < qrSize; x++) {
                if (!qrCode.isDark(x, y)) {
                    continue;
                }
                int left = offset + (x + quietZoneModules) * modulePixels;
                int top = offset + (y + quietZoneModules) * modulePixels;
                for (int dy = 0; dy < modulePixels; dy++) {
                    for (int dx = 0; dx < modulePixels; dx++) {
                        image.setRGB(left + dx, top + dy, BLACK);
                    }
                }
            }
        }

        return image;
    }

    @Nonnull
    public static byte[] toPngBytes(@Nonnull QrCode qrCode, int modulePixels, int quietZoneModules) throws IOException {
        BufferedImage image = render(qrCode, modulePixels, quietZoneModules);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writePng(image, out);
            return out.toByteArray();
        }
    }

    @Nonnull
    public static byte[] encodeTextToPngBytes(
            @Nonnull String text,
            @Nonnull QrCode.ErrorCorrectionLevel errorCorrectionLevel,
            int modulePixels,
            int quietZoneModules
    ) throws IOException {
        if (text == null) {
            throw new NullPointerException("text");
        }
        if (errorCorrectionLevel == null) {
            throw new NullPointerException("errorCorrectionLevel");
        }
        QrCode qrCode = QrCode.encodeText(text, errorCorrectionLevel);
        return toPngBytes(qrCode, modulePixels, quietZoneModules);
    }

    public static void writeTextPng(
            @Nonnull String text,
            @Nonnull QrCode.ErrorCorrectionLevel errorCorrectionLevel,
            int modulePixels,
            int quietZoneModules,
            @Nonnull Path path
    ) throws IOException {
        if (path == null) {
            throw new NullPointerException("path");
        }
        QrCode qrCode = QrCode.encodeText(text, errorCorrectionLevel);
        writePng(qrCode, modulePixels, quietZoneModules, path);
    }

    public static void writePng(@Nonnull QrCode qrCode, int modulePixels, int quietZoneModules, @Nonnull Path path) throws IOException {
        if (path == null) {
            throw new NullPointerException("path");
        }
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        BufferedImage image = render(qrCode, modulePixels, quietZoneModules);
        try (OutputStream out = Files.newOutputStream(path)) {
            writePng(image, out);
        }
    }

    private static void writePng(@Nonnull BufferedImage image, @Nonnull OutputStream out) throws IOException {
        boolean written = ImageIO.write(image, "PNG", out);
        if (!written) {
            throw new IOException("No PNG ImageIO writer available.");
        }
    }

    private static int normalizeTextureSize(int rawSize) {
        int safeSize = Math.max(rawSize, MIN_TEXTURE_SIZE);
        int rounded = safeSize;
        int remainder = rounded % TEXTURE_SIZE_MULTIPLE;
        if (remainder != 0) {
            rounded += TEXTURE_SIZE_MULTIPLE - remainder;
        }

        int powerOfTwo = MIN_TEXTURE_SIZE;
        while (powerOfTwo < rounded) {
            powerOfTwo <<= 1;
        }
        return powerOfTwo;
    }
}
