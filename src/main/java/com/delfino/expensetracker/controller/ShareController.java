package com.delfino.expensetracker.controller;

import com.delfino.expensetracker.model.ExpenseItem;
import com.delfino.expensetracker.model.Store;
import com.delfino.expensetracker.repository.ExpenseItemRepository;
import com.delfino.expensetracker.repository.ExpenseRepository;
import com.delfino.expensetracker.repository.StoreRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Serves a lightweight HTML page with Open Graph / Twitter Card meta tags so that
 * expense detail links shared on WhatsApp, Telegram, iMessage, etc. show a rich preview.
 * The page immediately redirects the browser to the SPA hash route.
 */
@RestController
public class ShareController {

    private final ExpenseRepository expenseRepository;
    private final ExpenseItemRepository expenseItemRepository;
    private final StoreRepository storeRepository;

    public ShareController(ExpenseRepository expenseRepository,
                           ExpenseItemRepository expenseItemRepository,
                           StoreRepository storeRepository) {
        this.expenseRepository = expenseRepository;
        this.expenseItemRepository = expenseItemRepository;
        this.storeRepository = storeRepository;
    }

    @GetMapping(value = "/view/expenses/{urlId}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> shareExpense(@PathVariable String urlId, HttpServletRequest request) {
        return expenseRepository.findByUrlId(urlId)
                .map(expense -> {
                    Store store = expense.getStoreId() != null
                            ? storeRepository.findById(expense.getStoreId()).orElse(null) : null;
                    List<ExpenseItem> items = expenseItemRepository.findByExpenseIdAndDeletedFalse(expense.getId());

                    String storeName = (store != null && store.getName() != null && !store.getName().isBlank())
                            ? store.getName() : "Expense";
                    String amount   = expense.getAmount()   != null ? expense.getAmount().toPlainString()   : "";
                    String currency = expense.getCurrency() != null ? expense.getCurrency() : "";
                    String date     = expense.getTransactionDatetime() != null
                            ? expense.getTransactionDatetime().toLocalDate().toString() : "";
                    String category = expense.getCategory() != null ? expense.getCategory() : "";
                    int itemCount   = items.size();

                    String title = storeName + (amount.isEmpty() ? "" : " — " + amount + " " + currency);

                    StringBuilder desc = new StringBuilder();
                    if (!date.isEmpty())     desc.append(date).append(" · ");
                    if (!category.isEmpty()) desc.append(category).append(" · ");
                    if (itemCount > 0)       desc.append(itemCount).append(itemCount == 1 ? " item · " : " items · ");
                    desc.append(amount).append(" ").append(currency);
                    String description = desc.toString().trim().replaceAll("( · )$", "");

                    String baseUrl   = request.getScheme() + "://" + request.getServerName()
                            + (request.getServerPort() == 80 || request.getServerPort() == 443
                               ? "" : ":" + request.getServerPort());
                    String shareUrl  = baseUrl + "/view/expenses/" + urlId;
                    String appUrl    = baseUrl + "/#/expenses/" + urlId;

                    String html = buildOgHtml(title, description, shareUrl, appUrl);
                    return ResponseEntity.ok()
                            .contentType(MediaType.TEXT_HTML)
                            .body(html);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private String buildOgHtml(String title, String description, String url, String redirectUrl) {
        String t = esc(title);
        String d = esc(description);
        String r = esc(redirectUrl);
        return "<!DOCTYPE html><html lang=\"en\"><head>" +
                "<meta charset=\"UTF-8\">" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
                "<title>" + t + "</title>" +
                "<meta name=\"description\" content=\"" + d + "\">" +
                // Open Graph
                "<meta property=\"og:type\" content=\"website\">" +
                "<meta property=\"og:title\" content=\"" + t + "\">" +
                "<meta property=\"og:description\" content=\"" + d + "\">" +
                "<meta property=\"og:url\" content=\"" + esc(url) + "\">" +
                // Twitter / X Card
                "<meta name=\"twitter:card\" content=\"summary\">" +
                "<meta name=\"twitter:title\" content=\"" + t + "\">" +
                "<meta name=\"twitter:description\" content=\"" + d + "\">" +
                // Immediate redirect
                "<meta http-equiv=\"refresh\" content=\"0;url=" + r + "\">" +
                "</head><body style=\"font-family:sans-serif;text-align:center;padding:2rem\">" +
                "<p>Redirecting to expense details…</p>" +
                "<a href=\"" + r + "\">Click here if not redirected</a>" +
                "<script>window.location.replace('" + r + "');</script>" +
                "</body></html>";
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}


