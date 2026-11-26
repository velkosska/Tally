package com.tally.web;

import com.tally.service.DashboardService;
import com.tally.service.view.GroupDashboard;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.nio.charset.StandardCharsets;

@Controller
public class ExportController {

    /** Byte order mark, so Excel opens the UTF-8 file with accents and € signs intact. */
    private static final String UTF8_BOM = "﻿";

    private final DashboardService dashboardService;
    private final CsvExporter csvExporter;

    public ExportController(DashboardService dashboardService, CsvExporter csvExporter) {
        this.dashboardService = dashboardService;
        this.csvExporter = csvExporter;
    }

    @GetMapping("/groups/{groupId}/export.csv")
    public ResponseEntity<byte[]> exportCsv(@PathVariable long groupId) {
        GroupDashboard group = dashboardService.dashboard(groupId);
        byte[] body = (UTF8_BOM + csvExporter.export(group)).getBytes(StandardCharsets.UTF_8);
        String fileName = group.name().replaceAll("[^A-Za-z0-9-_]+", "-").toLowerCase() + ".csv";

        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName).build().toString())
                .body(body);
    }
}
