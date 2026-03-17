package io.tebex.hytale.plugin.qr;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * A compact QR Code encoder implemented directly in Java with no external dependencies.
 * Supports byte-mode payloads and automatic version/mask selection.
 */
public final class QrCode {

    public enum ErrorCorrectionLevel {
        LOW(1),
        MEDIUM(0),
        QUARTILE(3),
        HIGH(2);

        private final int formatBits;

        ErrorCorrectionLevel(int formatBits) {
            this.formatBits = formatBits;
        }
    }

    private static final int MIN_VERSION = 1;
    private static final int MAX_VERSION = 40;

    private static final int[][] ECC_CODEWORDS_PER_BLOCK = {
            {-1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30},
            {-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28},
            {-1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30},
            {-1, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30}
    };

    private static final int[][] NUM_ERROR_CORRECTION_BLOCKS = {
            {-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8, 8, 9, 9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25},
            {-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49},
            {-1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8, 8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68},
            {-1, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81}
    };

    private final ErrorCorrectionLevel errorCorrectionLevel;
    private final int version;
    private final int size;
    private final int mask;
    private final boolean[][] modules;
    private final boolean[][] isFunction;

    @Nonnull
    public static QrCode encodeText(@Nonnull String text) {
        return encodeText(text, ErrorCorrectionLevel.MEDIUM);
    }

    @Nonnull
    public static QrCode encodeText(@Nonnull String text, @Nonnull ErrorCorrectionLevel errorCorrectionLevel) {
        if (text == null) {
            throw new NullPointerException("text");
        }
        if (errorCorrectionLevel == null) {
            throw new NullPointerException("errorCorrectionLevel");
        }
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        return encodeBytes(payload, errorCorrectionLevel);
    }

    @Nonnull
    public static QrCode encodeBytes(@Nonnull byte[] data, @Nonnull ErrorCorrectionLevel errorCorrectionLevel) {
        if (data == null) {
            throw new NullPointerException("data");
        }
        if (errorCorrectionLevel == null) {
            throw new NullPointerException("errorCorrectionLevel");
        }

        int version = MIN_VERSION;
        int usedBits;
        for (; ; version++) {
            if (version > MAX_VERSION) {
                throw new IllegalArgumentException("Data is too long for QR Code byte mode.");
            }
            int dataCapacityBits = getNumDataCodewords(version, errorCorrectionLevel) * 8;
            int charCountBits = (version <= 9) ? 8 : 16;
            usedBits = 4 + charCountBits + data.length * 8;
            if (usedBits <= dataCapacityBits) {
                break;
            }
        }

        int dataCapacityBits = getNumDataCodewords(version, errorCorrectionLevel) * 8;
        BitBuffer bitBuffer = new BitBuffer();
        bitBuffer.appendBits(0b0100, 4); // byte mode
        int charCountBits = (version <= 9) ? 8 : 16;
        bitBuffer.appendBits(data.length, charCountBits);
        for (byte b : data) {
            bitBuffer.appendBits(b & 0xFF, 8);
        }

        int terminatorBits = Math.min(4, dataCapacityBits - bitBuffer.bitLength());
        bitBuffer.appendBits(0, terminatorBits);
        int paddingBits = (8 - (bitBuffer.bitLength() % 8)) % 8;
        bitBuffer.appendBits(0, paddingBits);

        byte[] dataCodewords = bitBuffer.toByteArray();
        int numDataCodewords = getNumDataCodewords(version, errorCorrectionLevel);
        int existingCodewords = dataCodewords.length;
        dataCodewords = Arrays.copyOf(dataCodewords, numDataCodewords);
        for (int i = existingCodewords; i < numDataCodewords; i++) {
            dataCodewords[i] = (byte) (((i - existingCodewords) & 1) == 0 ? 0xEC : 0x11);
        }

        return new QrCode(version, errorCorrectionLevel, dataCodewords, -1);
    }

