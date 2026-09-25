package com.delfino.expensetracker.service;

import com.delfino.expensetracker.dto.report.CreateReportRequest;
import com.delfino.expensetracker.dto.report.ReportFilterRequest;
import com.delfino.expensetracker.model.ReportGroupBy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Locale;

final class ReportFilterSupport {

    private ReportFilterSupport() {
    }

    static List<String> normalizeKeywords(List<String> keywords, String search) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (keywords != null) {
            keywords.stream()
                    .filter(Objects::nonNull)
                    .map(ReportFilterSupport::trimToNull)
                    .filter(Objects::nonNull)
                    .map(value -> value.toLowerCase(Locale.ROOT))
                    .forEach(unique::add);
        }
        if (search != null && !search.isBlank()) {
            for (String token : search.split("[,;\\n]")) {
                String trimmed = trimToNull(token);
                if (trimmed != null) {
                    unique.add(trimmed.toLowerCase(Locale.ROOT));
                }
            }
        }
        return List.copyOf(unique);
    }

    static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static JsonNode buildFilterSnapshot(ObjectMapper objectMapper, CreateReportRequest request, List<Long> expenseIds) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("groupBy", request.groupBy().name());

        if (request.expenseIds() != null) {
            node.put("mode", "EXPLICIT_EXPENSE_IDS");
            ArrayNode ids = node.putArray("expenseIds");
            expenseIds.forEach(ids::add);
            return node;
        }

        node.put("mode", "FILTERS");
        putIfHasText(node, "startDate", request.startDate());
        putIfHasText(node, "endDate", request.endDate());
        putIfHasText(node, "category", request.category());
        putIfHasText(node, "country", request.country());
        putIfHasText(node, "city", request.city());
        putIfHasText(node, "storeName", request.storeName());
        List<String> keywords = normalizeKeywords(request.searchKeywords(), request.search());
        if (!keywords.isEmpty()) {
            node.put("search", String.join(", ", keywords));
            ArrayNode keywordArray = node.putArray("searchKeywords");
            keywords.forEach(keywordArray::add);
        }
        return node;
    }

    static JsonNode buildFilterSnapshot(ObjectMapper objectMapper, ReportGroupBy groupBy, ReportFilterRequest filterRequest) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("groupBy", groupBy.name());
        node.put("mode", "FILTERS");
        putIfHasText(node, "startDate", filterRequest.startDate());
        putIfHasText(node, "endDate", filterRequest.endDate());
        putIfHasText(node, "category", filterRequest.category());
        putIfHasText(node, "country", filterRequest.country());
        putIfHasText(node, "city", filterRequest.city());
        putIfHasText(node, "storeName", filterRequest.storeName());
        List<String> keywords = normalizeKeywords(filterRequest.searchKeywords(), filterRequest.search());
        if (!keywords.isEmpty()) {
            node.put("search", String.join(", ", keywords));
            ArrayNode keywordArray = node.putArray("searchKeywords");
            keywords.forEach(keywordArray::add);
        }
        return node;
    }

    private static void putIfHasText(ObjectNode node, String key, String value) {
        String trimmed = trimToNull(value);
        if (trimmed != null) {
            node.put(key, trimmed);
        }
    }
}

