package com.prizm.jobposting.controller;

import com.prizm.jobposting.dto.request.JobPostingSegmentationRequest;
import com.prizm.jobposting.dto.response.JobPostingItemResponse;
import com.prizm.jobposting.service.JobPostingSegmentationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/job-postings")
public class JobPostingSegmentationController {

    private final JobPostingSegmentationService segmentationService;

    public JobPostingSegmentationController(JobPostingSegmentationService segmentationService) {
        this.segmentationService = segmentationService;
    }

    @PostMapping("/segment")
    public List<JobPostingItemResponse> segment(
            @Valid @RequestBody JobPostingSegmentationRequest request) {
        return segmentationService.segment(request.content());
    }
}
