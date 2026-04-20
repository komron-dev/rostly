package com.komron.rostly.exam;

import com.komron.rostly.config.PageResponse;
import com.komron.rostly.exam.dto.ExamResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/student/exams")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('STUDENT')")
public class StudentExamController {

    private final ExamService examService;

    @GetMapping
    public ResponseEntity<PageResponse<ExamResponse>> listMyExams(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(examService.listStudentExams(search, page, size));
    }

    @GetMapping("/{examId}")
    public ResponseEntity<ExamResponse> getMyExam(@PathVariable UUID examId) {
        return ResponseEntity.ok(examService.getStudentExam(examId));
    }
}