    private QrCode(int version, @Nonnull ErrorCorrectionLevel errorCorrectionLevel, @Nonnull byte[] dataCodewords, int forcedMask) {
        this.version = version;
        this.errorCorrectionLevel = errorCorrectionLevel;
        this.size = version * 4 + 17;
        this.modules = new boolean[size][size];
        this.isFunction = new boolean[size][size];

        drawFunctionPatterns();
        byte[] allCodewords = addErrorCorrectionAndInterleave(dataCodewords);
        drawCodewords(allCodewords);

        int selectedMask = forcedMask;
        if (selectedMask == -1) {
            int minPenalty = Integer.MAX_VALUE;
            for (int candidateMask = 0; candidateMask < 8; candidateMask++) {
                applyMask(candidateMask);
                drawFormatBits(candidateMask);
                int penalty = getPenaltyScore();
                if (penalty < minPenalty) {
                    selectedMask = candidateMask;
                    minPenalty = penalty;
                }
                applyMask(candidateMask);
            }
        } else if (selectedMask < 0 || selectedMask > 7) {
            throw new IllegalArgumentException("Mask value out of range");
        }

        this.mask = selectedMask;
        applyMask(this.mask);
        drawFormatBits(this.mask);
    }

    public int getVersion() {
        return version;
    }

    public int getSize() {
        return size;
    }

    public int getMask() {
        return mask;
    }

    @Nonnull
    public ErrorCorrectionLevel getErrorCorrectionLevel() {
        return errorCorrectionLevel;
    }

    public boolean isDark(int x, int y) {
        if (x < 0 || x >= size || y < 0 || y >= size) {
            throw new IllegalArgumentException("Coordinates out of bounds");
        }
        return modules[y][x];
    }

    private void drawFunctionPatterns() {
        drawFinderPattern(3, 3);
        drawFinderPattern(size - 4, 3);
        drawFinderPattern(3, size - 4);

        int[] alignmentPatternPositions = getAlignmentPatternPositions(version);
        for (int i = 0; i < alignmentPatternPositions.length; i++) {
            for (int j = 0; j < alignmentPatternPositions.length; j++) {
                if ((i == 0 && j == 0)
                        || (i == 0 && j == alignmentPatternPositions.length - 1)
                        || (i == alignmentPatternPositions.length - 1 && j == 0)) {
                    continue;
                }
                drawAlignmentPattern(alignmentPatternPositions[i], alignmentPatternPositions[j]);
            }
        }

        for (int i = 0; i < size; i++) {
            if (!isFunction[6][i]) {
                setFunctionModule(i, 6, (i & 1) == 0);
            }
            if (!isFunction[i][6]) {
                setFunctionModule(6, i, (i & 1) == 0);
            }
        }

        // Reserve the format information area using the standard placement logic.
        // The final mask-specific bits are written later after mask selection.
        drawFormatBits(0);

        if (version >= 7) {
            for (int i = 0; i < 6; i++) {
                for (int j = 0; j < 3; j++) {
                    setFunctionModule(size - 11 + j, i, false);
                    setFunctionModule(i, size - 11 + j, false);
                }
            }
            drawVersion();
        }
    }

