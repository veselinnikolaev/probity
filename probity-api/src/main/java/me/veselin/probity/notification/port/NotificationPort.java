package me.veselin.probity.notification.port;

public interface NotificationPort {
    void sendEmail(String to, String subject, String body, String contentType);
}
