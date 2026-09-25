package com.delfino.expensetracker.service;

import com.delfino.expensetracker.model.ExpenseShare;
import com.delfino.expensetracker.model.ShareAccessLog;
import com.delfino.expensetracker.repository.ShareAccessLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ShareAccessLogService {

    private final ShareAccessLogRepository shareAccessLogRepository;

    public ShareAccessLogService(ShareAccessLogRepository shareAccessLogRepository) {
        this.shareAccessLogRepository = shareAccessLogRepository;
    }

    public void recordAccess(HttpServletRequest request,
                             ExpenseShare share,
                             String resourceType,
                             String resourceName,
                             boolean allowed,
                             int statusCode) {
        ShareAccessLog accessLog = new ShareAccessLog();
        accessLog.setShareId(share != null ? share.getId() : null);
        accessLog.setExpenseId(share != null ? share.getExpenseId() : null);
        accessLog.setResourceType(resourceType);
        accessLog.setResourceName(truncate(resourceName, 512));
        accessLog.setClientIp(truncate(resolveClientIp(request), 128));
        accessLog.setUserAgent(truncate(request != null ? request.getHeader("User-Agent") : null, 512));
        accessLog.setAllowed(allowed);
        accessLog.setStatusCode(statusCode);
        accessLog.setAccessedAt(LocalDateTime.now());
        shareAccessLogRepository.save(accessLog);
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
