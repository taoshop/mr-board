package com.mrboard.system.service;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BoardStatusCalculator {

    public String calculate(String platformStatus, Boolean hasConflict, String ciStatus, Boolean mergeable) {
        return calculate(platformStatus, hasConflict, ciStatus, mergeable, "");
    }

    public String calculate(String platformStatus, Boolean hasConflict, String ciStatus, Boolean mergeable, String title) {
        if (platformStatus == null) platformStatus = "";
        if (ciStatus == null) ciStatus = "unknown";

        if ("merged".equalsIgnoreCase(platformStatus)) {
            return "merged";
        }
        if ("closed".equalsIgnoreCase(platformStatus)) {
            return "closed";
        }

        String titleLower = title != null ? title.toLowerCase() : "";
        boolean isDraft = titleLower.startsWith("draft:") || titleLower.startsWith("wip:");

        if (Boolean.TRUE.equals(hasConflict)) {
            return "conflict";
        }

        boolean ciFailed = "failed".equalsIgnoreCase(ciStatus);
        boolean ciRunning = "running".equalsIgnoreCase(ciStatus) || "pending".equalsIgnoreCase(ciStatus);
        boolean ciSuccess = "success".equalsIgnoreCase(ciStatus);

        if (ciFailed) {
            return "failed";
        }

        if (ciRunning) {
            return "testing";
        }

        if (!ciSuccess && !"unknown".equalsIgnoreCase(ciStatus)) {
            return "testing";
        }

        if (Boolean.FALSE.equals(mergeable)) {
            return "conflict";
        }

        if (isDraft) {
            return "open";
        }

        return "ready";
    }
}
