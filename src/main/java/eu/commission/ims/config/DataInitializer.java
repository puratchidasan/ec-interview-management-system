package eu.commission.ims.config;

import eu.commission.ims.module.resume.entity.Candidate;
import eu.commission.ims.module.resume.entity.Resume;
import eu.commission.ims.module.resume.entity.SubmissionStatus;
import eu.commission.ims.module.resume.repository.CandidateRepository;
import eu.commission.ims.module.resume.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Seeds sample data for dev and test profiles.
 * Not active in production.
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class DataInitializer {

        @Bean
        @Profile({ "dev", "test" })
        public CommandLineRunner seedData(CandidateRepository candidateRepository,
                        ResumeRepository resumeRepository) {
                return args -> {
                        if (candidateRepository.count() > 0) {
                                log.info("[DataInitializer] Data already seeded — skipping.");
                                return;
                        }

                        log.info("[DataInitializer] Seeding sample candidate and resume data...");

                        // Candidate 1 — submitted resume
                        Candidate alice = Candidate.builder()
                                        .firstName("Alice")
                                        .lastName("Fontaine")
                                        .email("alice.fontaine@example.eu")
                                        .nationality("FR")
                                        .phoneNumber("+33 6 12 34 56 78")
                                        .build();
                        alice = candidateRepository.save(alice);

                        Resume r1 = Resume.builder()
                                        .candidate(alice)
                                        .positionTitle("Senior Software Engineer")
                                        .department("Digital Infrastructure")
                                        .yearsOfExperience(7)
                                        .linkedInProfile("https://linkedin.com/in/alice-fontaine")
                                        .coverLetter("I am highly motivated to contribute to the European External Action Service's digital agenda.")
                                        .status(SubmissionStatus.SUBMITTED)
                                        .build();
                        resumeRepository.save(r1);

                        // Candidate 2 — draft
                        Candidate bob = Candidate.builder()
                                        .firstName("Bob")
                                        .lastName("Müller")
                                        .email("bob.mueller@example.eu")
                                        .nationality("DE")
                                        .phoneNumber("+49 30 1234 5678")
                                        .build();
                        bob = candidateRepository.save(bob);

                        Resume r2 = Resume.builder()
                                        .candidate(bob)
                                        .positionTitle("Data Analyst")
                                        .department("Statistics & Analytics")
                                        .yearsOfExperience(3)
                                        .linkedInProfile("https://linkedin.com/in/bob-mueller")
                                        .coverLetter("Seeking to apply analytical skills in a European context.")
                                        .status(SubmissionStatus.DRAFT)
                                        .build();
                        resumeRepository.save(r2);

                        // Candidate 3 — under review
                        Candidate carla = Candidate.builder()
                                        .firstName("Carla")
                                        .lastName("Rossi")
                                        .email("carla.rossi@example.eu")
                                        .nationality("IT")
                                        .phoneNumber("+39 02 1234 5678")
                                        .build();
                        carla = candidateRepository.save(carla);

                        Resume r3 = Resume.builder()
                                        .candidate(carla)
                                        .positionTitle("Policy Officer")
                                        .department("External Relations")
                                        .yearsOfExperience(5)
                                        .linkedInProfile("https://linkedin.com/in/carla-rossi")
                                        .coverLetter("Dedicated to advancing EU policy frameworks across member states.")
                                        .status(SubmissionStatus.UNDER_REVIEW)
                                        .build();
                        resumeRepository.save(r3);

                        log.info("[DataInitializer] Seeded {} candidates and {} resumes.",
                                        candidateRepository.count(), resumeRepository.count());
                };
        }
}
