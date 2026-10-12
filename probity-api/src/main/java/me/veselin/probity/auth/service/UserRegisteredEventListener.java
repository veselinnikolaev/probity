package me.veselin.probity.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.veselin.probity.auth.dto.UserRegisteredEvent;
import me.veselin.probity.auth.port.EmailVerificationPort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserRegisteredEventListener {

    private final EmailVerificationPort emailVerificationPort;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("ioExecutor")
    public void onUserRegistered(UserRegisteredEvent event) {
        try {
            emailVerificationPort.sendVerificationEmail(event.email());
        } catch (Exception e) {
            log.error("Failed to send verification email to {}: {}", event.email(), e.getMessage(), e);
        }
    }
}