// exam/ExamSettings.java
package com.komron.rostly.exam;

import com.komron.rostly.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(name = "exam_settings")
public class ExamSettings {

    @Id @GeneratedValue @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id", nullable = false, unique = true)
    private Exam exam;

    @Builder.Default @Column(nullable = false)
    private boolean requireCamera = false;

    @Builder.Default @Column(nullable = false)
    private boolean requireMicrophone = false;

    @Builder.Default @Column(nullable = false)
    private boolean allowCopyPaste = true;

    @Builder.Default @Column(nullable = false)
    private boolean allowTabSwitch = false;

    private Integer maxIdleSeconds;
    private Integer maxViolations;
    private Integer randomPhotoInterval;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // exam/ExamSettings.java — add createdBy, updatedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}