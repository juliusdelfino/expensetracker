package com.delfino.expensetracker;

import com.delfino.expensetracker.model.Expense;
import com.delfino.expensetracker.model.ExpenseItem;
import com.delfino.expensetracker.model.ExpenseStatus;
import com.delfino.expensetracker.model.ExpenseType;
import com.delfino.expensetracker.model.Store;
import com.delfino.expensetracker.model.User;
import com.delfino.expensetracker.repository.ChatMessageRepository;
import com.delfino.expensetracker.repository.ExchangeRateCacheRepository;
import com.delfino.expensetracker.repository.ExpenseItemRepository;
import com.delfino.expensetracker.repository.ExpenseRepository;
import com.delfino.expensetracker.repository.StoreRepository;
import com.delfino.expensetracker.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for all controller integration tests.
 * <p>
 * Uses a real SQLite DB with Hibernate create-drop for full integration coverage.
 * A single WireMock server stubs all external HTTP calls so no service beans are mocked:
 * <ul>
 *   <li>Frankfurter currency API  → {@code GET /currencies} and {@code GET /{date}?from=&to=}</li>
 *   <li>Ollama chat API           → {@code POST /api/chat}</li>
 *   <li>OCR (Ollama generate) API → {@code POST /api/generate}</li>
 *   <li>Nominatim geocoding API   → {@code GET /search}</li>
 * </ul>
 */
