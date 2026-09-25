package com.delfino.expensetracker.service;

import com.delfino.expensetracker.BaseControllerTest;
import com.delfino.expensetracker.dto.ai.AiUsageStatusResponse;
import com.delfino.expensetracker.exception.AiQuotaExceededException;
import com.delfino.expensetracker.model.AiUsageType;
import com.delfino.expensetracker.model.User;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiUsageServiceTest extends BaseControllerTest {

    @Test
    void getCurrentStatus_createsCurrentMonthRowsWithConfiguredQuotas() {
        User user = createTestUser("alice", "pass", "USD", com.delfino.expensetracker.model.UserRole.USER, "qwen3.5-397b-a17b");

        AiUsageStatusResponse status = aiUsageService.getCurrentStatus(user.getId());

        assertThat(status.monthYear()).isEqualTo(YearMonth.now().toString());
        assertThat(status.selectedAiModel()).isEqualTo("qwen3.5-397b-a17b");
        assertThat(status.effectiveChatModel()).isEqualTo("qwen3.5-397b-a17b");
        assertThat(status.effectiveOcrModel()).isEqualTo("qwen3.5-397b-a17b");
        assertThat(status.chat().usageCount()).isZero();
        assertThat(status.chat().quota()).isEqualTo(2);
        assertThat(status.ocr().usageCount()).isZero();
        assertThat(status.ocr().quota()).isEqualTo(2);
        assertThat(aiUsageRepository.findByUserIdAndMonthYear(user.getId(), YearMonth.now().toString())).hasSize(2);
    }

    @Test
    void consume_chatQuotaBlocksWhenMonthlyLimitReached() {
        User user = createTestUser("alice", "pass");

        aiUsageService.consume(user.getId(), AiUsageType.CHAT);
        aiUsageService.consume(user.getId(), AiUsageType.CHAT);

        assertThatThrownBy(() -> aiUsageService.consume(user.getId(), AiUsageType.CHAT))
                .isInstanceOf(AiQuotaExceededException.class)
                .hasMessage("Monthly CHAT quota exceeded");
    }

    @Test
    void consume_ocrBatchRejectsWholeRequestWhenRemainingQuotaIsTooSmall() {
        User user = createTestUser("alice", "pass");

        aiUsageService.consume(user.getId(), AiUsageType.OCR);

        assertThatThrownBy(() -> aiUsageService.consume(user.getId(), AiUsageType.OCR, 2))
                .isInstanceOf(AiQuotaExceededException.class)
                .hasMessage("Monthly OCR quota exceeded");
    }

    @Test
    void consume_concurrentRequests_respectQuotaUnderContention() throws Exception {
        User user = createTestUser("alice", "pass");
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Boolean>> tasks = List.of(
                    () -> tryConsumeChat(user.getId()),
                    () -> tryConsumeChat(user.getId()),
                    () -> tryConsumeChat(user.getId()),
                    () -> tryConsumeChat(user.getId())
            );

            List<Future<Boolean>> futures = executor.invokeAll(tasks);
            int succeeded = 0;
            int denied = 0;
            for (Future<Boolean> future : futures) {
                if (future.get()) {
                    succeeded++;
                } else {
                    denied++;
                }
            }

            AiUsageStatusResponse status = aiUsageService.getCurrentStatus(user.getId());
            assertThat(succeeded).isEqualTo(2);
            assertThat(denied).isEqualTo(2);
            assertThat(status.chat().usageCount()).isEqualTo(2);
            assertThat(status.chatAllowed()).isFalse();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void consume_andQuotaExceeded_incrementObservabilityCounters() {
        User user = createTestUser("alice", "pass");
        double consumedBefore = chatCounterValue("app.ai.usage.consumed");
        double deniedBefore = chatCounterValue("app.ai.usage.denied");

        aiUsageService.consume(user.getId(), AiUsageType.CHAT);
        aiUsageService.consume(user.getId(), AiUsageType.CHAT);

        assertThatThrownBy(() -> aiUsageService.consume(user.getId(), AiUsageType.CHAT))
                .isInstanceOf(AiQuotaExceededException.class);

        assertThat(chatCounterValue("app.ai.usage.consumed") - consumedBefore).isEqualTo(2.0d);
        assertThat(chatCounterValue("app.ai.usage.denied") - deniedBefore).isEqualTo(1.0d);
    }

    private boolean tryConsumeChat(long userId) {
        try {
            aiUsageService.consume(userId, AiUsageType.CHAT);
            return true;
        } catch (AiQuotaExceededException ex) {
            return false;
        }
    }

    private double chatCounterValue(String name) {
        var counter = meterRegistry.find(name).tag("type", "chat").counter();
        return counter != null ? counter.count() : 0.0d;
    }
}

