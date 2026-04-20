package com.komron.rostly.question;

import com.komron.rostly.question.dto.QuestionRequest;
import com.komron.rostly.question.dto.QuestionResponse;
import com.komron.rostly.question.dto.ReorderQuestionsRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/exams/{examId}/questions")
@Slf4j
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('TEACHER') or hasAuthority('ADMIN')")
public class QuestionController {

    private final QuestionService questionService;

    @PostMapping
    public ResponseEntity<QuestionResponse> createQuestion(
            @PathVariable UUID examId,
            @Valid @RequestBody QuestionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(questionService.createQuestion(examId, request));
    }

    @GetMapping("/{questionId}")
    public ResponseEntity<QuestionResponse> getQuestion(
            @PathVariable UUID examId,
            @PathVariable UUID questionId) {
        return ResponseEntity.ok(questionService.getQuestion(examId, questionId));
    }

    @PutMapping("/{questionId}")
    public ResponseEntity<QuestionResponse> updateQuestion(
            @PathVariable UUID examId,
            @PathVariable UUID questionId,
            @Valid @RequestBody QuestionRequest request) {
        return ResponseEntity.ok(questionService.updateQuestion(examId, questionId, request));
    }

    @GetMapping
    public ResponseEntity<List<QuestionResponse>> listQuestions(
            @PathVariable UUID examId) {
        return ResponseEntity.ok(questionService.listQuestions(examId));
    }

    @DeleteMapping("/{questionId}")
    public ResponseEntity<Void> deleteQuestion(
            @PathVariable UUID examId,
            @PathVariable UUID questionId) {
        questionService.deleteQuestion(examId, questionId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/reorder")
    public ResponseEntity<Void> reorderQuestions(
            @PathVariable UUID examId,
            @Valid @RequestBody ReorderQuestionsRequest request) {
        questionService.reorderQuestions(examId, request);
        return ResponseEntity.noContent().build();
    }
}