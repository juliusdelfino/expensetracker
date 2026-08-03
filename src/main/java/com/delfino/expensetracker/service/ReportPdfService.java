package com.delfino.expensetracker.service;

import com.delfino.expensetracker.dto.report.ReportAggregateSummaryResponse;
import com.delfino.expensetracker.dto.report.ReportChartResponse;
import com.delfino.expensetracker.dto.report.ReportExpenseResponse;
import com.delfino.expensetracker.dto.report.ReportResponse;
import com.delfino.expensetracker.model.User;
import com.delfino.expensetracker.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ReportPdfService {

    private static final PDRectangle PAGE_SIZE = PDRectangle.LETTER;
    private static final float MARGIN = 42f;
    private static final float CONTENT_WIDTH = PAGE_SIZE.getWidth() - (MARGIN * 2);
    private static final float CELL_PADDING = 4f;
    private static final PDFont FONT_REGULAR = PDType1Font.HELVETICA;
    private static final PDFont FONT_BOLD = PDType1Font.HELVETICA_BOLD;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String FILTER_EXPENSE_IDS = "expenseIds";

    private final UserRepository userRepository;

    public ReportPdfService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public byte[] generatePdf(Long userId, ReportResponse report) {
        String baseCurrency = userRepository.findById(userId)
                .map(User::getBaseCurrency)
                .filter(StringUtils::hasText)
                .orElse("USD");

        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(document);
            try {
                writeHeader(writer, report);
                writeFilters(writer, report.filterSnapshot());
                writeSummary(writer, report.summary(), baseCurrency);
                writeInsights(writer, report.insights());
                writeCharts(writer, report.charts(), baseCurrency);
                writeExpenses(writer, report.expenses(), baseCurrency);
            } finally {
                writer.close();
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to generate report PDF", ex);
        }
    }

    public String buildFilename(ReportResponse report) {
        String slug = slugify(report.title());
        return "report-" + report.id() + "-" + slug + ".pdf";
    }

    private void writeHeader(PdfWriter writer, ReportResponse report) throws IOException {
        writer.writeSectionTitle(report.title() != null ? report.title() : "Report");
        if (StringUtils.hasText(report.description())) {
            writer.writeParagraph(report.description(), FONT_REGULAR, 11f);
        }

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("Report ID", report.id() != null ? String.valueOf(report.id()) : "—");
        metadata.put("Group By", formatGroupBy(report.groupBy() != null ? report.groupBy().name() : null));
        metadata.put("Created", formatDateTime(report.createdAt()));
        metadata.put("Updated", formatDateTime(report.updatedAt()));
        metadata.put("Included Expenses", String.valueOf(report.expenses() != null ? report.expenses().size() : 0));
        writer.writeKeyValueSection("Report Details", metadata);
    }

    private void writeFilters(PdfWriter writer, JsonNode filterSnapshot) throws IOException {
        if (filterSnapshot == null || filterSnapshot.isNull()) {
            return;
        }

        Map<String, String> filters = new LinkedHashMap<>();
        addIfPresent(filters, "Mode", filterSnapshot.path("mode").asText(null));
        addIfPresent(filters, "Group By", formatGroupBy(filterSnapshot.path("groupBy").asText(null)));
        addIfPresent(filters, "Start Date", filterSnapshot.path("startDate").asText(null));
        addIfPresent(filters, "End Date", filterSnapshot.path("endDate").asText(null));
        addIfPresent(filters, "Category", filterSnapshot.path("category").asText(null));
        addIfPresent(filters, "Country", filterSnapshot.path("country").asText(null));
        addIfPresent(filters, "City", filterSnapshot.path("city").asText(null));
        addIfPresent(filters, "Store", filterSnapshot.path("storeName").asText(null));
        addIfPresent(filters, "Keyword", filterSnapshot.path("search").asText(null));
        if (filterSnapshot.has(FILTER_EXPENSE_IDS) && filterSnapshot.get(FILTER_EXPENSE_IDS).isArray()) {
            filters.put("Selected Expense IDs", String.valueOf(filterSnapshot.get(FILTER_EXPENSE_IDS).size()));
        }
        if (!filters.isEmpty()) {
            writer.writeKeyValueSection("Filters", filters);
        }
    }

    private void writeSummary(PdfWriter writer,
                              ReportAggregateSummaryResponse summary,
                              String baseCurrency) throws IOException {
        if (summary == null) {
            return;
        }

        Map<String, String> rows = new LinkedHashMap<>();
        rows.put("Total Spend", formatMoney(summary.totalAmount(), baseCurrency));
        rows.put("Average Expense", formatMoney(summary.averageAmount(), baseCurrency));
        rows.put("Smallest Expense", formatMoney(summary.minAmount(), baseCurrency));
        rows.put("Largest Expense", formatMoney(summary.maxAmount(), baseCurrency));
        rows.put("Expense Count", String.valueOf(summary.expenseCount()));
        rows.put("Active Days", String.valueOf(summary.activeDaysCount()));
        rows.put("Top Category", defaultValue(summary.topCategory()));
        rows.put("Top Location", defaultValue(summary.topLocation()));
        rows.put("Covered Dates", formatCoveredDates(summary.coveredStartDate(), summary.coveredEndDate()));
        writer.writeKeyValueSection("Summary", rows);
    }

    private void writeInsights(PdfWriter writer, List<String> insights) throws IOException {
        writer.writeSectionSubTitle("Insights");
        if (insights == null || insights.isEmpty()) {
            writer.writeParagraph("No insights available.", FONT_REGULAR, 10f);
            return;
        }
        for (String insight : insights) {
            writer.writeBullet(insight);
        }
    }

    private void writeCharts(PdfWriter writer, List<ReportChartResponse> charts, String baseCurrency) throws IOException {
        writer.writeSectionSubTitle("Chart Data");
        if (charts == null || charts.isEmpty()) {
            writer.writeParagraph("No charts available for this report.", FONT_REGULAR, 10f);
            return;
        }

        for (ReportChartResponse chart : charts) {
            writeChart(writer, chart, baseCurrency);
        }
    }

    private void writeChart(PdfWriter writer, ReportChartResponse chart, String baseCurrency) throws IOException {
        writer.writeParagraph((chart.title() != null ? chart.title() : "Chart") + " (" + (chart.type() != null ? chart.type() : "BAR") + ")",
                FONT_BOLD, 11f);
        List<String> labels = chart.labels() != null ? chart.labels() : List.of();
        List<BigDecimal> values = chart.values() != null ? chart.values() : List.of();
        if (labels.isEmpty() || values.isEmpty()) {
            writer.writeParagraph("No chart data available.", FONT_REGULAR, 10f);
            return;
        }

        int rows = Math.min(Math.min(labels.size(), values.size()), 8);
        for (int i = 0; i < rows; i++) {
            writer.writeBullet(labels.get(i) + ": " + formatMoney(values.get(i), baseCurrency));
        }
        if (labels.size() > rows) {
            writer.writeParagraph("+ " + (labels.size() - rows) + " more row(s) in the web report", FONT_REGULAR, 9f);
        }
        writer.addSpacing(4f);
    }

    private void writeExpenses(PdfWriter writer,
                               List<ReportExpenseResponse> expenses,
                               String baseCurrency) throws IOException {
        writer.writeSectionSubTitle("Expenses");
        if (expenses == null || expenses.isEmpty()) {
            writer.writeParagraph("No expenses are included in this report.", FONT_REGULAR, 10f);
            return;
        }

        float[] widths = new float[]{70f, 165f, 135f, 70f, 70f};
        writer.writeTableRow(List.of("Date", "Description", "Location", "Amount", "Base"), widths, true);
        for (ReportExpenseResponse expense : expenses) {
            String description = firstNonBlank(expense.category(), expense.notes(), "Expense");
            if (expense.deleted()) {
                description += " (deleted)";
            }
            writer.writeTableRow(List.of(
                    formatDate(expense.transactionDatetime()),
                    description,
                    firstNonBlank(expense.locationLabel(), expense.storeName(), "—"),
                    formatMoney(expense.amount(), expense.currency()),
                    formatMoney(expense.amountInBase(), baseCurrency)
            ), widths, false);
        }
    }

    private void addIfPresent(Map<String, String> target, String label, String value) {
        if (StringUtils.hasText(value)) {
            target.put(label, value.trim());
        }
    }

    private String defaultValue(String value) {
        return StringUtils.hasText(value) ? value.trim() : "—";
    }

    private String formatCoveredDates(String start, String end) {
        if (StringUtils.hasText(start) && StringUtils.hasText(end)) {
            return start + " → " + end;
        }
        return firstNonBlank(start, end, "—");
    }

    private String formatGroupBy(String value) {
        if (!StringUtils.hasText(value)) {
            return "—";
        }
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "CATEGORY" -> "Category";
            case "STORE_LOCATION" -> "Store Location";
            case "KEYWORD" -> "Keyword";
            default -> value;
        };
    }

    private String formatDate(LocalDateTime value) {
        return value != null ? value.format(DATE_FORMAT) : "—";
    }

    private String formatDateTime(LocalDateTime value) {
        return value != null ? value.format(DATE_TIME_FORMAT) : "—";
    }

    private String formatMoney(BigDecimal amount, String currency) {
        if (amount == null) {
            return "—";
        }
        return amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() + (StringUtils.hasText(currency) ? " " + currency : "");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String slugify(String input) {
        String normalized = normalizeText(input);
        String slug = normalized.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(?:^-+)|(?:-+$)", "");
        if (slug.isBlank()) {
            return "report";
        }
        return slug.length() > 60 ? slug.substring(0, 60) : slug;
    }

    private String normalizeText(String input) {
        if (input == null) {
            return "";
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "")
                .replace('—', '-')
                .replace('–', '-')
                .replace('“', '"')
                .replace('”', '"')
                .replace('’', '\'')
                .replace('•', '-')
                .replace('→', '>');
        return normalized.replaceAll("[^\\x20-\\x7E\\n\\r\\t]", "?");
    }

    private final class PdfWriter implements AutoCloseable {
        private final PDDocument document;
        private PDPageContentStream contentStream;
        private float cursorY;

        private PdfWriter(PDDocument document) throws IOException {
            this.document = document;
            newPage();
        }

        private void newPage() throws IOException {
            if (contentStream != null) {
                contentStream.close();
            }
            PDPage page = new PDPage(PAGE_SIZE);
            document.addPage(page);
            contentStream = new PDPageContentStream(document, page);
            cursorY = PAGE_SIZE.getHeight() - MARGIN;
        }

        private void ensureSpace(float height) throws IOException {
            if (cursorY - height < MARGIN) {
                newPage();
            }
        }

        private void writeSectionTitle(String text) throws IOException {
            writeWrappedText(text, FONT_BOLD, 18f, new Color(35, 57, 91), 22f);
            addSpacing(4f);
        }

        private void writeSectionSubTitle(String text) throws IOException {
            addSpacing(8f);
            writeWrappedText(text, FONT_BOLD, 13f, Color.DARK_GRAY, 16f);
            addSpacing(2f);
        }

        private void writeParagraph(String text, PDFont font, float fontSize) throws IOException {
            writeWrappedText(text, font, fontSize, Color.BLACK, fontSize + 3f);
        }

        private void writeBullet(String text) throws IOException {
            writeWrappedText("- " + text, FONT_REGULAR, 10f, Color.BLACK, 13f);
        }

        private void writeKeyValueSection(String title, Map<String, String> rows) throws IOException {
            writeSectionSubTitle(title);
            for (Map.Entry<String, String> row : rows.entrySet()) {
                writeWrappedText(row.getKey() + ": " + row.getValue(), FONT_REGULAR, 10f, Color.BLACK, 13f);
            }
        }

        private void writeWrappedText(String text,
                                      PDFont font,
                                      float fontSize,
                                      Color color,
                                      float lineHeight) throws IOException {
            List<String> paragraphs = splitParagraphs(text);
            for (int p = 0; p < paragraphs.size(); p++) {
                List<String> lines = wrapText(paragraphs.get(p), font, fontSize, CONTENT_WIDTH);
                for (String line : lines) {
                    ensureSpace(lineHeight);
                    contentStream.beginText();
                    contentStream.setFont(font, fontSize);
                    contentStream.setNonStrokingColor(color);
                    contentStream.newLineAtOffset(MARGIN, cursorY);
                    contentStream.showText(normalizeText(line));
                    contentStream.endText();
                    cursorY -= lineHeight;
                }
                if (p < paragraphs.size() - 1) {
                    addSpacing(2f);
                }
            }
        }

        private void writeTableRow(List<String> cells, float[] widths, boolean header) throws IOException {
            float fontSize = header ? 10f : 9.5f;
            PDFont font = header ? FONT_BOLD : FONT_REGULAR;
            float lineHeight = fontSize + 2f;

            List<List<String>> wrappedCells = new ArrayList<>();
            int maxLines = 1;
            for (int i = 0; i < cells.size(); i++) {
                float maxWidth = widths[i] - (CELL_PADDING * 2);
                List<String> lines = wrapText(cells.get(i), font, fontSize, maxWidth);
                wrappedCells.add(lines);
                maxLines = Math.max(maxLines, lines.size());
            }

            float rowHeight = (maxLines * lineHeight) + (CELL_PADDING * 2);
            ensureSpace(rowHeight + 2f);

            float x = MARGIN;
            for (int i = 0; i < cells.size(); i++) {
                if (header) {
                    contentStream.setNonStrokingColor(new Color(233, 238, 246));
                    contentStream.addRect(x, cursorY - rowHeight, widths[i], rowHeight);
                    contentStream.fill();
                }
                contentStream.setStrokingColor(new Color(180, 188, 200));
                contentStream.addRect(x, cursorY - rowHeight, widths[i], rowHeight);
                contentStream.stroke();

                float textY = cursorY - CELL_PADDING - fontSize;
                for (String line : wrappedCells.get(i)) {
                    contentStream.beginText();
                    contentStream.setFont(font, fontSize);
                    contentStream.setNonStrokingColor(Color.BLACK);
                    contentStream.newLineAtOffset(x + CELL_PADDING, textY);
                    contentStream.showText(normalizeText(line));
                    contentStream.endText();
                    textY -= lineHeight;
                }
                x += widths[i];
            }
            cursorY -= rowHeight;
        }

        private void addSpacing(float spacing) {
            cursorY -= spacing;
        }

        @Override
        public void close() throws IOException {
            if (contentStream != null) {
                contentStream.close();
                contentStream = null;
            }
        }
    }

    private List<String> splitParagraphs(String text) {
        String normalized = normalizeText(text);
        String[] parts = normalized.split("\\r?\\n");
        List<String> paragraphs = new ArrayList<>();
        for (String part : parts) {
            if (part.isBlank()) {
                paragraphs.add(" ");
            } else {
                paragraphs.add(part.trim());
            }
        }
        return paragraphs.isEmpty() ? List.of("") : paragraphs;
    }

    private List<String> wrapText(String text, PDFont font, float fontSize, float maxWidth) throws IOException {
        String normalized = normalizeText(text);
        if (normalized.isBlank()) {
            return List.of(" ");
        }

        List<String> lines = new ArrayList<>();
        String[] words = normalized.split("\\s+");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String candidate = currentLine.length() == 0 ? word : currentLine + " " + word;
            if (textWidth(candidate, font, fontSize) <= maxWidth) {
                currentLine.setLength(0);
                currentLine.append(candidate);
                continue;
            }

            if (currentLine.length() > 0) {
                lines.add(currentLine.toString());
                currentLine.setLength(0);
            }

            if (textWidth(word, font, fontSize) <= maxWidth) {
                currentLine.append(word);
                continue;
            }

            List<String> chunks = splitLongWord(word, font, fontSize, maxWidth);
            for (int i = 0; i < chunks.size() - 1; i++) {
                lines.add(chunks.get(i));
            }
            currentLine.append(chunks.get(chunks.size() - 1));
        }

        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        return lines.isEmpty() ? List.of(" ") : lines;
    }

    private List<String> splitLongWord(String word, PDFont font, float fontSize, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder chunk = new StringBuilder();
        for (char ch : word.toCharArray()) {
            String candidate = chunk + String.valueOf(ch);
            if (textWidth(candidate, font, fontSize) <= maxWidth || chunk.length() == 0) {
                chunk.setLength(0);
                chunk.append(candidate);
            } else {
                lines.add(chunk.toString());
                chunk.setLength(0);
                chunk.append(ch);
            }
        }
        if (chunk.length() > 0) {
            lines.add(chunk.toString());
        }
        return lines;
    }

    private float textWidth(String text, PDFont font, float fontSize) throws IOException {
        return font.getStringWidth(normalizeText(text)) / 1000f * fontSize;
    }
}



