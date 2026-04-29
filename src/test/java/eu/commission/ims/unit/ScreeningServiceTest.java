package eu.commission.ims.unit;

import eu.commission.ims.common.exception.BusinessException;
import eu.commission.ims.common.exception.ResourceNotFoundException;
import eu.commission.ims.module.resume.entity.Candidate;
import eu.commission.ims.module.resume.entity.Resume;
import eu.commission.ims.module.resume.entity.SubmissionStatus;
import eu.commission.ims.module.resume.repository.ResumeRepository;
import eu.commission.ims.module.screening.dto.DecisionRequest;
import eu.commission.ims.module.screening.dto.ScreeningRequest;
import eu.commission.ims.module.screening.dto.ScreeningResponse;
import eu.commission.ims.module.screening.entity.Screening;
import eu.commission.ims.module.screening.entity.ScreeningDecision;
import eu.commission.ims.module.screening.repository.ScreeningRepository;
import eu.commission.ims.module.screening.service.ScreeningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScreeningService Unit Tests")
class ScreeningServiceTest {

    @Mock private ScreeningRepository screeningRepository;
    @Mock private ResumeRepository resumeRepository;
    @InjectMocks private ScreeningService screeningService;

    private Candidate testCandidate;
    private Resume submittedResume;
    private Screening pendingScreening;

    @BeforeEach
    void setUp() {
        testCandidate = Candidate.builder()
                .id(1L).firstName("Emma").lastName("Müller")
                .email("emma@ec.europa.eu").nationality("DE")
                .phoneNumber("+491234567890").build();

        submittedResume = Resume.builder()
                .id(1L).candidate(testCandidate)
                .positionTitle("Policy Analyst").department("Legal")
                .yearsOfExperience(3).status(SubmissionStatus.SUBMITTED).build();

        pendingScreening = Screening.builder()
                .id(1L).resume(submittedResume)
                .screenerName("HR Officer").eligibilityScore(75)
                .decision(ScreeningDecision.PENDING).build();
    }

    // =========================================================
    // createScreening
    // =========================================================
    @Nested
    @DisplayName("createScreening()")
    class CreateScreeningTests {

        @Test
        @DisplayName("Should create screening and transition resume to UNDER_REVIEW for SUBMITTED resume")
        void createScreening_SubmittedResume_Succeeds() {
            ScreeningRequest req = buildRequest(1L, "HR Officer", 75);

            when(resumeRepository.findByIdWithCandidate(1L)).thenReturn(Optional.of(submittedResume));
            when(screeningRepository.existsByResumeId(1L)).thenReturn(false);
            when(screeningRepository.save(any())).thenReturn(pendingScreening);

            ScreeningResponse result = screeningService.createScreening(req);

            assertThat(result).isNotNull();
            assertThat(result.getDecision()).isEqualTo(ScreeningDecision.PENDING);
            assertThat(submittedResume.getStatus()).isEqualTo(SubmissionStatus.UNDER_REVIEW);
            verify(resumeRepository).save(submittedResume);
            verify(screeningRepository).save(any(Screening.class));
        }

