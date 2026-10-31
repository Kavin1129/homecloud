package com.homecloud.audit;

import com.homecloud.user.User;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void log(
            User user,
            String action,
            String entityType,
            Long entityId,
            String details) {
        AuditLog auditLog = new AuditLog(
                user,
                action,
                entityType,
                entityId,
                details
        );

        auditLogRepository.save(auditLog);
    }
}