package eu.commission.ims.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.commission.ims.module.interview.dto.InterviewRequest;
import eu.commission.ims.module.interview.dto.InterviewResponse;
import eu.commission.ims.module.interview.dto.RescheduleRequest;
import eu.commission.ims.module.interview.entity.InterviewType;
import eu.commission.ims.module.interview.service.InterviewService;
import eu.commission.ims.module.resume.dto.ResumeRequest;
import eu.commission.ims.module.resume.dto.ResumeResponse;
import eu.commission.ims.module.resume.service.ResumeService;
import eu.commission.ims.module.screening.dto.DecisionRequest;
import eu.commission.ims.module.screening.dto.ScreeningRequest;
import eu.commission.ims.module.screening.dto.ScreeningResponse;
import eu.commission.ims.module.screening.entity.ScreeningDecision;
import eu.commission.ims.module.screening.service.ScreeningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for InterviewController.
 * Loads full Spring Boot context with H2 in-memory database.
 * Each test runs in a rolled-back transaction.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Interview Controller Integration Tests")
class InterviewControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ResumeService resumeService;
    @Autowired private ScreeningService screeningService;
    @Autowired private InterviewService interviewService;

    /** Screening ID for a candidate who has PASSED screening — used as pre-condition for most tests. */
    private Long passedScreeningId;
    /** A scheduled interview ID available for complete/cancel/reschedule tests. */
    private Long scheduledInterviewId;
    /** Screening ID where decision is still PENDING — used to test scheduling guard. */
    private Long pendingScreeningId;

    @BeforeEach
    void setUp() {
        // Chain 1: resume → screening → PASSED decision
        ResumeResponse resume = resumeService.submitResume(buildResumeRequest("interview-it@ec.europa.eu"));
        ScreeningResponse screening = screeningService.createScreening(buildScreeningRequest(resume.getId()));
        DecisionRequest passed = new DecisionRequest();
        passed.setDecision(ScreeningDecision.PASSED);
        ScreeningResponse passedScreening = screeningService.recordDecision(screening.getId(), passed);
        passedScreeningId = passedScreening.getId();

        // Schedule one interview for complete/cancel/reschedule tests
        InterviewResponse interview = interviewService.scheduleInterview(buildInterviewRequest(passedScreeningId));
        scheduledInterviewId = interview.getId();

        // Chain 2: resume → screening with PENDING decision (for guard tests)
        ResumeResponse resume2 = resumeService.submitResume(buildResumeRequest("interview-pending@ec.europa.eu"));
        ScreeningResponse pendingScreening = screeningService.createScreening(buildScreeningRequest(resume2.getId()));
        pendingScreeningId = pendingScreening.getId();
    }

    // =========================================================
    // POST /api/v1/interviews
    // =========================================================

    @Test
    @WithMockUser(username = "interviewer", roles = "INTERVIEWER")
    @DisplayName("POST /api/v1/interviews — Should return 201 for a PASSED screening")
    void scheduleInterview_ValidRequest_Returns201() throws Exception {
        // Use chain 2: promote it to PASSED first so we have a fresh screening to schedule against
        DecisionRequest passed = new DecisionRequest();
        passed.setDecision(ScreeningDecision.PASSED);
        ScreeningResponse promoted = screeningService.recordDecision(pendingScreeningId, passed);

        InterviewRequest req = buildInterviewRequest(promoted.getId());

        mockMvc.perform(post("/api/v1/interviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.interviewerName").value("Dr. Müller"))
                .andExpect(jsonPath("$.data.id").isNumber());
    }

    @Test
    @WithMockUser(username = "interviewer", roles = "INTERVIEWER")
    @DisplayName("POST /api/v1/interviews — Should return 400 for missing required fields")
    void scheduleInterview_MissingFields_Returns400() throws Exception {
        InterviewRequest req = new InterviewRequest(); // empty

        mockMvc.perform(post("/api/v1/interviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    @WithMockUser(username = "interviewer", roles = "INTERVIEWER")
    @DisplayName("POST /api/v1/interviews — Should return 404 when screening does not exist")
    void scheduleInterview_ScreeningNotFound_Returns404() throws Exception {
        InterviewRequest req = buildInterviewRequest(99999L);

        mockMvc.perform(post("/api/v1/interviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "interviewer", roles = "INTERVIEWER")
    @DisplayName("POST /api/v1/interviews — Should return 409 when screening is not PASSED")
    void scheduleInterview_ScreeningNotPassed_Returns409() throws Exception {
        InterviewRequest req = buildInterviewRequest(pendingScreeningId);

        mockMvc.perform(post("/api/v1/interviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SCREENING_NOT_PASSED"));
    }

    @Test
    @DisplayName("POST /api/v1/interviews — Should return 401 when unauthenticated")
    void scheduleInterview_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(post("/api/v1/interviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildInterviewRequest(passedScreeningId))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "recruiter", roles = "RECRUITER")
    @DisplayName("POST /api/v1/interviews — Should return 403 for RECRUITER role")
    void scheduleInterview_WrongRole_Returns403() throws Exception {
        mockMvc.perform(post("/api/v1/interviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildInterviewRequest(passedScreeningId))))
                .andExpect(status().isForbidden());
    }

    // =========================================================
    // GET /api/v1/interviews/{id}
    // =========================================================

    @Test
    @WithMockUser(username = "interviewer", roles = "INTERVIEWER")
    @DisplayName("GET /api/v1/interviews/{id} — Should return 200 with interview data")
    void getInterview_Found_Returns200() throws Exception {
        mockMvc.perform(get("/api/v1/interviews/{id}", scheduledInterviewId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(scheduledInterviewId))
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"));
    }

    @Test
    @WithMockUser(username = "interviewer", roles = "INTERVIEWER")
    @DisplayName("GET /api/v1/interviews/{id} — Should return 404 for unknown ID")
    void getInterview_NotFound_Returns404() throws Exception {
        mockMvc.perform(get("/api/v1/interviews/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // =========================================================
    // GET /api/v1/interviews/upcoming
    // =========================================================

    @Test
    @WithMockUser(username = "interviewer", roles = "INTERVIEWER")
    @DisplayName("GET /api/v1/interviews/upcoming — Should return 200 with list")
    void getUpcoming_Returns200() throws Exception {
        mockMvc.perform(get("/api/v1/interviews/upcoming"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    // =========================================================
    // GET /api/v1/interviews?status=...
    // =========================================================

    @Test
    @WithMockUser(username = "interviewer", roles = "INTERVIEWER")
    @DisplayName("GET /api/v1/interviews?status=SCHEDULED — Should return scheduled interviews")
    void getByStatus_Scheduled_Returns200() throws Exception {
        mockMvc.perform(get("/api/v1/interviews").param("status", "SCHEDULED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @WithMockUser(username = "interviewer", roles = "INTERVIEWER")
    @DisplayName("GET /api/v1/interviews — Should default to SCHEDULED status when param is omitted")
    void getByStatus_NoParam_DefaultsToScheduled() throws Exception {
        mockMvc.perform(get("/api/v1/interviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    // =========================================================
    // PUT /api/v1/interviews/{id}/reschedule
    // =========================================================

    @Test
    @WithMockUser(username = "interviewer", roles = "INTERVIEWER")
    @DisplayName("PUT /api/v1/interviews/{id}/reschedule — Should return 200 and RESCHEDULED status")
    void rescheduleInterview_Scheduled_Returns200() throws Exception {
        RescheduleRequest req = new RescheduleRequest();
        req.setScheduledAt(LocalDateTime.now().plusDays(14));
        req.setDurationMinutes(90);
        req.setLocation("EC Brussels HQ");

        mockMvc.perform(put("/api/v1/interviews/{id}/reschedule", scheduledInterviewId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("RESCHEDULED"));
    }

    @Test
    @WithMockUser(username = "interviewer", roles = "INTERVIEWER")
    @DisplayName("PUT /api/v1/interviews/{id}/reschedule — Should return 409 for COMPLETED interview")
    void rescheduleInterview_Completed_Returns409() throws Exception {
        interviewService.completeInterview(scheduledInterviewId);

        RescheduleRequest req = new RescheduleRequest();
        req.setScheduledAt(LocalDateTime.now().plusDays(14));

        mockMvc.perform(put("/api/v1/interviews/{id}/reschedule", scheduledInterviewId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    @WithMockUser(username = "interviewer", roles = "INTERVIEWER")
    @DisplayName("PUT /api/v1/interviews/{id}/reschedule — Should return 409 for CANCELLED interview")
    void rescheduleInterview_Cancelled_Returns409() throws Exception {
        interviewService.cancelInterview(scheduledInterviewId, "Test cancellation");

        RescheduleRequest req = new RescheduleRequest();
        req.setScheduledAt(LocalDateTime.now().plusDays(14));

        mockMvc.perform(put("/api/v1/interviews/{id}/reschedule", scheduledInterviewId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
    }

    // =========================================================
    // PUT /api/v1/interviews/{id}/complete
    // =========================================================

    @Test
    @WithMockUser(username = "interviewer", roles = "INTERVIEWER")
    @DisplayName("PUT /api/v1/interviews/{id}/complete — Should return 200 and COMPLETED status")
    void completeInterview_Scheduled_Returns200() throws Exception {
        mockMvc.perform(put("/api/v1/interviews/{id}/complete", scheduledInterviewId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.completedAt").isNotEmpty());
    }

    @Test
    @WithMockUser(username = "interviewer", roles = "INTERVIEWER")
    @DisplayName("PUT /api/v1/interviews/{id}/complete — Should return 409 when already COMPLETED")
    void completeInterview_AlreadyCompleted_Returns409() throws Exception {
        interviewService.completeInterview(scheduledInterviewId);

        mockMvc.perform(put("/api/v1/interviews/{id}/complete", scheduledInterviewId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ALREADY_COMPLETED"));
    }

    // =========================================================
    // PUT /api/v1/interviews/{id}/cancel
    // =========================================================

    @Test
    @WithMockUser(username = "interviewer", roles = "INTERVIEWER")
    @DisplayName("PUT /api/v1/interviews/{id}/cancel — Should return 200 and CANCELLED status")
    void cancelInterview_Scheduled_Returns200() throws Exception {
        mockMvc.perform(put("/api/v1/interviews/{id}/cancel", scheduledInterviewId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Candidate withdrew"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @WithMockUser(username = "interviewer", roles = "INTERVIEWER")
    @DisplayName("PUT /api/v1/interviews/{id}/cancel — Should return 409 for COMPLETED interview")
    void cancelInterview_Completed_Returns409() throws Exception {
        interviewService.completeInterview(scheduledInterviewId);

        mockMvc.perform(put("/api/v1/interviews/{id}/cancel", scheduledInterviewId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Too late"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
    }

    // =========================================================
    // Helpers
    // =========================================================

    private ResumeRequest buildResumeRequest(String email) {
        ResumeRequest req = new ResumeRequest();
        req.setFirstName("Test");
        req.setLastName("Candidate");
        req.setEmail(email);
        req.setNationality("FR");
        req.setPhoneNumber("+3300000000");
        req.setPositionTitle("Technical Officer");
        req.setDepartment("IT");
        req.setYearsOfExperience(5);
        req.setCoverLetter("Interview IT test cover letter");
        return req;
    }

    private ScreeningRequest buildScreeningRequest(Long resumeId) {
        ScreeningRequest req = new ScreeningRequest();
        req.setResumeId(resumeId);
        req.setScreenerName("HR Officer");
        req.setEligibilityScore(85);
        return req;
    }

    private InterviewRequest buildInterviewRequest(Long screeningId) {
        InterviewRequest req = new InterviewRequest();
        req.setScreeningId(screeningId);
        req.setInterviewerName("Dr. Müller");
        req.setInterviewerEmail("muller@ec.europa.eu");
        req.setScheduledAt(LocalDateTime.now().plusDays(7));
        req.setDurationMinutes(60);
        req.setType(InterviewType.ONLINE);
        return req;
    }
}
