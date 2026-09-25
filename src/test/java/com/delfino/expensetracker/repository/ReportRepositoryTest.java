package com.delfino.expensetracker.repository;

import com.delfino.expensetracker.BaseControllerTest;
import com.delfino.expensetracker.model.Report;
import com.delfino.expensetracker.model.ReportGroupBy;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportRepositoryTest extends BaseControllerTest {

    @Test
    void findByUserIdOrderByCreatedAtDesc_shouldReturnOnlyUserReportsInDescendingOrder() {
        var alice = createTestUser("alice", "pass");
        var bob = createTestUser("bob", "pass");

        Report older = saveReport(alice.getId(), "Older", LocalDateTime.now().minusDays(1));
        Report newer = saveReport(alice.getId(), "Newer", LocalDateTime.now());
        saveReport(bob.getId(), "Bobs report", LocalDateTime.now().plusMinutes(1));

        List<Report> reports = reportRepository.findByUserIdOrderByCreatedAtDesc(alice.getId());

        assertThat(reports).extracting(Report::getId).containsExactly(newer.getId(), older.getId());
        assertThat(reports).allMatch(report -> report.getUserId().equals(alice.getId()));
    }

    @Test
    void findByIdAndUserId_shouldRespectOwnership() {
        var alice = createTestUser("alice", "pass");
        var bob = createTestUser("bob", "pass");
        Report report = saveReport(alice.getId(), "Alice report", LocalDateTime.now());

        assertThat(reportRepository.findByIdAndUserId(report.getId(), alice.getId())).isPresent();
        assertThat(reportRepository.findByIdAndUserId(report.getId(), bob.getId())).isEmpty();
    }

    private Report saveReport(Long userId, String title, LocalDateTime createdAt) {
        Report report = new Report();
        report.setUserId(userId);
        report.setTitle(title);
        report.setDescription("Description for " + title);
        report.setExpenseIds(List.of());
        report.setChartDefinitions(objectMapper.createArrayNode());
        report.setGroupBy(ReportGroupBy.KEYWORD);
        report.setFilterSnapshot(objectMapper.createObjectNode().put("search", title.toLowerCase()));
        report.setInsights(objectMapper.createArrayNode());
        report.setCreatedAt(createdAt);
        report.setUpdatedAt(createdAt);
        return reportRepository.save(report);
    }
}

