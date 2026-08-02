package com.delfino.expensetracker.service;

import com.delfino.expensetracker.dto.ocr.OcrRequest;
import com.delfino.expensetracker.dto.ocr.ParsedItemDto;
import com.delfino.expensetracker.dto.ocr.ParsedReceiptDto;
import com.delfino.expensetracker.dto.ocr.ParsedStoreDto;
import com.delfino.expensetracker.model.Expense;
import com.delfino.expensetracker.model.ExpenseItem;
import com.delfino.expensetracker.model.ExpenseStatus;
import com.delfino.expensetracker.model.Store;
import com.delfino.expensetracker.model.User;
import com.delfino.expensetracker.repository.UserRepository;
import com.delfino.expensetracker.repository.ExpenseItemRepository;
import com.delfino.expensetracker.repository.ExpenseRepository;
import com.delfino.expensetracker.repository.StoreRepository;
import com.delfino.expensetracker.util.JsonUtils;
import com.delfino.expensetracker.util.MediaUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);
    private static final int MAX_TOOL_RETRIES = 3;
    private static final Set<String> IMAGE_MEDIA_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp");

    private final ExpenseRepository expenseRepository;
    private final ExpenseItemRepository expenseItemRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final CurrencyService currencyService;
    private final GeocodingService geocodingService;
    private final ObjectMapper objectMapper;
    private final OcrModelResolver ocrModelResolver;
    private final Map<String, Object> toolSchema;
    private final MeterRegistry meterRegistry;
    private final AiUsageService aiUsageService;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public OcrService(ExpenseRepository expenseRepository, ExpenseItemRepository expenseItemRepository,
                      StoreRepository storeRepository, UserRepository userRepository,
                      CurrencyService currencyService, GeocodingService geocodingService,
                      ObjectMapper objectMapper, OcrModelResolver ocrModelResolver,
                      @Qualifier("ocrToolSchema") Map<String, Object> toolSchema,
                      MeterRegistry meterRegistry,
                      AiUsageService aiUsageService) {
        this.expenseRepository = expenseRepository;
        this.expenseItemRepository = expenseItemRepository;
        this.storeRepository = storeRepository;
        this.userRepository = userRepository;
        this.currencyService = currencyService;
        this.geocodingService = geocodingService;
        this.objectMapper = objectMapper;
        this.ocrModelResolver = ocrModelResolver;
        this.toolSchema = toolSchema;
        this.meterRegistry = meterRegistry;
        this.aiUsageService = aiUsageService;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Request body builders
    // ─────────────────────────────────────────────────────────────────────

    public OcrRequest buildRequestBody(OcrModelResolver.ResolvedOcrModel resolvedOcrModel,
                                       String imagePath, Long expenseId) throws IOException {
        log.info("Processing receipt for expense {}: reading image from {}", expenseId, imagePath);
        byte[] imageBytes = Files.readAllBytes(Path.of(imagePath));
        String mediaType = MediaUtils.detectMediaType(imageBytes);

        Map<String, Object> requestBody;
        if ("application/pdf".equals(mediaType)) {
            String pdfText = MediaUtils.extractPdfText(imageBytes);
            if (MediaUtils.hasUsableText(pdfText)) {
                log.info("PDF for expense {} contains extractable text ({} chars) — using text-only LLM path",
                        expenseId, pdfText.length());
                requestBody = resolvedOcrModel.ocrProvider().buildTextRequestBody(
                        resolvedOcrModel.modelId(),
                        resolvedOcrModel.prompt(),
                        pdfText,
                        toolSchema,
                        resolvedOcrModel.useTools(),
                        resolvedOcrModel.disableThinking());
                return new OcrRequest(imageBytes, "application/pdf", requestBody);
            }
            log.info("PDF for expense {} appears image-based — falling back to vision path", expenseId);
            List<byte[]> imgs = MediaUtils.convertPdfToImages(imageBytes);
            if (imgs.isEmpty()) throw new IOException("PDF contained no renderable pages");
            imageBytes = imgs.get(0);
            mediaType = "image/jpeg";
            requestBody = resolvedOcrModel.ocrProvider().buildVisionRequestBody(
                    resolvedOcrModel.modelId(),
                    resolvedOcrModel.prompt(),
                    imgs,
                    mediaType,
                    toolSchema,
                    resolvedOcrModel.useTools(),
                    resolvedOcrModel.disableThinking());
        } else {
            requestBody = resolvedOcrModel.ocrProvider().buildVisionRequestBody(
                    resolvedOcrModel.modelId(),
                    resolvedOcrModel.prompt(),
                    Collections.singletonList(imageBytes),
                    mediaType,
                    toolSchema,
                    resolvedOcrModel.useTools(),
                    resolvedOcrModel.disableThinking());
        }
        return new OcrRequest(imageBytes, mediaType, requestBody);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Receipt processing — unified tool-calling loop
    // ─────────────────────────────────────────────────────────────────────

    @Async
    public void processReceipt(Long expenseId, String imagePath) {
        processReceiptSync(expenseId, imagePath);
    }

    public void processReceiptSync(Long expenseId, String imagePath) {
        try {
            User user = expenseRepository.findById(expenseId)
                    .flatMap(expense -> userRepository.findById(expense.getUserId()))
                    .orElseThrow(() -> new IllegalStateException("User not found for OCR expense " + expenseId));
            OcrModelResolver.ResolvedOcrModel resolvedOcrModel = ocrModelResolver.resolveForUser(user);
            OcrRequest ocrRequest = buildRequestBody(resolvedOcrModel, imagePath, expenseId);

            String currentResponseBody = callOcrApi(objectMapper.writeValueAsString(ocrRequest.requestBody()), expenseId, resolvedOcrModel);
            if (currentResponseBody == null) return;

            currentResponseBody = validateAndRetryToolCall(currentResponseBody, ocrRequest, expenseId, resolvedOcrModel);
            if (currentResponseBody == null) return;

            processOcrResponse(expenseId, currentResponseBody, resolvedOcrModel);
            aiUsageService.consume(user.getId(), com.delfino.expensetracker.model.AiUsageType.OCR);
            log.info("Successfully processed receipt for expense {}", expenseId);

        } catch (Exception e) { //must handle ANY error that can possibly occur (DB or AI-provider related)
            log.error("Failed to process receipt for expense {}", expenseId, e);
            markFailed(expenseId, e);
        }
    }

    @SuppressWarnings("unchecked")
    private String validateAndRetryToolCall(String currentResponseBody, OcrRequest ocrRequest,
                                            Long expenseId,
                                            OcrModelResolver.ResolvedOcrModel resolvedOcrModel) throws IOException, InterruptedException {
        List<Map<String, Object>> messages = new ArrayList<>(
                (List<Map<String, Object>>) ocrRequest.requestBody().get("messages"));

        for (int attempt = 0; attempt <= MAX_TOOL_RETRIES; attempt++) {
            JsonNode root = objectMapper.readTree(currentResponseBody);
            JsonNode assistantMsg = resolvedOcrModel.ocrProvider().extractAssistantMessage(root);
            ParsedReceiptDto args = resolvedOcrModel.ocrProvider().extractToolCallArgs(assistantMsg);
            boolean usedToolCall = args != null;

            if (!usedToolCall) {
                args = JsonUtils.extractJsonFromContent(assistantMsg, objectMapper);
                if (args == null) break;
            }

            String validationError = validateAmountFormula(args);

            if (validationError == null || attempt == MAX_TOOL_RETRIES) {
                if (validationError != null) {
                    log.warn("Amount validation still failing for expense {} after {} retries: {}",
                            expenseId, MAX_TOOL_RETRIES, validationError);
                }
                break;
            }

            log.warn("Amount validation failed for expense {} (via {}): {}. Sending correction (attempt {}/{})",
                    expenseId, usedToolCall ? "tool_call" : "content", validationError, attempt + 1, MAX_TOOL_RETRIES);

            messages.add(objectMapper.convertValue(assistantMsg, Map.class));

            if (usedToolCall) {
                Map<String, Object> toolResultMsg = new LinkedHashMap<>();
                toolResultMsg.put("role", "tool");
                toolResultMsg.put("content", "Validation failed: " + validationError +
                        ". Please recalculate so that amount equals the exact sum of" +
                        " (quantity * unitPrice + adjustment) for every item, then call submit_receipt again.");
                if (resolvedOcrModel.ocrProvider().requiresToolCallId()) {
                    toolResultMsg.put("tool_call_id", resolvedOcrModel.ocrProvider().extractToolCallId(assistantMsg));
                }
                messages.add(toolResultMsg);
            } else {
                Map<String, Object> correctionMsg = new LinkedHashMap<>();
                correctionMsg.put("role", "user");
                correctionMsg.put("content", "Validation failed: " + validationError +
                        ". Please fix it so that 'amount' equals the exact sum of" +
                        " (quantity * unitPrice + adjustment) for every item." +
                        " Call submit_receipt with the corrected values.");
                messages.add(correctionMsg);
            }

            Map<String, Object> correctionBody = new LinkedHashMap<>(ocrRequest.requestBody());
            correctionBody.put("messages", messages);
            String nextResponse = callOcrApi(objectMapper.writeValueAsString(correctionBody), expenseId, resolvedOcrModel);
            if (nextResponse == null) return null;
            currentResponseBody = nextResponse;
        }
        return currentResponseBody;
    }

    private String validateAmountFormula(ParsedReceiptDto parsed) {
        if (parsed.amount() == null || parsed.items() == null || parsed.items().isEmpty()) return null;
        BigDecimal amount = parsed.amount();
        BigDecimal sum = BigDecimal.ZERO;
        for (ParsedItemDto item : parsed.items()) {
            BigDecimal qty        = item.quantity()   != null ? item.quantity()   : BigDecimal.ONE;
            BigDecimal unitPrice  = item.unitPrice()  != null ? item.unitPrice()  : BigDecimal.ZERO;
            BigDecimal adjustment = item.adjustment() != null ? item.adjustment() : BigDecimal.ZERO;
            sum = sum.add(qty.multiply(unitPrice).add(adjustment));
        }
        if (amount.subtract(sum).abs().compareTo(new BigDecimal("0.01")) > 0) {
            return "correct amount=" + amount.toPlainString() + " but sum of items=" + sum.toPlainString();
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────
    // HTTP & response processing
    // ─────────────────────────────────────────────────────────────────────

    private String callOcrApi(String jsonBody, Long expenseId,
                              OcrModelResolver.ResolvedOcrModel resolvedOcrModel) throws IOException, InterruptedException {
        String provider = resolvedOcrModel.provider().name().toLowerCase();
        String modelId = resolvedOcrModel.modelId();
        Timer.Sample sample = Timer.start(meterRegistry);
        log.info("Calling OCR API for expense {}: POST {} provider={} model={}", expenseId, resolvedOcrModel.apiUrl(), provider, modelId);
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(resolvedOcrModel.apiUrl()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
        if (resolvedOcrModel.apiKey() != null && !resolvedOcrModel.apiKey().isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + resolvedOcrModel.apiKey());
        }
        HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        String responseBody = response.body();
        log.info("OCR API response: status={}, body={}", response.statusCode(), responseBody);
        String outcome = response.statusCode() == 200 ? "success" : "error";
        meterRegistry.counter("app.ai.ocr.calls", "provider", provider, "model", modelId, "outcome", outcome).increment();
        sample.stop(Timer.builder("app.ai.ocr.latency")
                .tag("provider", provider)
                .tag("model", modelId)
                .tag("outcome", outcome)
                .register(meterRegistry));
        if (response.statusCode() != 200) {
            log.error("OCR API returned non-200 status {} for expense {}. Body: {}", response.statusCode(), expenseId, responseBody);
            markFailed(expenseId, new Exception(responseBody));
            return null;
        }
        return responseBody;
    }

    private void processOcrResponse(Long expenseId, String responseBody,
                                    OcrModelResolver.ResolvedOcrModel resolvedOcrModel) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode assistantMsg = resolvedOcrModel.ocrProvider().extractAssistantMessage(root);

        ParsedReceiptDto dto = resolvedOcrModel.ocrProvider().extractToolCallArgs(assistantMsg);
        if (dto != null) {
            log.info("OCR parsed receipt for expense {} via tool_call: amount={}, currency={}, items={}",
                    expenseId,
                    dto.amount() != null ? dto.amount().toPlainString() : "N/A",
                    dto.currency() != null ? dto.currency() : "N/A",
                    dto.items() != null ? dto.items().size() : 0);
            saveExpenseFromParsed(expenseId, dto);
            return;
        }

        // Fallback: parse content as JSON
        String responseText = assistantMsg.path("content").asText(null);
        if (responseText == null || responseText.isBlank()) {
            responseText = root.has("response") ? root.get("response").asText() : null;
        }
        if (responseText == null || responseText.isBlank()) {
            throw new IllegalStateException("OCR response missing both tool_calls and content: " + responseBody);
        }

        responseText = JsonUtils.stripMarkdownFences(responseText);
        dto = objectMapper.readValue(responseText, ParsedReceiptDto.class);
        log.info("OCR parsed receipt for expense {}: amount={}, currency={}, items={}",
                expenseId,
                dto.amount() != null ? dto.amount().toPlainString() : "N/A",
                dto.currency() != null ? dto.currency() : "N/A",
                dto.items() != null ? dto.items().size() : 0);

        saveExpenseFromParsed(expenseId, dto);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Persistence
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Truncates a string to the given max length. Returns the truncated value,
     * or the original if it is within the limit.
     */
    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }

    private void saveExpenseFromParsed(Long expenseId, ParsedReceiptDto parsed) {
        Expense expense = expenseRepository.findById(expenseId).orElse(null);
        if (expense == null) return;

        List<String> truncatedFields = new ArrayList<>();

        if (parsed.transactionDatetime() != null) {
            try {
                expense.setTransactionDatetime(LocalDateTime.parse(parsed.transactionDatetime(),
                        DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            } catch (Exception ignored) {}
        }
        if (parsed.amount() != null) expense.setAmount(parsed.amount());
        if (parsed.currency() != null) expense.setCurrency(parsed.currency());
        if (parsed.receiptNumber() != null) {
            String raw = parsed.receiptNumber();
            String trimmed = truncate(raw, 50);
            if (trimmed.length() < raw.length()) truncatedFields.add("receiptNumber");
            expense.setReceiptNumber(trimmed);
        }
        if (parsed.category() != null) {
            String raw = parsed.category();
            String trimmed = truncate(raw, 50);
            if (trimmed.length() < raw.length()) truncatedFields.add("category");
            expense.setCategory(trimmed);
        }

        User user = userRepository.findById(expense.getUserId()).orElse(null);
        if (!StringUtils.hasText(user.getBaseCurrency())) {
            user.setBaseCurrency(expense.getCurrency());
            userRepository.save(user);
            log.info("Set base currency for the first time for user {} to {}", user.getId(), user.getBaseCurrency());
        }
        if (expense.getCurrency() != null && user.getBaseCurrency() != null && expense.getAmount() != null) {
            BigDecimal rate = currencyService.getRate(expense.getCurrency(), user.getBaseCurrency(),
                    expense.getTransactionDatetime() != null ? expense.getTransactionDatetime().toLocalDate() : LocalDate.now());
            if (rate != null) {
                expense.setExchangeRate(rate);
                expense.setAmountInBase(expense.getAmount().multiply(rate));
            }
        }

        if (!truncatedFields.isEmpty()) {
            String truncationNote = "Value of field " + String.join(", ", truncatedFields)
                    + " has been truncated, refer to the scanned receipt.";
            String existingNotes = expense.getNotes();
            String combined = (existingNotes != null && !existingNotes.isBlank())
                    ? existingNotes + " | " + truncationNote
                    : truncationNote;
            expense.setNotes(truncate(combined, 500));
            log.warn("Truncated OCR fields for expense {}: {}", expenseId, truncatedFields);
        }
        expense.setStatus(ExpenseStatus.COMPLETED);
        expense.setUpdatedAt(LocalDateTime.now());
        expenseRepository.save(expense);

        List<ExpenseItem> existingItems = expenseItemRepository.findByExpenseIdAndDeletedFalse(expenseId);
        if (!existingItems.isEmpty()) {
            existingItems.forEach(i -> i.setDeleted(true));
            expenseItemRepository.saveAll(existingItems);
            log.info("Deleted {} existing items for expense {} before re-processing", existingItems.size(), expenseId);
        }

        if (parsed.items() != null && !parsed.items().isEmpty()) {
            List<ExpenseItem> items = new ArrayList<>();
            boolean itemNameTruncated = false;
            for (ParsedItemDto itemDto : parsed.items()) {
                ExpenseItem item = new ExpenseItem();
                item.setExpenseId(expenseId);
                String rawItemName = itemDto.itemName() != null ? itemDto.itemName() : "";
                if (rawItemName.length() > 100) { itemNameTruncated = true; rawItemName = truncate(rawItemName, 100); }
                item.setItemName(rawItemName);
                item.setQuantity(itemDto.quantity() != null ? itemDto.quantity() : BigDecimal.ONE);
                item.setUnitPrice(itemDto.unitPrice() != null ? itemDto.unitPrice() : BigDecimal.ZERO);
                if (itemDto.adjustment() != null) item.setAdjustment(itemDto.adjustment());
                item.setDeleted(false);
                items.add(item);
            }
            if (itemNameTruncated) {
                truncatedFields.add("item.itemName");
                String truncationNote = "Value of field item.itemName has been truncated, refer to the scanned receipt.";
                String existingNotes = expense.getNotes();
                String combined = (existingNotes != null && !existingNotes.isBlank())
                        ? existingNotes + " | " + truncationNote
                        : truncationNote;
                expense.setNotes(truncate(combined, 500));
                expenseRepository.save(expense);
                log.warn("Truncated OCR item.itemName fields for expense {}", expenseId);
            }
            expenseItemRepository.saveAll(items);
        }

        if (parsed.store() != null) {
            ParsedStoreDto sn = parsed.store();
            String sName = sn.name();
            String sAddress = sn.address();
            String sCity = sn.city();
            String sCountry = sn.country();
            String sPostal = sn.postalCode();

            List<String> storeTruncated = new ArrayList<>();
            if (sName != null && sName.length() > 100) { storeTruncated.add("store.name"); sName = truncate(sName, 100); }
            if (sAddress != null && sAddress.length() > 200) { storeTruncated.add("store.address"); sAddress = truncate(sAddress, 200); }
            if (sCity != null && sCity.length() > 100) { storeTruncated.add("store.city"); sCity = truncate(sCity, 100); }
            if (sCountry != null && sCountry.length() > 2) { storeTruncated.add("store.country"); sCountry = truncate(sCountry, 2); }
            if (sPostal != null && sPostal.length() > 20) { storeTruncated.add("store.postalCode"); sPostal = truncate(sPostal, 20); }

            if (!storeTruncated.isEmpty()) {
                truncatedFields.addAll(storeTruncated);
                String truncationNote = "Value of field " + String.join(", ", storeTruncated)
                        + " has been truncated, refer to the scanned receipt.";
                String existingNotes = expense.getNotes();
                String combined = (existingNotes != null && !existingNotes.isBlank())
                        ? existingNotes + " | " + truncationNote
                        : truncationNote;
                expense.setNotes(truncate(combined, 500));
                expenseRepository.save(expense);
                log.warn("Truncated OCR store fields for expense {}: {}", expenseId, storeTruncated);
            }

            Optional<Store> existingStore = storeRepository.findMatchingStore(
                    expense.getUserId(), sName, sAddress, sCity, sCountry, sPostal);

            if (existingStore.isPresent()) {
                expense.setStoreId(existingStore.get().getId());
                expenseRepository.save(expense);
                log.info("Reused existing store {} for expense {}", existingStore.get().getId(), expenseId);
            } else {
                Store store = new Store();
                store.setUserId(expense.getUserId());
                store.setName(sName);
                store.setAddress(sAddress);
                store.setCity(sCity);
                store.setCountry(sCountry);
                store.setPostalCode(sPostal);
                store.setPhoneNumber(sn.phoneNumber());
                store.setWebsite(sn.website());
                storeRepository.save(store);
                expense.setStoreId(store.getId());
                expenseRepository.save(expense);
                geocodingService.geocodeStoreAsync(store);
            }
        }
    }

    private void markFailed(Long expenseId, Exception e) {
        expenseRepository.findById(expenseId).ifPresent(expense -> {
            expense.setStatus(ExpenseStatus.FAILED);
            expense.setUpdatedAt(LocalDateTime.now());
            expense.setNotes(e.toString());
            expenseRepository.save(expense);
        });
    }
}

