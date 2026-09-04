package com.app.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ActivityLogService {

    private static final Logger log = LoggerFactory.getLogger(ActivityLogService.class);

    public void log(String action, String details) {
        log("Admin", action, details);
    }

    public void log(String userName, String action, String details) {
        log.info("[ActivityLog] [{}] {}: {}", userName, action, details);
    }
}