@Import({BaseControllerTest.Http11RestClientConfig.class, BaseControllerTest.SynchronousAsyncConfig.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseControllerTest {

    // -------------------------------------------------------------------------
    // Force HTTP/1.1 for the RestClient used by Spring AI's OllamaApi.
    // WireMock (Jetty) only supports HTTP/1.1 by default; the JDK HttpClient
    // used by Spring's JdkClientHttpRequestFactory tries to upgrade to HTTP/2
    // which WireMock rejects with RST_STREAM, causing long retry backoffs.
    // -------------------------------------------------------------------------
    @TestConfiguration(proxyBeanMethods = false)
    static class Http11RestClientConfig {
        @Bean
        @Primary
        RestClient.Builder http11RestClientBuilder() {

            HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                    .build();
            return RestClient.builder()
                    .requestFactory(new JdkClientHttpRequestFactory(httpClient));
        }
    }

    @TestConfiguration
    static class SynchronousAsyncConfig implements AsyncConfigurer {

        @Override
        public Executor getAsyncExecutor() {
            // Returns a synchronous executor — tasks run on the calling thread
            return Runnable::run;
        }
    }

    // -------------------------------------------------------------------------
    // Single WireMock server shared across ALL test classes via static singleton.
    // Started once at JVM startup; dynamic port kept constant so Spring's test
    // context cache can be reused across subclasses.
    // -------------------------------------------------------------------------
    protected static final WireMockServer WIRE_MOCK;

    static {
        WIRE_MOCK = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        WIRE_MOCK.start();
        Runtime.getRuntime().addShutdownHook(new Thread(WIRE_MOCK::stop));
    }

    /**
     * Override every external-service URL to point to WireMock before the
     * Spring application context is created.
     */
    @DynamicPropertySource
    static void overrideExternalUrls(DynamicPropertyRegistry registry) {
        String base = "http://localhost:" + WIRE_MOCK.port();
        // Frankfurter currency API (used by CurrencyService + SupportedCurrencyService)
        registry.add("currency.api.url", () -> base);
        // OCR API – full endpoint URL (used directly by OcrService)
        registry.add("ocr.api.url", () -> base + "/api/generate");
        // Ollama base URL (used by Spring AI ChatClient → OllamaApi)
        registry.add("spring.ai.ollama.base-url", () -> base);
        // Nominatim geocoding API (used by GeocodingService)
        registry.add("geocoding.api.url", () -> base);
    }

    // -------------------------------------------------------------------------
    // Spring beans
    // -------------------------------------------------------------------------
    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected UserRepository userRepository;
    @Autowired protected ExpenseRepository expenseRepository;
    @Autowired protected ExpenseItemRepository expenseItemRepository;
    @Autowired protected StoreRepository storeRepository;
    @Autowired protected ChatMessageRepository chatMessageRepository;
    @Autowired protected ExchangeRateCacheRepository exchangeRateCacheRepository;
    @Autowired protected PasswordEncoder passwordEncoder;

    // -------------------------------------------------------------------------
    // Per-test setup: clean DB + reset WireMock stubs to sensible defaults
    // -------------------------------------------------------------------------
    @BeforeEach
    void setUpEach() {
        // 1. Clean all data for test isolation
        chatMessageRepository.deleteAll();
        expenseItemRepository.deleteAll();
        expenseRepository.deleteAll();
        storeRepository.deleteAll();
        exchangeRateCacheRepository.deleteAll();
        userRepository.deleteAll();

        // 2. Reset and re-register default WireMock stubs
        WIRE_MOCK.resetAll();
        registerDefaultStubs();
    }

    /**
     * Register default WireMock stubs that cover the happy path for every
     * external service. Individual tests can add more-specific stubs that
     * WireMock will evaluate before falling through to these defaults.
     */
    protected void registerDefaultStubs() {
        // -- Frankfurter: GET /currencies -----------------------------------------
        WIRE_MOCK.stubFor(WireMock.get(urlPathEqualTo("/currencies"))
                .willReturn(okJson(
                        "{\"USD\":\"United States Dollar\",\"EUR\":\"Euro\"," +
                        "\"GBP\":\"British Pound\",\"SGD\":\"Singapore Dollar\"," +
                        "\"JPY\":\"Japanese Yen\",\"AUD\":\"Australian Dollar\"," +
                        "\"CHF\":\"Swiss Franc\",\"CNY\":\"Chinese Renminbi Yuan\"," +
                        "\"CAD\":\"Canadian Dollar\",\"HKD\":\"Hong Kong Dollar\"}")
                        .withHeader("Connection", "close")));

        // -- Frankfurter: GET /{date}?from=X&to=Y → exchange rate -----------------
        WIRE_MOCK.stubFor(WireMock.get(urlPathMatching("/[0-9]{4}-[0-9]{2}-[0-9]{2}"))
                .willReturn(okJson(
                        "{\"rates\":{\"EUR\":0.92,\"GBP\":0.79,\"SGD\":1.35," +
                        "\"JPY\":150.0,\"AUD\":1.55,\"CHF\":0.90," +
                        "\"CNY\":7.25,\"CAD\":1.36,\"HKD\":7.82}}")
                        .withHeader("Connection", "close")));

        // -- OCR API: POST /api/generate → valid receipt JSON ---------------------
        WIRE_MOCK.stubFor(WireMock.post(urlPathEqualTo("/api/generate"))
                .willReturn(okJson(
                        "{\"response\":\"{\\\"transactionDatetime\\\":\\\"2026-04-01T12:00:00\\\"," +
                        "\\\"amount\\\":12.50,\\\"currency\\\":\\\"USD\\\",\\\"category\\\":\\\"Food\\\"," +
                        "\\\"receiptNumber\\\":\\\"001\\\",\\\"items\\\":[]," +
                        "\\\"store\\\":{\\\"name\\\":\\\"TestShop\\\",\\\"city\\\":\\\"Singapore\\\"," +
                        "\\\"country\\\":\\\"SG\\\"}}\"}")
                        .withHeader("Connection", "close")));

        // -- Ollama chat API: POST /api/chat → plain-text assistant reply ----------
        WIRE_MOCK.stubFor(WireMock.post(urlPathEqualTo("/api/chat"))
                .willReturn(okJson(ollamaChatResponse(
                        "I'm a helpful expense assistant. How can I help you today?"))
                        .withHeader("Connection", "close")));

        // -- Nominatim: GET /search → empty result (no geocoding in tests) --------
        WIRE_MOCK.stubFor(WireMock.get(urlPathEqualTo("/search"))
                .willReturn(okJson("[]").withHeader("Connection", "close")));
    }

    // -------------------------------------------------------------------------
    // WireMock helper
    // -------------------------------------------------------------------------

    /**
     * Build a minimal valid Ollama /api/chat response body for Spring AI's OllamaApi.
     */
    protected static String ollamaChatResponse(String content) {
        // Escape the content for JSON embedding
        String escaped = content
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
        return "{\"model\":\"test-model\"," +
               "\"created_at\":\"2026-04-08T00:00:00.000Z\"," +
               "\"message\":{\"role\":\"assistant\",\"content\":\"" + escaped + "\"}," +
               "\"done_reason\":\"stop\",\"done\":true}";
    }

    /**
     * Build an Ollama /api/chat response that requests a single tool call.
     * Spring AI will intercept the tool_calls field, execute the matching
     * {@code @Tool}-annotated method, and send the result back to the LLM
     * in a second request.
     *
     * @param toolName     The Java method name of the {@code @Tool}-annotated method
     *                     (Spring AI uses the method name as the tool name by default).
     * @param argumentsJson A JSON-object string of the arguments, e.g.
     *                     {@code "{\"itemName\":\"milk\",\"storeName\":\"\"}"},
     *                     or {@code "{}"} for parameterless tools.
     */
    protected static String ollamaToolCallResponse(String toolName, String argumentsJson) {
        return "{\"model\":\"test-model\"," +
               "\"created_at\":\"2026-04-08T00:00:00.000Z\"," +
               "\"message\":{\"role\":\"assistant\",\"content\":\"\"," +
               "\"tool_calls\":[{\"function\":{\"name\":\"" + toolName + "\"," +
               "\"arguments\":" + argumentsJson + "}}]}," +
               "\"done_reason\":\"stop\",\"done\":true}";
    }

    // -------------------------------------------------------------------------
    // DB helpers
    // -------------------------------------------------------------------------

    protected User createTestUser(String username, String password) {
        return createTestUser(username, password, "USD");
    }

    protected User createTestUser(String username, String password, String baseCurrency) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setBaseCurrency(baseCurrency);
        user.setEmail(username + "@test.com");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    protected MockHttpSession loginAs(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    protected Expense createTestExpense(long userId, String category, BigDecimal amount, String currency) {
        Expense expense = new Expense();
        expense.setUserId(userId);
        expense.setType(ExpenseType.MANUAL);
        expense.setStatus(ExpenseStatus.COMPLETED);
        expense.setCategory(category);
        expense.setAmount(amount);
        expense.setAmountInBase(amount);
        expense.setCurrency(currency);
        expense.setTransactionDatetime(LocalDateTime.now());
        expense.setDeleted(false);
        expense.setCreatedAt(LocalDateTime.now());
        expense.setUpdatedAt(LocalDateTime.now());
        expense.setUrlId(UUID.randomUUID().toString());
        return expenseRepository.save(expense);
    }

    protected ExpenseItem createTestItem(long expenseId, String itemName, BigDecimal qty, BigDecimal price) {
        ExpenseItem item = new ExpenseItem();
        item.setExpenseId(expenseId);
        item.setItemName(itemName);
        item.setQuantity(qty);
        item.setUnitPrice(price);
        item.setTotalPrice(qty.multiply(price));
        item.setDeleted(false);
        return expenseItemRepository.save(item);
    }

    protected Store createTestStore(long userId, String name, String city, String country) {
        Store store = new Store();
        store.setUserId(userId);
        store.setName(name);
        store.setCity(city);
        store.setCountry(country);
        store.setLatitude(1.3521);
        store.setLongitude(103.8198);
        return storeRepository.save(store);
    }


    protected void linkExpenseToStore(Expense expense, Store store) {
        expense.setStoreId(store.getId());
        expense.setUpdatedAt(LocalDateTime.now());
        expenseRepository.save(expense);
    }
}



