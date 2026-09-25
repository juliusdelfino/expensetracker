package com.delfino.expensetracker.service.mcp;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@RequestScope
public class ChatReportContext {

    private final Set<Long> linkedReportIds = new LinkedHashSet<>();

    public void trackReport(Long reportId) {
        if (reportId != null && reportId > 0) {
            linkedReportIds.add(reportId);
        }
    }

    public List<Long> getLinkedReportIds() {
        return new ArrayList<>(linkedReportIds);
    }

    public void clear() {
        linkedReportIds.clear();
    }
}

