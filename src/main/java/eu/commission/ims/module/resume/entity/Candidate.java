package eu.commission.ims.module.resume.entity;

import eu.commission.ims.common.audit.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a job applicant (candidate) in the European External Action
 * Service recruitment process.
 */
@Entity
@Table(name = "candidates", uniqueConstraints = {
        @UniqueConstraint(columnNames = "email", name = "uk_candidate_email")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Candidate extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "nationality", nullable = false, length = 2)
    private String nationality;

    @Column(name = "phone_number", length = 30)
    private String phoneNumber;

    @OneToMany(mappedBy = "candidate", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Resume> resumes = new ArrayList<>();
}