    private void drawFinderPattern(int x, int y) {
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int xx = x + dx;
                int yy = y + dy;
                if (0 <= xx && xx < size && 0 <= yy && yy < size) {
                    int distance = Math.max(Math.abs(dx), Math.abs(dy));
                    setFunctionModule(xx, yy, distance != 2 && distance != 4);
                }
            }
        }
    }

    private void drawAlignmentPattern(int x, int y) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                setFunctionModule(x + dx, y + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
            }
        }
    }

    private void drawVersion() {
        int remainder = version;
        for (int i = 0; i < 12; i++) {
            remainder = (remainder << 1) ^ (((remainder >>> 11) & 1) * 0x1F25);
        }
        int bits = (version << 12) | remainder;
        for (int i = 0; i < 18; i++) {
            boolean bit = ((bits >>> i) & 1) != 0;
            int a = size - 11 + (i % 3);
            int b = i / 3;
            setFunctionModule(a, b, bit);
            setFunctionModule(b, a, bit);
        }
    }

    private void drawFormatBits(int mask) {
        int data = (errorCorrectionLevel.formatBits << 3) | mask;
        int remainder = data;
        for (int i = 0; i < 10; i++) {
            remainder = (remainder << 1) ^ (((remainder >>> 9) & 1) * 0x537);
        }
        int bits = ((data << 10) | remainder) ^ 0x5412;

        for (int i = 0; i <= 5; i++) {
            setFunctionModule(8, i, ((bits >>> i) & 1) != 0);
        }
        setFunctionModule(8, 7, ((bits >>> 6) & 1) != 0);
        setFunctionModule(8, 8, ((bits >>> 7) & 1) != 0);
        setFunctionModule(7, 8, ((bits >>> 8) & 1) != 0);
        for (int i = 9; i < 15; i++) {
            setFunctionModule(14 - i, 8, ((bits >>> i) & 1) != 0);
        }

        for (int i = 0; i < 8; i++) {
            setFunctionModule(size - 1 - i, 8, ((bits >>> i) & 1) != 0);
        }
        for (int i = 8; i < 15; i++) {
            setFunctionModule(8, size - 15 + i, ((bits >>> i) & 1) != 0);
        }
        setFunctionModule(8, size - 8, true);
    }

    private void drawCodewords(@Nonnull byte[] allCodewords) {
        int bitIndex = 0;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) {
                right = 5;
            }
            for (int vert = 0; vert < size; vert++) {
                int y = (((right + 1) & 2) == 0) ? (size - 1 - vert) : vert;
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    if (!isFunction[y][x] && bitIndex < allCodewords.length * 8) {
                        modules[y][x] = getBit(allCodewords, bitIndex);
                        bitIndex++;
                    }
                }
            }
        }
    }

    private void applyMask(int mask) {
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (isFunction[y][x]) {
                    continue;
                }
                boolean invert;
                switch (mask) {
                    case 0 -> invert = ((x + y) % 2) == 0;
                    case 1 -> invert = (y % 2) == 0;
                    case 2 -> invert = (x % 3) == 0;
                    case 3 -> invert = ((x + y) % 3) == 0;
                    case 4 -> invert = ((x / 3 + y / 2) % 2) == 0;
                    case 5 -> invert = ((x * y) % 2 + (x * y) % 3) == 0;
                    case 6 -> invert = (((x * y) % 2 + (x * y) % 3) % 2) == 0;
                    case 7 -> invert = ((((x + y) % 2) + ((x * y) % 3)) % 2) == 0;
                    default -> throw new IllegalArgumentException("Mask out of range");
                }
                if (invert) {
                    modules[y][x] = !modules[y][x];
                }
            }
        }
    }

    private int getPenaltyScore() {
        int result = 0;

        for (int y = 0; y < size; y++) {
            boolean runColor = modules[y][0];
            int runLength = 1;
            for (int x = 1; x < size; x++) {
                if (modules[y][x] == runColor) {
                    runLength++;
                    if (runLength == 5) {
                        result += 3;
                    } else if (runLength > 5) {
                        result++;
                    }
                } else {
                    runColor = modules[y][x];
                    runLength = 1;
                }
            }
        }

        for (int x = 0; x < size; x++) {
            boolean runColor = modules[0][x];
            int runLength = 1;
            for (int y = 1; y < size; y++) {
                if (modules[y][x] == runColor) {
                    runLength++;
                    if (runLength == 5) {
                        result += 3;
                    } else if (runLength > 5) {
                        result++;
                    }
                } else {
                    runColor = modules[y][x];
                    runLength = 1;
                }
            }
        }

        for (int y = 0; y < size - 1; y++) {
            for (int x = 0; x < size - 1; x++) {
                boolean color = modules[y][x];
                if (color == modules[y][x + 1]
                        && color == modules[y + 1][x]
                        && color == modules[y + 1][x + 1]) {
                    result += 3;
                }
            }
        }

        for (int y = 0; y < size; y++) {
            for (int x = 0; x <= size - 7; x++) {
                if (modules[y][x]
                        && !modules[y][x + 1]
                        && modules[y][x + 2]
                        && modules[y][x + 3]
                        && modules[y][x + 4]
                        && !modules[y][x + 5]
                        && modules[y][x + 6]
                        && (isWhiteHorizontal(y, x - 4, x) || isWhiteHorizontal(y, x + 7, x + 11))) {
                    result += 40;
                }
            }
        }

        for (int x = 0; x < size; x++) {
            for (int y = 0; y <= size - 7; y++) {
                if (modules[y][x]
                        && !modules[y + 1][x]
                        && modules[y + 2][x]
                        && modules[y + 3][x]
                        && modules[y + 4][x]
                        && !modules[y + 5][x]
                        && modules[y + 6][x]
                        && (isWhiteVertical(x, y - 4, y) || isWhiteVertical(x, y + 7, y + 11))) {
                    result += 40;
                }
            }
        }

        int dark = 0;
        for (boolean[] row : modules) {
            for (boolean module : row) {
                if (module) {
                    dark++;
                }
            }
        }
        int total = size * size;
        int k = Math.abs(dark * 20 - total * 10) / total;
        result += k * 10;

        return result;
    }

    private boolean isWhiteHorizontal(int y, int start, int endExclusive) {
        if (start < 0 || endExclusive > size) {
            return false;
        }
        for (int x = start; x < endExclusive; x++) {
            if (modules[y][x]) {
                return false;
            }
        }
        return true;
    }

    private boolean isWhiteVertical(int x, int start, int endExclusive) {
        if (start < 0 || endExclusive > size) {
            return false;
        }
        for (int y = start; y < endExclusive; y++) {
            if (modules[y][x]) {
                return false;
            }
        }
        return true;
    }

    private void setFunctionModule(int x, int y, boolean value) {
        modules[y][x] = value;
        isFunction[y][x] = true;
    }

    @Nonnull
    private byte[] addErrorCorrectionAndInterleave(@Nonnull byte[] dataCodewords) {
        int numBlocks = NUM_ERROR_CORRECTION_BLOCKS[errorCorrectionLevel.ordinal()][version];
        int eccCodewordsPerBlock = ECC_CODEWORDS_PER_BLOCK[errorCorrectionLevel.ordinal()][version];
        int rawCodewords = getNumRawDataModules(version) / 8;
        int numShortBlocks = numBlocks - (rawCodewords % numBlocks);
        int shortBlockDataLen = rawCodewords / numBlocks - eccCodewordsPerBlock;

        byte[][] dataBlocks = new byte[numBlocks][];
        byte[][] eccBlocks = new byte[numBlocks][];
        byte[] rsDivisor = reedSolomonComputeDivisor(eccCodewordsPerBlock);

        int dataOffset = 0;
        for (int i = 0; i < numBlocks; i++) {
            int dataLen = shortBlockDataLen + (i < numShortBlocks ? 0 : 1);
            dataBlocks[i] = Arrays.copyOfRange(dataCodewords, dataOffset, dataOffset + dataLen);
            dataOffset += dataLen;
            eccBlocks[i] = reedSolomonComputeRemainder(dataBlocks[i], rsDivisor);
        }
        if (dataOffset != dataCodewords.length) {
            throw new IllegalStateException("Unexpected data block sizing while interleaving QR codewords.");
        }

        byte[] result = new byte[rawCodewords];
        int resultOffset = 0;
        int maxDataLen = shortBlockDataLen + 1;
        for (int i = 0; i < maxDataLen; i++) {
            for (byte[] block : dataBlocks) {
                if (i < block.length) {
                    result[resultOffset++] = block[i];
                }
            }
        }
        for (int i = 0; i < eccCodewordsPerBlock; i++) {
            for (byte[] block : eccBlocks) {
                result[resultOffset++] = block[i];
            }
        }

        if (resultOffset != result.length) {
            throw new IllegalStateException("Unexpected output length while interleaving QR codewords.");
        }
        return result;
    }

    @Nonnull
    private static byte[] reedSolomonComputeDivisor(int degree) {
        if (degree < 1 || degree > 255) {
            throw new IllegalArgumentException("Degree out of range");
        }
        byte[] result = new byte[degree];
        result[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < result.length; j++) {
                result[j] = (byte) reedSolomonMultiply(result[j] & 0xFF, root);
                if (j + 1 < result.length) {
                    result[j] ^= result[j + 1];
                }
            }
            root = reedSolomonMultiply(root, 0x02);
        }
        return result;
    }

    @Nonnull
    private static byte[] reedSolomonComputeRemainder(@Nonnull byte[] data, @Nonnull byte[] divisor) {
        byte[] result = new byte[divisor.length];
        for (byte b : data) {
            int factor = (b ^ result[0]) & 0xFF;
            System.arraycopy(result, 1, result, 0, result.length - 1);
            result[result.length - 1] = 0;
            for (int i = 0; i < result.length; i++) {
                result[i] ^= (byte) reedSolomonMultiply(divisor[i] & 0xFF, factor);
            }
        }
        return result;
    }

    private static int reedSolomonMultiply(int x, int y) {
        int z = 0;
        for (; y != 0; y >>>= 1) {
            if ((y & 1) != 0) {
                z ^= x;
            }
            x <<= 1;
            if ((x & 0x100) != 0) {
                x ^= 0x11D;
            }
        }
        return z;
    }

    private static int getNumDataCodewords(int version, @Nonnull ErrorCorrectionLevel errorCorrectionLevel) {
        return getNumRawDataModules(version) / 8
                - ECC_CODEWORDS_PER_BLOCK[errorCorrectionLevel.ordinal()][version]
                * NUM_ERROR_CORRECTION_BLOCKS[errorCorrectionLevel.ordinal()][version];
    }

    private static int getNumRawDataModules(int version) {
        int result = (16 * version + 128) * version + 64;
        if (version >= 2) {
            int numAlign = version / 7 + 2;
            result -= (25 * numAlign - 10) * numAlign - 55;
            if (version >= 7) {
                result -= 36;
            }
        }
        return result;
    }

    @Nonnull
    private int[] getAlignmentPatternPositions(int version) {
        if (version == 1) {
            return new int[0];
        }
        int numAlign = version / 7 + 2;
        int step;
        if (version == 32) {
            step = 26;
        } else {
            step = ((version * 4 + numAlign * 2 + 1) / (numAlign * 2 - 2)) * 2;
        }

        int[] result = new int[numAlign];
        result[0] = 6;
        for (int i = numAlign - 1, pos = size - 7; i >= 1; i--, pos -= step) {
            result[i] = pos;
        }
        return result;
    }

    private static boolean getBit(@Nonnull byte[] data, int bitIndex) {
        return ((data[bitIndex >>> 3] >>> (7 - (bitIndex & 7))) & 1) != 0;
    }

    private static final class BitBuffer {
        private byte[] bytes = new byte[32];
        private int bitLength;

        int bitLength() {
            return bitLength;
        }

        void appendBits(int value, int length) {
            if (length < 0 || length > 31 || value < 0 || (value >>> length) != 0) {
                throw new IllegalArgumentException("Value out of range for bit append.");
            }
            ensureCapacity(bitLength + length);
            for (int i = length - 1; i >= 0; i--) {
                if (((value >>> i) & 1) != 0) {
                    bytes[bitLength >>> 3] |= (byte) (1 << (7 - (bitLength & 7)));
                }
                bitLength++;
            }
        }

        @Nonnull
        byte[] toByteArray() {
            return Arrays.copyOf(bytes, (bitLength + 7) / 8);
        }

        private void ensureCapacity(int newBitLength) {
            int neededBytes = (newBitLength + 7) / 8;
            if (neededBytes > bytes.length) {
                int newSize = Math.max(neededBytes, bytes.length * 2);
                bytes = Arrays.copyOf(bytes, newSize);
            }
        }
    }
}
