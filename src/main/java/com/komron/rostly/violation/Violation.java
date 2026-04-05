// violation/Violation.java
package com.komron.rostly.violation;

import com.komron.rostly.session.ExamSession;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(name = "violations")
public class Violation {

    @Id @GeneratedValue @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ExamSession session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ViolationType type;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    private Integer durationSeconds;

    @Column(length = 500)
    private String evidenceUrl;

    @Column(precision = 5, scale = 2)
    private BigDecimal penaltyScore;

    @PrePersist
    protected void onCreate() {
        occurredAt = LocalDateTime.now();
    }
}