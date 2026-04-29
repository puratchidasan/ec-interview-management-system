package eu.commission.ims.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.commission.ims.module.feedback.dto.FeedbackRequest;
import eu.commission.ims.module.feedback.dto.FeedbackResponse;
import eu.commission.ims.module.feedback.dto.FinalizeRequest;
import eu.commission.ims.module.feedback.entity.FinalDecision;
import eu.commission.ims.module.feedback.service.FeedbackService;
import eu.commission.ims.module.interview.dto.InterviewRequest;
import eu.commission.ims.module.interview.dto.InterviewResponse;
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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for FeedbackController.
 * Loads full Spring Boot context with H2 in-memory database.
 * Each test runs in a rolled-back transaction.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Feedback Controller Integration Tests")
class FeedbackControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ResumeService resumeService;
    @Autowired private ScreeningService screeningService;
    @Autowired private InterviewService interviewService;
    @Autowired private FeedbackService feedbackService;

    /** A COMPLETED interview ready for feedback submission. */
    private Long completedInterviewId;
    /** The candidate ID associated with the completed interview chain. */
    private Long candidateId;
    /** A SCHEDULED (not yet completed) interview — used to test the guard. */
    private Long scheduledInterviewId;
    /** A feedback ID for finalize/re-finalize tests. */
    private Long feedbackId;

    @BeforeEach
    void setUp() {
        // Chain 1: resume → screening → PASSED → interview → COMPLETED → feedback (draft)
        ResumeResponse resume = resumeService.submitResume(buildResumeRequest("feedback-it@ec.europa.eu"));
        candidateId = resume.getCandidateId();

        ScreeningResponse screening = screeningService.createScreening(buildScreeningRequest(resume.getId()));
        DecisionRequest passed = new DecisionRequest();
        passed.setDecision(ScreeningDecision.PASSED);
        ScreeningResponse passedScreening = screeningService.recordDecision(screening.getId(), passed);

        InterviewResponse interview = interviewService.scheduleInterview(buildInterviewRequest(passedScreening.getId()));
        interviewService.completeInterview(interview.getId());
        completedInterviewId = interview.getId();

        FeedbackResponse feedback = feedbackService.submitFeedback(buildFeedbackRequest(completedInterviewId));
        feedbackId = feedback.getId();

        // Chain 2: resume → screening → PASSED → interview (stays SCHEDULED for guard test)
        ResumeResponse resume2 = resumeService.submitResume(buildResumeRequest("feedback-scheduled@ec.europa.eu"));
        ScreeningResponse screening2 = screeningService.createScreening(buildScreeningRequest(resume2.getId()));
        DecisionRequest passed2 = new DecisionRequest();
        passed2.setDecision(ScreeningDecision.PASSED);
        ScreeningResponse passedScreening2 = screeningService.recordDecision(screening2.getId(), passed2);
        InterviewResponse interview2 = interviewService.scheduleInterview(buildInterviewRequest(passedScreening2.getId()));
        scheduledInterviewId = interview2.getId();
    }

    // =========================================================
    // POST /api/v1/feedback
    // =========================================================

    @Test
    @WithMockUser(username = "interviewer", roles = "INTERVIEWER")
    @DisplayName("POST /api/v1/feedback — Should return 201 for a COMPLETED interview")
    void submitFeedback_ValidRequest_Returns201() throws Exception {
        FeedbackRequest req = buildFeedbackRequest(scheduledInterviewId);
        // First complete the scheduled interview so we can submit feedback for it
        interviewService.completeInterview(scheduledInterviewId);

        mockMvc.perform(post("/api/v1/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.technicalScore").value(80))
                .andExpect(jsonPath("$.data.isFinalized").value(false))
                .andExpect(jsonPath("$.data.overallScore").isNumber())
                .andExpect(jsonPath("$.data.id").isNumber());
    }

    @Test
    @WithMockUser(username = "interviewer", roles = "INTERVIEWER")
    @DisplayName("POST /api/v1/feedback — Should return 400 for missing required fields")
    void submitFeedback_MissingFields_Returns400() throws Exception {
        FeedbackRequest req = new FeedbackRequest(); // empty

        mockMvc.perform(post("/api/v1/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    @WithMockUser(username = "interviewer", roles = "INTERVIEWER")
    @DisplayName("POST /api/v1/feedback — Should return 404 when interview does not exist")
    void submitFeedback_InterviewNotFound_Returns404() throws Exception {
        FeedbackRequest req = buildFeedbackRequest(99999L);

        mockMvc.perform(post("/api/v1/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "interviewer", roles = "INTERVIEWER")
    @DisplayName("POST /api/v1/feedback — Should return 409 when interview is not COMPLETED")
    void submitFeedback_InterviewNotCompleted_Returns409() throws Exception {
        FeedbackRequest req = buildFeedbackRequest(scheduledInterviewId);

        mockMvc.perform(post("/api/v1/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INTERVIEW_NOT_COMPLETED"));
    }

    @Test
    @WithMockUser(username = "interviewer", roles = "INTERVIEWER")
    @DisplayName("POST /api/v1/feedback — Should return 409 for duplicate feedback submission")
    void submitFeedback_Duplicate_Returns409() throws Exception {
        // feedbackId already exists for completedInterviewId — attempt another
        FeedbackRequest req = buildFeedbackRequest(completedInterviewId);

        mockMvc.perform(post("/api/v1/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_FEEDBACK"));
    }

    @Test
    @DisplayName("POST /api/v1/feedback — Should return 401 when unauthenticated")
    void submitFeedback_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(post("/api/v1/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildFeedbackRequest(completedInterviewId))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "recruiter", roles = "RECRUITER")
    @DisplayName("POST /api/v1/feedback — Should return 403 for RECRUITER role")
    void submitFeedback_WrongRole_Returns403() throws Exception {
        mockMvc.perform(post("/api/v1/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildFeedbackRequest(completedInterviewId))))
                .andExpect(status().isForbidden());
    }

    // =========================================================
    // GET /api/v1/feedback/{id}
    // =========================================================

    @Test
    @WithMockUser(username = "interviewer", roles = "INTERVIEWER")
    @DisplayName("GET /api/v1/feedback/{id} — Should return 200 with feedback data")
    void getFeedback_Found_Returns200() throws Exception {
        mockMvc.perform(get("/api/v1/feedback/{id}", feedbackId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(feedbackId))
                .andExpect(jsonPath("$.data.isFinalized").value(false));
    }

    @Test
    @WithMockUser(username = "interviewer", roles = "INTERVIEWER")
    @DisplayName("GET /api/v1/feedback/{id} — Should return 404 for unknown ID")
    void getFeedback_NotFound_Returns404() throws Exception {
        mockMvc.perform(get("/api/v1/feedback/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // =========================================================
    // GET /api/v1/feedback/interview/{interviewId}
    // =========================================================

    @Test
    @WithMockUser(username = "interviewer", roles = "INTERVIEWER")
    @DisplayName("GET /api/v1/feedback/interview/{interviewId} — Should return 200 with feedback")
    void getByInterview_Found_Returns200() throws Exception {
        mockMvc.perform(get("/api/v1/feedback/interview/{interviewId}", completedInterviewId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.interviewId").value(completedInterviewId));
    }

    @Test
    @WithMockUser(username = "interviewer", roles = "INTERVIEWER")
    @DisplayName("GET /api/v1/feedback/interview/{interviewId} — Should return 404 for interview with no feedback")
    void getByInterview_NotFound_Returns404() throws Exception {
        mockMvc.perform(get("/api/v1/feedback/interview/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // =========================================================
    // GET /api/v1/feedback/candidate/{candidateId}/summary
    // =========================================================

    @Test
    @WithMockUser(username = "interviewer", roles = "INTERVIEWER")
    @DisplayName("GET /api/v1/feedback/candidate/{candidateId}/summary — Should return list of feedbacks")
    void getCandidateSummary_Returns200() throws Exception {
        mockMvc.perform(get("/api/v1/feedback/candidate/{candidateId}/summary", candidateId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @WithMockUser(username = "interviewer", roles = "INTERVIEWER")
    @DisplayName("GET /api/v1/feedback/candidate/{candidateId}/summary — Should return empty list for unknown candidate")
    void getCandidateSummary_UnknownCandidate_ReturnsEmpty() throws Exception {
        mockMvc.perform(get("/api/v1/feedback/candidate/99999/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    // =========================================================
    // GET /api/v1/feedback/pending
    // =========================================================

    @Test
    @WithMockUser(username = "interviewer", roles = "INTERVIEWER")
    @DisplayName("GET /api/v1/feedback/pending — Should return list of unfinalized feedbacks")
    void getPendingFeedbacks_Returns200() throws Exception {
        mockMvc.perform(get("/api/v1/feedback/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].isFinalized").value(false));
    }

    // =========================================================
    // PUT /api/v1/feedback/{id}/finalize
    // =========================================================

    @Test
    @WithMockUser(username = "interviewer", roles = "INTERVIEWER")
    @DisplayName("PUT /api/v1/feedback/{id}/finalize — Should return 200 with HIRED decision")
    void finalizeFeedback_Hired_Returns200() throws Exception {
        FinalizeRequest req = new FinalizeRequest();
        req.setFinalDecision(FinalDecision.HIRED);

        mockMvc.perform(put("/api/v1/feedback/{id}/finalize", feedbackId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.finalDecision").value("HIRED"))
                .andExpect(jsonPath("$.data.isFinalized").value(true))
                .andExpect(jsonPath("$.data.finalizedAt").isNotEmpty());
    }

    @Test
    @WithMockUser(username = "interviewer", roles = "INTERVIEWER")
    @DisplayName("PUT /api/v1/feedback/{id}/finalize — Should return 200 with REJECTED decision")
    void finalizeFeedback_Rejected_Returns200() throws Exception {
        FinalizeRequest req = new FinalizeRequest();
        req.setFinalDecision(FinalDecision.REJECTED);

        mockMvc.perform(put("/api/v1/feedback/{id}/finalize", feedbackId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.finalDecision").value("REJECTED"))
                .andExpect(jsonPath("$.data.isFinalized").value(true));
    }

    @Test
    @WithMockUser(username = "interviewer", roles = "INTERVIEWER")
    @DisplayName("PUT /api/v1/feedback/{id}/finalize — Should return 200 with ON_HOLD decision")
    void finalizeFeedback_OnHold_Returns200() throws Exception {
        FinalizeRequest req = new FinalizeRequest();
        req.setFinalDecision(FinalDecision.ON_HOLD);

        mockMvc.perform(put("/api/v1/feedback/{id}/finalize", feedbackId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.finalDecision").value("ON_HOLD"));
    }

    @Test
    @WithMockUser(username = "interviewer", roles = "INTERVIEWER")
    @DisplayName("PUT /api/v1/feedback/{id}/finalize — Should return 409 when already finalized")
    void finalizeFeedback_AlreadyFinalized_Returns409() throws Exception {
        FinalizeRequest first = new FinalizeRequest();
        first.setFinalDecision(FinalDecision.HIRED);
        feedbackService.finalizeFeedback(feedbackId, first);

        FinalizeRequest second = new FinalizeRequest();
        second.setFinalDecision(FinalDecision.REJECTED);

        mockMvc.perform(put("/api/v1/feedback/{id}/finalize", feedbackId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ALREADY_FINALIZED"));
    }

    @Test
    @WithMockUser(username = "interviewer", roles = "INTERVIEWER")
    @DisplayName("PUT /api/v1/feedback/{id}/finalize — Should return 400 when finalDecision is missing")
    void finalizeFeedback_MissingDecision_Returns400() throws Exception {
        mockMvc.perform(put("/api/v1/feedback/{id}/finalize", feedbackId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recommendation\": \"Good candidate\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    // =========================================================
    // Helpers
    // =========================================================

    private ResumeRequest buildResumeRequest(String email) {
        ResumeRequest req = new ResumeRequest();
        req.setFirstName("Test");
        req.setLastName("Candidate");
        req.setEmail(email);
        req.setNationality("BE");
        req.setPhoneNumber("+3200000000");
        req.setPositionTitle("Economist");
        req.setDepartment("Finance");
        req.setYearsOfExperience(6);
        req.setCoverLetter("Feedback IT test cover letter");
        return req;
    }

    private ScreeningRequest buildScreeningRequest(Long resumeId) {
        ScreeningRequest req = new ScreeningRequest();
        req.setResumeId(resumeId);
        req.setScreenerName("HR Screener");
        req.setEligibilityScore(85);
        return req;
    }

    private InterviewRequest buildInterviewRequest(Long screeningId) {
        InterviewRequest req = new InterviewRequest();
        req.setScreeningId(screeningId);
        req.setInterviewerName("Prof. Laurent");
        req.setInterviewerEmail("laurent@ec.europa.eu");
        req.setScheduledAt(LocalDateTime.now().plusDays(7));
        req.setDurationMinutes(90);
        req.setType(InterviewType.ONSITE);
        return req;
    }

    private FeedbackRequest buildFeedbackRequest(Long interviewId) {
        FeedbackRequest req = new FeedbackRequest();
        req.setInterviewId(interviewId);
        req.setTechnicalScore(80);
        req.setBehavioralScore(75);
        req.setCommunicationScore(85);
        req.setStrengths("Strong analytical skills");
        req.setWeaknesses("Limited domain knowledge");
        return req;
    }
}
