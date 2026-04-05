// invitation/ExamInvitation.java
package com.komron.rostly.invitation;

import com.komron.rostly.exam.Exam;
import com.komron.rostly.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(name = "exam_invitations")
public class ExamInvitation {

    @Id @GeneratedValue @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sent_by", nullable = false)
    private User sentBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvitationStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime sentAt;

    private LocalDateTime acceptedAt;

    // invitation/ExamInvitation.java — add expiresAt
    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @PrePersist
    protected void onCreate() {
        sentAt = LocalDateTime.now();
    }
}