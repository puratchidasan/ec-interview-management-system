package eu.commission.ims.unit;

import eu.commission.ims.common.exception.BusinessException;
import eu.commission.ims.common.exception.ResourceNotFoundException;
import eu.commission.ims.module.interview.dto.InterviewRequest;
import eu.commission.ims.module.interview.dto.InterviewResponse;
import eu.commission.ims.module.interview.dto.RescheduleRequest;
import eu.commission.ims.module.interview.entity.Interview;
import eu.commission.ims.module.interview.entity.InterviewStatus;
import eu.commission.ims.module.interview.entity.InterviewType;
import eu.commission.ims.module.interview.repository.InterviewRepository;
import eu.commission.ims.module.interview.service.InterviewService;
import eu.commission.ims.module.resume.entity.Candidate;
import eu.commission.ims.module.resume.entity.Resume;
import eu.commission.ims.module.resume.entity.SubmissionStatus;
import eu.commission.ims.module.screening.entity.Screening;
import eu.commission.ims.module.screening.entity.ScreeningDecision;
import eu.commission.ims.module.screening.repository.ScreeningRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InterviewService Unit Tests")
class InterviewServiceTest {

    @Mock private InterviewRepository interviewRepository;
    @Mock private ScreeningRepository screeningRepository;
    @InjectMocks private InterviewService interviewService;

    private Screening passedScreening;
    private Interview scheduledInterview;

    @BeforeEach
    void setUp() {
        Candidate c = Candidate.builder().id(1L).firstName("Carla").lastName("Rossi")
                .email("carla@ec.europa.eu").nationality("IT").build();
        Resume r = Resume.builder().id(1L).candidate(c).positionTitle("Policy Officer")
                .department("External").yearsOfExperience(5).status(SubmissionStatus.ACCEPTED).build();

        passedScreening = Screening.builder().id(1L).resume(r)
                .screenerName("HR").eligibilityScore(90)
                .decision(ScreeningDecision.PASSED).build();

        scheduledInterview = Interview.builder()
                .id(1L).screening(passedScreening)
                .interviewerName("Dr. Weber").interviewerEmail("weber@ec.europa.eu")
                .scheduledAt(LocalDateTime.now().plusDays(5)).durationMinutes(60)
                .type(InterviewType.ONLINE).status(InterviewStatus.SCHEDULED).build();
    }

    @Nested
    @DisplayName("scheduleInterview()")
    class ScheduleTests {

        @Test
        @DisplayName("Should schedule interview for PASSED screening")
        void scheduleInterview_PassedScreening_Succeeds() {
            InterviewRequest req = new InterviewRequest();
            req.setScreeningId(1L);
            req.setInterviewerName("Dr. Weber");
            req.setInterviewerEmail("weber@ec.europa.eu");
            req.setScheduledAt(LocalDateTime.now().plusDays(5));
            req.setDurationMinutes(60);
            req.setType(InterviewType.ONLINE);

            when(screeningRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(passedScreening));
            when(interviewRepository.save(any())).thenReturn(scheduledInterview);

            InterviewResponse result = interviewService.scheduleInterview(req);

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(InterviewStatus.SCHEDULED);
        }

