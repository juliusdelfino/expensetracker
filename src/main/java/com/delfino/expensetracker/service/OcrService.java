package com.delfino.expensetracker.service;

import com.delfino.expensetracker.dto.ocr.OcrRequest;
import com.delfino.expensetracker.model.Expense;
import com.delfino.expensetracker.model.ExpenseItem;
import com.delfino.expensetracker.model.ExpenseStatus;
import com.delfino.expensetracker.model.Store;
import com.delfino.expensetracker.model.User;
import com.delfino.expensetracker.repository.UserRepository;
import com.delfino.expensetracker.repository.ExpenseItemRepository;
import com.delfino.expensetracker.repository.ExpenseRepository;
import com.delfino.expensetracker.repository.StoreRepository;
import com.delfino.expensetracker.service.ocr.OcrProvider;
import com.delfino.expensetracker.util.JsonUtils;
import com.delfino.expensetracker.util.MediaUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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
    private final OcrProvider ocrProvider;
    private final Map<String, Object> toolSchema;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${ocr.api.url:http://localhost:11434/api/chat}")
    private String ocrApiUrl;

    @Value("${ocr.api.model:llava}")
    private String ocrModel;

    @Value("${ocr.api.api-key:}")
    private String ocrApiKey;

    @Value("${ocr.api.disable-thinking:false}")
    private boolean ocrDisableThinking;

    @Value("${ocr.api.use-tools:true}")
    private boolean ocrUseTools;

    @Value("${ocr.api.prompt:Parse this receipt image and return a JSON object. Return ONLY valid JSON.}")
    private String ocrPrompt;

    public OcrService(ExpenseRepository expenseRepository, ExpenseItemRepository expenseItemRepository,
                      StoreRepository storeRepository, UserRepository userRepository,
                      CurrencyService currencyService, GeocodingService geocodingService,
                      ObjectMapper objectMapper, OcrProvider ocrProvider,
                      @Qualifier("ocrToolSchema") Map<String, Object> toolSchema) {
        this.expenseRepository = expenseRepository;
        this.expenseItemRepository = expenseItemRepository;
        this.storeRepository = storeRepository;
        this.userRepository = userRepository;
        this.currencyService = currencyService;
        this.geocodingService = geocodingService;
        this.objectMapper = objectMapper;
        this.ocrProvider = ocrProvider;
        this.toolSchema = toolSchema;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Request body builders
    // ─────────────────────────────────────────────────────────────────────

    public OcrRequest buildRequestBody(String ocrModel, String ocrPrompt,
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
                requestBody = ocrProvider.buildTextRequestBody(ocrModel, ocrPrompt, pdfText, toolSchema, ocrUseTools, ocrDisableThinking);
                return new OcrRequest(imageBytes, "application/pdf", requestBody);
            }
            log.info("PDF for expense {} appears image-based — falling back to vision path", expenseId);
            List<byte[]> imgs = MediaUtils.convertPdfToImages(imageBytes);
            if (imgs.isEmpty()) throw new IOException("PDF contained no renderable pages");
            imageBytes = imgs.get(0);
            mediaType = "image/jpeg";
            requestBody = ocrProvider.buildVisionRequestBody(ocrModel, ocrPrompt, imgs, mediaType, toolSchema, ocrUseTools, ocrDisableThinking);
        } else {
            requestBody = ocrProvider.buildVisionRequestBody(ocrModel, ocrPrompt,
                    Collections.singletonList(imageBytes), mediaType, toolSchema, ocrUseTools, ocrDisableThinking);
        }
        return new OcrRequest(imageBytes, mediaType, requestBody);
    }

    public Map<String, Object> buildRequestBody(String ocrModel, String ocrPrompt,
                                                List<byte[]> imageBytesList, String mediaType) {
        if (imageBytesList == null || imageBytesList.isEmpty()) {
            throw new IllegalArgumentException("No image data provided");
        }
        if (!IMAGE_MEDIA_TYPES.contains(mediaType)) {
            throw new IllegalArgumentException("Unsupported media type: " + mediaType);
        }
        return ocrProvider.buildVisionRequestBody(ocrModel, ocrPrompt, imageBytesList, mediaType, toolSchema, ocrUseTools, ocrDisableThinking);
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
            OcrRequest ocrRequest = buildRequestBody(ocrModel, ocrPrompt, imagePath, expenseId);

            String currentResponseBody = callOcrApi(objectMapper.writeValueAsString(ocrRequest.requestBody()), expenseId);
            if (currentResponseBody == null) return;

            currentResponseBody = validateAndRetryToolCall(currentResponseBody, ocrRequest, expenseId);
            if (currentResponseBody == null) return;

            processOcrResponse(expenseId, currentResponseBody);
            log.info("Successfully processed receipt for expense {}", expenseId);

        } catch (IOException | InterruptedException e) {
            log.error("Failed to process receipt for expense {}", expenseId, e);
            markFailed(expenseId, e);
        }
    }

    @SuppressWarnings("unchecked")
    private String validateAndRetryToolCall(String currentResponseBody, OcrRequest ocrRequest,
                                            Long expenseId) throws IOException, InterruptedException {
        List<Map<String, Object>> messages = new ArrayList<>(
                (List<Map<String, Object>>) ocrRequest.requestBody().get("messages"));

        for (int attempt = 0; attempt <= MAX_TOOL_RETRIES; attempt++) {
            JsonNode root = objectMapper.readTree(currentResponseBody);
            JsonNode assistantMsg = ocrProvider.extractAssistantMessage(root);
            JsonNode args = ocrProvider.extractToolCallArgs(assistantMsg);
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
                if (ocrProvider.requiresToolCallId()) {
                    toolResultMsg.put("tool_call_id", ocrProvider.extractToolCallId(assistantMsg));
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
            String nextResponse = callOcrApi(objectMapper.writeValueAsString(correctionBody), expenseId);
            if (nextResponse == null) return null;
            currentResponseBody = nextResponse;
        }
        return currentResponseBody;
    }

    private String validateAmountFormula(JsonNode parsed) {
        if (!parsed.has("amount") || !parsed.has("items") || !parsed.get("items").isArray()) return null;
        BigDecimal amount = parsed.get("amount").decimalValue();
        BigDecimal sum = BigDecimal.ZERO;
        for (JsonNode item : parsed.get("items")) {
            BigDecimal qty = item.has("quantity") ? item.get("quantity").decimalValue() : BigDecimal.ONE;
            BigDecimal unitPrice = item.has("unitPrice") ? item.get("unitPrice").decimalValue() : BigDecimal.ZERO;
            BigDecimal adjustment = item.has("adjustment") ? item.get("adjustment").decimalValue() : BigDecimal.ZERO;
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

    private String callOcrApi(String jsonBody, Long expenseId) throws IOException, InterruptedException {
        log.info("Calling OCR API: POST {} with model={}", ocrApiUrl, ocrModel);
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(ocrApiUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
        if (ocrApiKey != null && !ocrApiKey.isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + ocrApiKey);
        }
        HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        String responseBody = response.body();
        log.info("OCR API response: status={}, body={}", response.statusCode(), responseBody);
        if (response.statusCode() != 200) {
            log.error("OCR API returned non-200 status {} for expense {}. Body: {}", response.statusCode(), expenseId, responseBody);
            markFailed(expenseId, new Exception(responseBody));
            return null;
        }
        return responseBody;
    }

    private void processOcrResponse(Long expenseId, String responseBody) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode assistantMsg = ocrProvider.extractAssistantMessage(root);

        JsonNode args = ocrProvider.extractToolCallArgs(assistantMsg);
        if (args != null) {
            log.info("OCR parsed receipt for expense {} via tool_call: amount={}, currency={}, items={}",
                    expenseId,
                    args.has("amount") ? args.get("amount").asText() : "N/A",
                    args.has("currency") ? args.get("currency").asText() : "N/A",
                    args.has("items") ? args.get("items").size() : 0);
            saveExpenseFromParsed(expenseId, args);
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
        JsonNode parsed = objectMapper.readTree(responseText);
        log.info("OCR parsed receipt for expense {}: amount={}, currency={}, items={}",
                expenseId,
                parsed.has("amount") ? parsed.get("amount").asText() : "N/A",
                parsed.has("currency") ? parsed.get("currency").asText() : "N/A",
                parsed.has("items") ? parsed.get("items").size() : 0);

        saveExpenseFromParsed(expenseId, parsed);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Persistence
    // ─────────────────────────────────────────────────────────────────────

    private void saveExpenseFromParsed(Long expenseId, JsonNode parsed) {
        Expense expense = expenseRepository.findById(expenseId).orElse(null);
        if (expense == null) return;

        if (parsed.has("transactionDatetime")) {
            try {
                expense.setTransactionDatetime(LocalDateTime.parse(parsed.get("transactionDatetime").asText(),
                        DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            } catch (Exception ignored) {}
        }
        if (parsed.has("amount")) expense.setAmount(parsed.get("amount").decimalValue());
        if (parsed.has("currency")) expense.setCurrency(parsed.get("currency").asText());
        if (parsed.has("receiptNumber")) expense.setReceiptNumber(parsed.get("receiptNumber").asText());
        if (parsed.has("category")) expense.setCategory(parsed.get("category").asText());

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

        expense.setStatus(ExpenseStatus.COMPLETED);
        expense.setUpdatedAt(LocalDateTime.now());
        expenseRepository.save(expense);

        List<ExpenseItem> existingItems = expenseItemRepository.findByExpenseIdAndDeletedFalse(expenseId);
        if (!existingItems.isEmpty()) {
            existingItems.forEach(i -> i.setDeleted(true));
            expenseItemRepository.saveAll(existingItems);
            log.info("Deleted {} existing items for expense {} before re-processing", existingItems.size(), expenseId);
        }

        if (parsed.has("items") && parsed.get("items").isArray()) {
            List<ExpenseItem> items = new ArrayList<>();
            for (JsonNode itemNode : parsed.get("items")) {
                ExpenseItem item = new ExpenseItem();
                item.setExpenseId(expenseId);
                item.setItemName(itemNode.has("itemName") ? itemNode.get("itemName").asText() : "");
                item.setQuantity(itemNode.has("quantity") ? itemNode.get("quantity").decimalValue() : BigDecimal.ONE);
                item.setUnitPrice(itemNode.has("unitPrice") ? itemNode.get("unitPrice").decimalValue() : BigDecimal.ZERO);
                if (itemNode.has("adjustment")) item.setAdjustment(itemNode.get("adjustment").decimalValue());
                item.setDeleted(false);
                items.add(item);
            }
            expenseItemRepository.saveAll(items);
        }

        if (parsed.has("store") && parsed.get("store").isObject()) {
            JsonNode sn = parsed.get("store");
            String sName = sn.has("name") ? sn.get("name").asText() : null;
            String sAddress = sn.has("address") ? sn.get("address").asText() : null;
            String sCity = sn.has("city") ? sn.get("city").asText() : null;
            String sCountry = sn.has("country") ? sn.get("country").asText() : null;
            String sPostal = sn.has("postalCode") ? sn.get("postalCode").asText() : null;

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
                store.setPhoneNumber(sn.has("phoneNumber") ? sn.get("phoneNumber").asText() : null);
                store.setWebsite(sn.has("website") ? sn.get("website").asText() : null);
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

