package com.delfino.expensetracker.service;

import com.delfino.expensetracker.model.Expense;
import com.delfino.expensetracker.model.ExpenseItem;
import com.delfino.expensetracker.model.ExpenseStatus;
import com.delfino.expensetracker.model.Store;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.delfino.expensetracker.repository.ExpenseItemRepository;
import com.delfino.expensetracker.repository.ExpenseRepository;
import com.delfino.expensetracker.repository.StoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);
    private final ExpenseRepository expenseRepository;
    private final ExpenseItemRepository expenseItemRepository;
    private final StoreRepository storeRepository;
    private final CurrencyService currencyService;
    private final GeocodingService geocodingService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${ocr.api.url:http://localhost:11434/api/generate}")
    private String ocrApiUrl;

    @Value("${ocr.api.model:llava}")
    private String ocrModel;

    @Value("${ocr.api.prompt:Parse this receipt image and return a JSON object with these fields: {\"transactionDatetime\":\"ISO 8601\",\"amount\":0,\"currency\":\"USD\",\"receiptNumber\":\"\",\"category\":\"\",\"items\":[{\"itemName\":\"\",\"quantity\":1,\"unitPrice\":0,\"totalPrice\":0}],\"store\":{\"name\":\"\",\"address\":\"\",\"city\":\"\",\"country\":\"\",\"postalCode\":\"\",\"phoneNumber\":\"\",\"website\":\"\"}} Return ONLY valid JSON.}")
    private String ocrPrompt;

    public OcrService(ExpenseRepository expenseRepository, ExpenseItemRepository expenseItemRepository,
                      StoreRepository storeRepository, CurrencyService currencyService,
                      GeocodingService geocodingService, ObjectMapper objectMapper) {
        this.expenseRepository = expenseRepository;
        this.expenseItemRepository = expenseItemRepository;
        this.storeRepository = storeRepository;
        this.currencyService = currencyService;
        this.geocodingService = geocodingService;
        this.objectMapper = objectMapper;
    }

    @Async
    public void processReceipt(UUID expenseId, String imagePath, String userBaseCurrency) {
        try {
            log.info("Processing receipt for expense {}: reading image from {}", expenseId, imagePath);
            byte[] imageBytes = Files.readAllBytes(Path.of(imagePath));
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", ocrModel);
            requestBody.put("prompt", ocrPrompt);
            requestBody.put("images", List.of(base64Image));
            requestBody.put("stream", false);

            String jsonBody = objectMapper.writeValueAsString(requestBody);

            log.info("Calling OCR API: POST {} with model={}, imageSize={}KB", ocrApiUrl, ocrModel, imageBytes.length / 1024);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ocrApiUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();
            log.info("OCR API response: status={}, body={}", response.statusCode(), responseBody);

            if (response.statusCode() != 200) {
                log.error("OCR API returned non-200 status {} for expense {}. Body: {}", response.statusCode(), expenseId, responseBody);
                markFailed(expenseId);
                return;
            }

            JsonNode responseNode = objectMapper.readTree(responseBody);
            String responseText = responseNode.has("response") ? responseNode.get("response").asText() : responseBody;

            // Strip markdown code fences if present
            responseText = responseText.strip();
            if (responseText.startsWith("```")) {
                responseText = responseText.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("```$", "").strip();
            }

            JsonNode parsed = objectMapper.readTree(responseText);
            log.info("OCR parsed receipt for expense {}: amount={}, currency={}, items={}",
                    expenseId,
                    parsed.has("amount") ? parsed.get("amount").asText() : "N/A",
                    parsed.has("currency") ? parsed.get("currency").asText() : "N/A",
                    parsed.has("items") ? parsed.get("items").size() : 0);

            Expense expense = expenseRepository.findById(expenseId).orElse(null);
            if (expense == null) return;

            // Update expense fields
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

            // Currency conversion
            if (expense.getCurrency() != null && userBaseCurrency != null && expense.getAmount() != null) {
                BigDecimal rate = currencyService.getRate(expense.getCurrency(), userBaseCurrency,
                        expense.getTransactionDatetime() != null ? expense.getTransactionDatetime().toLocalDate() : java.time.LocalDate.now());
                if (rate != null) {
                    expense.setExchangeRate(rate);
                    expense.setAmountInBase(expense.getAmount().multiply(rate));
                }
            }

            expense.setStatus(ExpenseStatus.COMPLETED);
            expense.setUpdatedAt(LocalDateTime.now());
            expenseRepository.save(expense);

            // Parse items
            if (parsed.has("items") && parsed.get("items").isArray()) {
                List<ExpenseItem> items = new ArrayList<>();
                for (JsonNode itemNode : parsed.get("items")) {
                    ExpenseItem item = new ExpenseItem();
                    item.setId(UUID.randomUUID());
                    item.setExpenseId(expenseId);
                    item.setItemName(itemNode.has("itemName") ? itemNode.get("itemName").asText() : "");
                    item.setQuantity(itemNode.has("quantity") ? itemNode.get("quantity").decimalValue() : BigDecimal.ONE);
                    item.setUnitPrice(itemNode.has("unitPrice") ? itemNode.get("unitPrice").decimalValue() : BigDecimal.ZERO);
                    item.setTotalPrice(itemNode.has("totalPrice") ? itemNode.get("totalPrice").decimalValue()
                            : item.getQuantity().multiply(item.getUnitPrice()));
                    item.setDeleted(false);
                    items.add(item);
                }
                expenseItemRepository.saveAll(items);
            }

            // Parse store
            if (parsed.has("store") && parsed.get("store").isObject()) {
                JsonNode sn = parsed.get("store");
                String sName = sn.has("name") ? sn.get("name").asText() : null;
                String sAddress = sn.has("address") ? sn.get("address").asText() : null;
                String sCity = sn.has("city") ? sn.get("city").asText() : null;
                String sCountry = sn.has("country") ? sn.get("country").asText() : null;
                String sPostal = sn.has("postalCode") ? sn.get("postalCode").asText() : null;

                // Try to find an existing store for this user with matching key fields
                Optional<Store> existingStore = storeRepository.findMatchingStore(
                        expense.getUserId(), sName, sAddress, sCity, sCountry, sPostal);

                if (existingStore.isPresent()) {
                    // Reuse existing store
                    Store matched = existingStore.get();
                    expense.setStoreId(matched.getId());
                    expenseRepository.save(expense);
                    log.info("Reused existing store {} for expense {}", matched.getId(), expenseId);
                } else {
                    // Create new store
                    Store store = new Store();
                    store.setId(UUID.randomUUID());
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

                    // Async geocode the store address to fill lat/long + save place_id
                    geocodingService.geocodeStoreAsync(store);
                }
            }

            log.info("Successfully processed receipt for expense {}", expenseId);

        } catch (Exception e) {
            log.error("Failed to process receipt for expense {}", expenseId, e);
            markFailed(expenseId);
        }
    }

    private void markFailed(UUID expenseId) {
        expenseRepository.findById(expenseId).ifPresent(expense -> {
            expense.setStatus(ExpenseStatus.FAILED);
            expense.setUpdatedAt(LocalDateTime.now());
            expenseRepository.save(expense);
        });
    }
}

