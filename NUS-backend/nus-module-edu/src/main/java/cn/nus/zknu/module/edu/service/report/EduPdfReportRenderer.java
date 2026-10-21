package cn.nus.zknu.module.edu.service.report;

import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.FontMappers;
import org.apache.pdfbox.pdmodel.font.FontMapping;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.STATISTICS_REPORT_FONT_NOT_FOUND;

@Component
public class EduPdfReportRenderer {

    private static final PDRectangle PAGE_SIZE = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
    private static final float MARGIN = 32F;
    private static final float TABLE_WIDTH = PAGE_SIZE.getWidth() - MARGIN * 2;
    private static final float ROW_HEIGHT = 21F;
    private static final float FONT_SIZE = 8F;
    private static final String[] FONT_NAMES = {"Noto Sans CJK SC", "Source Han Sans SC", "Hiragino Sans GB", "STHeiti"};
    private static final String[] FONT_FILES = {
            "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.otf",
            "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttf"
    };

    public byte[] render(TeachingReportData data) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDFont font = loadChineseFont(document);
            PageCursor cursor = addFirstPage(document, font, data);
            float[] widths = columnWidths(data.columnWeights(), data.headers().size());
            drawHeader(cursor.stream(), font, data.headers(), widths, cursor.y());
            cursor = cursor.moveDown(ROW_HEIGHT);

            for (List<String> row : data.rows()) {
                if (cursor.y() - ROW_HEIGHT < MARGIN + 18F) {
                    cursor.close();
                    cursor = addPage(document);
                    drawHeader(cursor.stream(), font, data.headers(), widths, cursor.y());
                    cursor = cursor.moveDown(ROW_HEIGHT);
                }
                drawRow(cursor.stream(), font, row, widths, cursor.y());
                cursor = cursor.moveDown(ROW_HEIGHT);
            }
            cursor.close();
            addPageNumbers(document, font);
            document.save(output);
            return output.toByteArray();
        }
    }

    private PageCursor addFirstPage(PDDocument document, PDFont font, TeachingReportData data) throws IOException {
        PageCursor cursor = addPage(document);
        float y = cursor.y();
        drawText(cursor.stream(), font, 16F, MARGIN, y, data.title());
        y -= 28F;
        y = drawPairs(cursor.stream(), font, y, "筛选：", data.filters());
        y = drawPairs(cursor.stream(), font, y, "摘要：", data.summary());
        return new PageCursor(cursor.stream(), y - 8F);
    }

    private PageCursor addPage(PDDocument document) throws IOException {
        PDPage page = new PDPage(PAGE_SIZE);
        document.addPage(page);
        return new PageCursor(new PDPageContentStream(document, page), PAGE_SIZE.getHeight() - MARGIN);
    }

    private float drawPairs(PDPageContentStream stream, PDFont font, float y, String prefix,
                            Map<String, String> pairs) throws IOException {
        if (pairs.isEmpty()) {
            return y;
        }
        StringBuilder line = new StringBuilder(prefix);
        pairs.forEach((key, value) -> line.append(key).append('=').append(value).append("  "));
        drawText(stream, font, 9F, MARGIN, y, fit(font, line.toString(), TABLE_WIDTH, 9F));
        return y - 18F;
    }

    private void drawHeader(PDPageContentStream stream, PDFont font, List<String> headers,
                            float[] widths, float y) throws IOException {
        stream.setNonStrokingColor(230F / 255F, 233F / 255F, 237F / 255F);
        stream.addRect(MARGIN, y - ROW_HEIGHT, TABLE_WIDTH, ROW_HEIGHT);
        stream.fill();
        drawCells(stream, font, headers, widths, y, true);
    }

    private void drawRow(PDPageContentStream stream, PDFont font, List<String> row,
                         float[] widths, float y) throws IOException {
        drawCells(stream, font, row, widths, y, false);
    }

    private void drawCells(PDPageContentStream stream, PDFont font, List<String> values,
                           float[] widths, float y, boolean boldLike) throws IOException {
        float x = MARGIN;
        stream.setStrokingColor(160F / 255F);
        for (int i = 0; i < widths.length; i++) {
            stream.addRect(x, y - ROW_HEIGHT, widths[i], ROW_HEIGHT);
            stream.stroke();
            String value = i < values.size() ? values.get(i) : "";
            drawText(stream, font, boldLike ? 8.5F : FONT_SIZE, x + 3F, y - 13.5F,
                    fit(font, value == null ? "" : value, widths[i] - 6F, boldLike ? 8.5F : FONT_SIZE));
            x += widths[i];
        }
    }

    private void addPageNumbers(PDDocument document, PDFont font) throws IOException {
        int total = document.getNumberOfPages();
        for (int i = 0; i < total; i++) {
            try (PDPageContentStream stream = new PDPageContentStream(document, document.getPage(i),
                    PDPageContentStream.AppendMode.APPEND, true, true)) {
                String page = "第 " + (i + 1) + " / " + total + " 页";
                float width = font.getStringWidth(page) / 1000F * 8F;
                drawText(stream, font, 8F, (PAGE_SIZE.getWidth() - width) / 2F, 18F, page);
            }
        }
    }

    private PDFont loadChineseFont(PDDocument document) throws IOException {
        String configured = System.getenv("NUS_REPORT_FONT");
        if (configured != null && !configured.isBlank()) {
            File file = new File(configured);
            if (file.isFile()) {
                return PDType0Font.load(document, file);
            }
        }
        for (String path : FONT_FILES) {
            File file = new File(path);
            if (file.isFile()) {
                return PDType0Font.load(document, file);
            }
        }
        for (String name : FONT_NAMES) {
            try {
                FontMapping<TrueTypeFont> mapping = FontMappers.instance().getTrueTypeFont(name, null);
                TrueTypeFont mapped = mapping.getFont();
                if (!mapping.isFallback() && mapped != null
                        && mapped.getUnicodeCmapLookup().getGlyphId('课') != 0) {
                    return PDType0Font.load(document, mapped, true);
                }
            } catch (RuntimeException | IOException ignored) {
                // Try the next known CJK font.
            }
        }
        throw exception(STATISTICS_REPORT_FONT_NOT_FOUND);
    }

    private float[] columnWidths(List<Float> weights, int count) {
        float totalWeight = 0F;
        for (int i = 0; i < count; i++) {
            totalWeight += i < weights.size() ? weights.get(i) : 1F;
        }
        float[] result = new float[count];
        for (int i = 0; i < count; i++) {
            float weight = i < weights.size() ? weights.get(i) : 1F;
            result[i] = TABLE_WIDTH * weight / totalWeight;
        }
        return result;
    }

    private String fit(PDFont font, String value, float maxWidth, float fontSize) throws IOException {
        if (font.getStringWidth(value) / 1000F * fontSize <= maxWidth) {
            return value;
        }
        String suffix = "...";
        int end = value.length();
        while (end > 0 && font.getStringWidth(value.substring(0, end) + suffix) / 1000F * fontSize > maxWidth) {
            end--;
        }
        return value.substring(0, end) + suffix;
    }

    private void drawText(PDPageContentStream stream, PDFont font, float size,
                          float x, float y, String value) throws IOException {
        stream.setNonStrokingColor(0F);
        stream.beginText();
        stream.setFont(font, size);
        stream.newLineAtOffset(x, y);
        stream.showText(value == null ? "" : value);
        stream.endText();
    }

    private record PageCursor(PDPageContentStream stream, float y) {
        PageCursor moveDown(float distance) {
            return new PageCursor(stream, y - distance);
        }

        void close() throws IOException {
            stream.close();
        }
    }
}
