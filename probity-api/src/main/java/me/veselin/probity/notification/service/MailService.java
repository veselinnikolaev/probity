package me.veselin.probity.notification.service;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import lombok.RequiredArgsConstructor;
import me.veselin.probity.auth.exception.EmailSendException;
import me.veselin.probity.notification.port.NotificationPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class MailService implements NotificationPort {

    private final SendGrid sendGrid;

    @Value("${sendgrid.from-email}")
    private String fromEmail;

    @Override
    public void sendEmail(String to, String subject, String content, String contentType) {
        Email from = new Email(fromEmail);
        Email recipient = new Email(to);
        Content body = new Content(contentType, content);
        Mail mail = new Mail(from, subject, recipient, body);

        Request request = new Request();
        try {
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());

            Response response = sendGrid.api(request);

            if (response.getStatusCode() >= 400) {
                throw new EmailSendException("SendGrid rejected the request: " + response.getBody());
            }
        } catch (IOException ex) {
            throw new EmailSendException("Failed to send email to " + to, ex);
        }
    }
}