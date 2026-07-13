package com.prizm.document.controller;

import com.prizm.document.dto.response.DocumentApprovalResponse;
import com.prizm.document.service.DocumentApprovalService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/document-versions")
public class DocumentVersionController {

    private final DocumentApprovalService documentApprovalService;

    public DocumentVersionController(DocumentApprovalService documentApprovalService) {
        this.documentApprovalService = documentApprovalService;
    }

    /** 격리된 버전을 승인하고 비동기 색인 작업을 등록한다. */
    @PostMapping("/{versionId}/approve")
    public DocumentApprovalResponse approve(@PathVariable Long versionId) {
        return documentApprovalService.approve(versionId);
    }
}
