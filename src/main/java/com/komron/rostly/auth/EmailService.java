package com.komron.rostly.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendVerificationEmail(String to, String name, String token) {
        log.info("Sending verification email to {}", to);

        String link = "http://localhost:5173/api/auth/verify?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Verify your Rostly account");
        message.setText(
                "Hi " + name + ",\n\n" +
                        "Click the link below to verify your email address:\n\n" +
                        link + "\n\n" +
                        "This link expires in 12 hours.\n\n" +
                        "If you did not create a Rostly account, ignore this email."
        );

        mailSender.send(message);
        log.info("Verification email sent successfully to {}", to);
    }

    public void sendPendingApprovalEmail(String to, String name) {
        log.info("Sending pending approval email to {}", to);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Your Rostly account is pending approval");
        message.setText(
                "Hi " + name + ",\n\n" +
                        "Your email has been verified. Your teacher account is now pending " +
                        "admin approval.\n\n" +
                        "You will receive another email once your account has been approved.\n\n" +
                        "Rostly Team"
        );

        mailSender.send(message);
        log.info("Pending approval email sent to {}", to);
    }

    public void sendAccountApprovedEmail(String to, String name) {
        log.info("Sending account approved email to {}", to);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Your Rostly account has been approved");
        message.setText(
                "Hi " + name + ",\n\n" +
                        "Your teacher account has been approved. You can now log in to " +
                        "Rostly.\n\n" +
                        "Rostly Team"
        );
    }

    public void sendEmailChangeVerification(String to, String name, String token) {
        log.info("Sending email change verification to {}", to);

        String link = "http://localhost:5173/api/auth/verify?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Confirm your new Rostly email address");
        message.setText(
                "Hi " + name + ",\n\n" +
                        "Click the link below to confirm your new email address:\n\n" +
                        link + "\n\n" +
                        "This link expires in 1 hour.\n\n" +
                        "If you did not request this change, ignore this email — " +
                        "your current email remains active."
        );

        mailSender.send(message);
    }

    public void sendPasswordChangedNotification(String to, String name) {
        SimpleMailMessage message = new SimpleMailMessage();

        message.setTo(to);
        message.setSubject("Your Rostly password was changed");
        message.setText(
                "Hi " + name + ",\n\n" +
                        "Your account password was just changed.\n\n" +
                        "If this was you, no action is needed.\n\n" +
                        "If you did not make this change, your account may be compromised. " +
                        "Contact support immediately at rostly.platfrom@gmail.com"
        );

        mailSender.send(message);
    }

    public void sendEmailChangedNotification(String oldEmail, String name, String newEmail) {
        SimpleMailMessage message = new SimpleMailMessage();

        message.setTo(oldEmail);
        message.setSubject("Your Rostly email address was changed");
        message.setText(
                "Hi " + name + ",\n\n" +
                        "Your account email was changed to " + newEmail + ".\n\n" +
                        "If you did not make this change, contact support immediately at rostly.platfrom@gmail.com"
        );

        mailSender.send(message);
    }

    public void sendExamInvitation(String to, String studentName, String examName,
                                   String sentByName, UUID invitationId, LocalDateTime expiresAt) {
        log.info("Sending exam invitation email to {}", to);

        String invitationLink = "http://localhost:5173/api/invitations/" + invitationId.toString();

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("You've been invited to take an exam on Rostly");
        message.setText(
                "Hi " + studentName + ",\n\n" +
                        sentByName + " has invited you to take the following exam:\n\n" +
                        "Exam: " + examName + "\n" +
                        "Invitation expires: " + expiresAt.format(
                        DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")) + "\n\n" +
                        "View your invitation:\n" + invitationLink + "\n\n" +
                        "If you did not expect this invitation, you can safely ignore this email."
        );

        mailSender.send(message);
        log.info("Exam invitation email sent to {}", to);
    }

    public void sendAdminApprovalReminder(String adminEmail, String teacherName, String teacherEmail) {
        log.info("Sending admin approval reminder to {} for teacher {}", adminEmail, teacherEmail);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(adminEmail);
        message.setSubject("Rostly: Teacher account pending approval");
        message.setText(
                "A new teacher account is waiting for your approval:\n\n" +
                        "Name:  " + teacherName + "\n" +
                        "Email: " + teacherEmail + "\n\n" +
                        "Log in to the admin panel to approve or reject the account.\n\n" +
                        "Rostly Team"
        );

        mailSender.send(message);
        log.info("Admin approval reminder sent to {}", adminEmail);
    }
}