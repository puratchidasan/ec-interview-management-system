package eu.commission.ims;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * European External Action Service — Interview Management System
 * <p>
 * Entry point for the DevSecOps showcase application.
 * Manages the full interview lifecycle:
 * Resume Submission → Screening → Technical Interview → Feedback
 */
@SpringBootApplication
@EnableJpaAuditing
public class InterviewManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(InterviewManagementApplication.class, args);
    }
}