        @Test
        @DisplayName("Should throw BusinessException when screening is PENDING")
        void scheduleInterview_NotPassedScreening_ThrowsException() {
            passedScreening.setDecision(ScreeningDecision.PENDING);
            InterviewRequest req = new InterviewRequest();
            req.setScreeningId(1L);

            when(screeningRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(passedScreening));

            assertThatThrownBy(() -> interviewService.scheduleInterview(req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("PASSED");
        }

        @Test
        @DisplayName("Should throw BusinessException when screening is FAILED")
        void scheduleInterview_FailedScreening_ThrowsException() {
            passedScreening.setDecision(ScreeningDecision.FAILED);
            InterviewRequest req = new InterviewRequest();
            req.setScreeningId(1L);

            when(screeningRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(passedScreening));

            assertThatThrownBy(() -> interviewService.scheduleInterview(req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Decision: FAILED");
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when screening not found")
        void scheduleInterview_ScreeningNotFound_ThrowsException() {
            InterviewRequest req = new InterviewRequest();
            req.setScreeningId(99L);

            when(screeningRepository.findByIdWithDetails(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> interviewService.scheduleInterview(req))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Screening");
        }
    }

    @Nested
    @DisplayName("completeInterview()")
    class CompleteTests {

        @Test
        @DisplayName("Should mark SCHEDULED interview as COMPLETED")
        void completeInterview_Scheduled_CompletesSuccessfully() {
            when(interviewRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(scheduledInterview));
            when(interviewRepository.save(any())).thenReturn(scheduledInterview);

            InterviewResponse result = interviewService.completeInterview(1L);

            assertThat(scheduledInterview.getStatus()).isEqualTo(InterviewStatus.COMPLETED);
            assertThat(scheduledInterview.getCompletedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should throw BusinessException when interview is already COMPLETED")
        void completeInterview_AlreadyCompleted_ThrowsException() {
            scheduledInterview.setStatus(InterviewStatus.COMPLETED);
            when(interviewRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(scheduledInterview));

            assertThatThrownBy(() -> interviewService.completeInterview(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("already completed");
        }

        @Test
        @DisplayName("Should throw BusinessException when interview is CANCELLED")
        void completeInterview_Cancelled_ThrowsException() {
            scheduledInterview.setStatus(InterviewStatus.CANCELLED);
            when(interviewRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(scheduledInterview));

            assertThatThrownBy(() -> interviewService.completeInterview(1L))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("cancelInterview()")
    class CancelTests {

        @Test
        @DisplayName("Should cancel a SCHEDULED interview")
        void cancelInterview_Scheduled_CancelsSuccessfully() {
            when(interviewRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(scheduledInterview));
            when(interviewRepository.save(any())).thenReturn(scheduledInterview);

            interviewService.cancelInterview(1L, "Candidate withdrew");

            assertThat(scheduledInterview.getStatus()).isEqualTo(InterviewStatus.CANCELLED);
            assertThat(scheduledInterview.getCancellationReason()).isEqualTo("Candidate withdrew");
        }

        @Test
        @DisplayName("Should throw BusinessException when trying to cancel a COMPLETED interview")
        void cancelInterview_Completed_ThrowsException() {
            scheduledInterview.setStatus(InterviewStatus.COMPLETED);
            when(interviewRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(scheduledInterview));

            assertThatThrownBy(() -> interviewService.cancelInterview(1L, "test"))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("getInterviewById()")
    class GetByIdTests {

        @Test
        @DisplayName("Should throw ResourceNotFoundException for unknown ID")
        void getInterviewById_NotFound_ThrowsException() {
            when(interviewRepository.findByIdWithDetails(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> interviewService.getInterviewById(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Interview");
        }

        @Test
        @DisplayName("Should return interview response when found")
        void getInterviewById_Found_ReturnsResponse() {
            when(interviewRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(scheduledInterview));

            InterviewResponse result = interviewService.getInterviewById(1L);

            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getStatus()).isEqualTo(InterviewStatus.SCHEDULED);
        }
    }

    @Nested
    @DisplayName("rescheduleInterview()")
    class RescheduleTests {

        @Test
        @DisplayName("Should reschedule a SCHEDULED interview and set status to RESCHEDULED")
        void rescheduleInterview_Scheduled_SetsRescheduledStatus() {
            RescheduleRequest req = new RescheduleRequest();
            req.setScheduledAt(LocalDateTime.now().plusDays(14));
            req.setDurationMinutes(90);
            req.setLocation("Brussels HQ");

            when(interviewRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(scheduledInterview));
            when(interviewRepository.save(any())).thenReturn(scheduledInterview);

            InterviewResponse result = interviewService.rescheduleInterview(1L, req);

            assertThat(scheduledInterview.getStatus()).isEqualTo(InterviewStatus.RESCHEDULED);
            assertThat(scheduledInterview.getDurationMinutes()).isEqualTo(90);
            assertThat(scheduledInterview.getLocation()).isEqualTo("Brussels HQ");
        }

        @Test
        @DisplayName("Should throw BusinessException when rescheduling a COMPLETED interview")
        void rescheduleInterview_Completed_ThrowsException() {
            scheduledInterview.setStatus(InterviewStatus.COMPLETED);
            RescheduleRequest req = new RescheduleRequest();
            req.setScheduledAt(LocalDateTime.now().plusDays(14));

            when(interviewRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(scheduledInterview));

            assertThatThrownBy(() -> interviewService.rescheduleInterview(1L, req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Cannot reschedule");
        }

        @Test
        @DisplayName("Should throw BusinessException when rescheduling a CANCELLED interview")
        void rescheduleInterview_Cancelled_ThrowsException() {
            scheduledInterview.setStatus(InterviewStatus.CANCELLED);
            RescheduleRequest req = new RescheduleRequest();
            req.setScheduledAt(LocalDateTime.now().plusDays(14));

            when(interviewRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(scheduledInterview));

            assertThatThrownBy(() -> interviewService.rescheduleInterview(1L, req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Cannot reschedule");
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException for unknown interview ID")
        void rescheduleInterview_NotFound_ThrowsException() {
            when(interviewRepository.findByIdWithDetails(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> interviewService.rescheduleInterview(99L, new RescheduleRequest()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Interview");
        }
    }

    @Nested
    @DisplayName("getInterviewsByStatus()")
    class GetByStatusTests {

        @Test
        @DisplayName("Should return interviews matching the given status")
        void getInterviewsByStatus_ReturnsMatchingList() {
            when(interviewRepository.findAllByStatus(InterviewStatus.SCHEDULED))
                    .thenReturn(List.of(scheduledInterview));

            List<InterviewResponse> result = interviewService.getInterviewsByStatus(InterviewStatus.SCHEDULED);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStatus()).isEqualTo(InterviewStatus.SCHEDULED);
        }

        @Test
        @DisplayName("Should return empty list when no interviews match status")
        void getInterviewsByStatus_EmptyResult() {
            when(interviewRepository.findAllByStatus(InterviewStatus.CANCELLED)).thenReturn(List.of());

            List<InterviewResponse> result = interviewService.getInterviewsByStatus(InterviewStatus.CANCELLED);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getUpcomingInterviews()")
    class GetUpcomingTests {

        @Test
        @DisplayName("Should return interviews scheduled within the next 30 days")
        void getUpcomingInterviews_ReturnsUpcomingList() {
            when(interviewRepository.findUpcoming(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(List.of(scheduledInterview));

            List<InterviewResponse> result = interviewService.getUpcomingInterviews();

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("Should return empty list when no upcoming interviews exist")
        void getUpcomingInterviews_EmptyResult() {
            when(interviewRepository.findUpcoming(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(List.of());

            List<InterviewResponse> result = interviewService.getUpcomingInterviews();

            assertThat(result).isEmpty();
        }
    }
}
