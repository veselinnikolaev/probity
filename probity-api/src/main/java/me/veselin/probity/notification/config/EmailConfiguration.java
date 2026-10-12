package me.veselin.probity.notification.config;

import com.sendgrid.SendGrid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
/**
 * SendGrid email service configuration for transactional email delivery.
 */
public class EmailConfiguration {

    @Value("${sendgrid.api-key}")
    private String apiKey;

    @Bean
    public SendGrid sendGrid() {
        return new SendGrid(apiKey);
    }
}