        @Test
        @DisplayName("Should create screening when resume is already UNDER_REVIEW")
        void createScreening_UnderReviewResume_Succeeds() {
            submittedResume.setStatus(SubmissionStatus.UNDER_REVIEW);
            ScreeningRequest req = buildRequest(1L, "HR Officer", 80);

            when(resumeRepository.findByIdWithCandidate(1L)).thenReturn(Optional.of(submittedResume));
            when(screeningRepository.existsByResumeId(1L)).thenReturn(false);
            when(screeningRepository.save(any())).thenReturn(pendingScreening);

            ScreeningResponse result = screeningService.createScreening(req);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when resume does not exist")
        void createScreening_ResumeNotFound_ThrowsException() {
            ScreeningRequest req = buildRequest(99L, "HR Officer", 70);

            when(resumeRepository.findByIdWithCandidate(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> screeningService.createScreening(req))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Resume");
        }

        @Test
        @DisplayName("Should throw BusinessException when resume is ACCEPTED (wrong status)")
        void createScreening_AcceptedResume_ThrowsInvalidStatus() {
            submittedResume.setStatus(SubmissionStatus.ACCEPTED);
            ScreeningRequest req = buildRequest(1L, "HR Officer", 70);

            when(resumeRepository.findByIdWithCandidate(1L)).thenReturn(Optional.of(submittedResume));

            assertThatThrownBy(() -> screeningService.createScreening(req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("SUBMITTED or UNDER_REVIEW");
        }

        @Test
        @DisplayName("Should throw BusinessException when resume is REJECTED (wrong status)")
        void createScreening_RejectedResume_ThrowsInvalidStatus() {
            submittedResume.setStatus(SubmissionStatus.REJECTED);
            ScreeningRequest req = buildRequest(1L, "HR Officer", 70);

            when(resumeRepository.findByIdWithCandidate(1L)).thenReturn(Optional.of(submittedResume));

            assertThatThrownBy(() -> screeningService.createScreening(req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("SUBMITTED or UNDER_REVIEW");
        }

        @Test
        @DisplayName("Should throw BusinessException when screening already exists for resume")
        void createScreening_DuplicateScreening_ThrowsException() {
            ScreeningRequest req = buildRequest(1L, "HR Officer", 70);

            when(resumeRepository.findByIdWithCandidate(1L)).thenReturn(Optional.of(submittedResume));
            when(screeningRepository.existsByResumeId(1L)).thenReturn(true);

            assertThatThrownBy(() -> screeningService.createScreening(req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("already exists");
        }
    }

    // =========================================================
    // getScreeningById
    // =========================================================
    @Nested
    @DisplayName("getScreeningById()")
    class GetScreeningByIdTests {

        @Test
        @DisplayName("Should return screening response when found")
        void getScreeningById_Found_ReturnsResponse() {
            when(screeningRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(pendingScreening));

            ScreeningResponse result = screeningService.getScreeningById(1L);

            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getScreenerName()).isEqualTo("HR Officer");
            assertThat(result.getCandidateFullName()).isEqualTo("Emma Müller");
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when screening not found")
        void getScreeningById_NotFound_ThrowsException() {
            when(screeningRepository.findByIdWithDetails(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> screeningService.getScreeningById(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Screening")
                    .hasMessageContaining("99");
        }
    }

    // =========================================================
    // getScreeningByResumeId
    // =========================================================
    @Nested
    @DisplayName("getScreeningByResumeId()")
    class GetScreeningByResumeIdTests {

        @Test
        @DisplayName("Should return screening for given resume ID")
        void getScreeningByResumeId_Found_ReturnsResponse() {
            when(screeningRepository.findByResumeId(1L)).thenReturn(Optional.of(pendingScreening));

            ScreeningResponse result = screeningService.getScreeningByResumeId(1L);

            assertThat(result.getResumeId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when no screening exists for resume")
        void getScreeningByResumeId_NotFound_ThrowsException() {
            when(screeningRepository.findByResumeId(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> screeningService.getScreeningByResumeId(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Screening");
        }
    }

    // =========================================================
    // getPendingScreenings
    // =========================================================
    @Nested
    @DisplayName("getPendingScreenings()")
    class GetPendingScreeningsTests {

        @Test
        @DisplayName("Should return all screenings with PENDING decision")
        void getPendingScreenings_ReturnsList() {
            when(screeningRepository.findAllByDecision(ScreeningDecision.PENDING))
                    .thenReturn(List.of(pendingScreening));

            List<ScreeningResponse> result = screeningService.getPendingScreenings();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getDecision()).isEqualTo(ScreeningDecision.PENDING);
        }

        @Test
        @DisplayName("Should return empty list when no pending screenings exist")
        void getPendingScreenings_EmptyList_ReturnsEmpty() {
            when(screeningRepository.findAllByDecision(ScreeningDecision.PENDING)).thenReturn(List.of());

            List<ScreeningResponse> result = screeningService.getPendingScreenings();

            assertThat(result).isEmpty();
        }
    }

    // =========================================================
    // recordDecision
    // =========================================================
    @Nested
    @DisplayName("recordDecision()")
    class RecordDecisionTests {

        @Test
        @DisplayName("Should set resume to ACCEPTED when decision is PASSED")
        void recordDecision_Passed_PromotesResumeToAccepted() {
            DecisionRequest req = new DecisionRequest();
            req.setDecision(ScreeningDecision.PASSED);

            when(screeningRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(pendingScreening));
            when(screeningRepository.save(any())).thenReturn(pendingScreening);

            ScreeningResponse result = screeningService.recordDecision(1L, req);

            assertThat(pendingScreening.getDecision()).isEqualTo(ScreeningDecision.PASSED);
            assertThat(pendingScreening.getScreenedAt()).isNotNull();
            assertThat(submittedResume.getStatus()).isEqualTo(SubmissionStatus.ACCEPTED);
            verify(resumeRepository).save(submittedResume);
        }

        @Test
        @DisplayName("Should set resume to REJECTED when decision is FAILED")
        void recordDecision_Failed_MarkResumeAsRejected() {
            DecisionRequest req = new DecisionRequest();
            req.setDecision(ScreeningDecision.FAILED);

            when(screeningRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(pendingScreening));
            when(screeningRepository.save(any())).thenReturn(pendingScreening);

            screeningService.recordDecision(1L, req);

            assertThat(pendingScreening.getDecision()).isEqualTo(ScreeningDecision.FAILED);
            assertThat(submittedResume.getStatus()).isEqualTo(SubmissionStatus.REJECTED);
            verify(resumeRepository).save(submittedResume);
        }

        @Test
        @DisplayName("Should update notes when provided in decision request")
        void recordDecision_WithNotes_UpdatesNotes() {
            DecisionRequest req = new DecisionRequest();
            req.setDecision(ScreeningDecision.PASSED);
            req.setNotes("Excellent eligibility criteria met.");

            when(screeningRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(pendingScreening));
            when(screeningRepository.save(any())).thenReturn(pendingScreening);

            screeningService.recordDecision(1L, req);

            assertThat(pendingScreening.getNotes()).isEqualTo("Excellent eligibility criteria met.");
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when screening not found")
        void recordDecision_NotFound_ThrowsException() {
            DecisionRequest req = new DecisionRequest();
            req.setDecision(ScreeningDecision.PASSED);

            when(screeningRepository.findByIdWithDetails(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> screeningService.recordDecision(99L, req))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Screening");
        }

        @Test
        @DisplayName("Should throw BusinessException when decision is already recorded")
        void recordDecision_AlreadyDecided_ThrowsException() {
            pendingScreening.setDecision(ScreeningDecision.PASSED);
            DecisionRequest req = new DecisionRequest();
            req.setDecision(ScreeningDecision.FAILED);

            when(screeningRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(pendingScreening));

            assertThatThrownBy(() -> screeningService.recordDecision(1L, req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("already recorded");
        }
    }

    // =========================================================
    // Helpers
    // =========================================================

    private ScreeningRequest buildRequest(Long resumeId, String screenerName, int score) {
        ScreeningRequest req = new ScreeningRequest();
        req.setResumeId(resumeId);
        req.setScreenerName(screenerName);
        req.setEligibilityScore(score);
        return req;
    }
}
