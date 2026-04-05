// session/Answer.java
package com.komron.rostly.session;

import com.komron.rostly.question.Option;
import com.komron.rostly.question.Question;
import com.komron.rostly.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(name = "answers")
public class Answer {

    @Id @GeneratedValue @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ExamSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_option_id")
    private Option selectedOption;

    @Column(columnDefinition = "TEXT")
    private String textAnswer;

    @Column(length = 500)
    private String fileUrl;

    @Column(precision = 5, scale = 2)
    private BigDecimal pointsAwarded;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "graded_by")
    private User gradedBy;

    private LocalDateTime gradedAt;
}