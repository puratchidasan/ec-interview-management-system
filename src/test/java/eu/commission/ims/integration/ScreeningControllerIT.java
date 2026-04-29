package eu.commission.ims.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for ScreeningController.
 * Loads full Spring Boot context with H2 in-memory database.
 * Each test runs in a rolled-back transaction.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Screening Controller Integration Tests")
class ScreeningControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ResumeService resumeService;
    @Autowired private ScreeningService screeningService;

    private Long resumeId;
    private Long screeningId;

    @BeforeEach
    void setUp() {
        ResumeResponse resume = resumeService.submitResume(buildResumeRequest("screening-it@ec.europa.eu"));
        resumeId = resume.getId();

        ScreeningRequest screeningReq = new ScreeningRequest();
        screeningReq.setResumeId(resumeId);
        screeningReq.setScreenerName("HR Officer");
        screeningReq.setEligibilityScore(80);
        ScreeningResponse screening = screeningService.createScreening(screeningReq);
        screeningId = screening.getId();
    }

    // =========================================================
    // POST /api/v1/screenings
    // =========================================================

    @Test
    @WithMockUser(username = "recruiter", roles = "RECRUITER")
    @DisplayName("POST /api/v1/screenings — Should return 201 for a new resume (no prior screening)")
    void createScreening_ValidRequest_Returns201() throws Exception {
        ResumeResponse freshResume = resumeService.submitResume(buildResumeRequest("fresh-screening@ec.europa.eu"));

        ScreeningRequest req = new ScreeningRequest();
        req.setResumeId(freshResume.getId());
        req.setScreenerName("Senior HR");
        req.setEligibilityScore(90);

        mockMvc.perform(post("/api/v1/screenings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.screenerName").value("Senior HR"))
                .andExpect(jsonPath("$.data.decision").value("PENDING"))
                .andExpect(jsonPath("$.data.id").isNumber());
    }

    @Test
    @WithMockUser(username = "recruiter", roles = "RECRUITER")
    @DisplayName("POST /api/v1/screenings — Should return 400 when screenerName is missing")
    void createScreening_MissingScreenerName_Returns400() throws Exception {
        ScreeningRequest req = new ScreeningRequest();
        req.setResumeId(resumeId);
        // screenerName omitted

        mockMvc.perform(post("/api/v1/screenings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.screenerName").exists());
    }

    @Test
    @WithMockUser(username = "recruiter", roles = "RECRUITER")
    @DisplayName("POST /api/v1/screenings — Should return 400 when resumeId is missing")
    void createScreening_MissingResumeId_Returns400() throws Exception {
        ScreeningRequest req = new ScreeningRequest();
        req.setScreenerName("HR Officer");
        // resumeId omitted

        mockMvc.perform(post("/api/v1/screenings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    @WithMockUser(username = "recruiter", roles = "RECRUITER")
    @DisplayName("POST /api/v1/screenings — Should return 404 when resume does not exist")
    void createScreening_ResumeNotFound_Returns404() throws Exception {
        ScreeningRequest req = new ScreeningRequest();
        req.setResumeId(99999L);
        req.setScreenerName("HR Officer");

        mockMvc.perform(post("/api/v1/screenings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "recruiter", roles = "RECRUITER")
    @DisplayName("POST /api/v1/screenings — Should return 409 for duplicate screening on same resume")
    void createScreening_DuplicateScreening_Returns409() throws Exception {
        ScreeningRequest req = new ScreeningRequest();
        req.setResumeId(resumeId);
        req.setScreenerName("Another HR");

        mockMvc.perform(post("/api/v1/screenings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_SCREENING"));
    }

    @Test
    @DisplayName("POST /api/v1/screenings — Should return 401 when unauthenticated")
    void createScreening_Unauthenticated_Returns401() throws Exception {
        ScreeningRequest req = new ScreeningRequest();
        req.setResumeId(resumeId);
        req.setScreenerName("HR Officer");

        mockMvc.perform(post("/api/v1/screenings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "interviewer", roles = "INTERVIEWER")
    @DisplayName("POST /api/v1/screenings — Should return 403 for INTERVIEWER role")
    void createScreening_WrongRole_Returns403() throws Exception {
        ScreeningRequest req = new ScreeningRequest();
        req.setResumeId(resumeId);
        req.setScreenerName("HR Officer");

        mockMvc.perform(post("/api/v1/screenings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // =========================================================
    // GET /api/v1/screenings/{id}
    // =========================================================

    @Test
    @WithMockUser(username = "recruiter", roles = "RECRUITER")
    @DisplayName("GET /api/v1/screenings/{id} — Should return 200 with screening data")
    void getScreening_Found_Returns200() throws Exception {
        mockMvc.perform(get("/api/v1/screenings/{id}", screeningId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(screeningId))
                .andExpect(jsonPath("$.data.screenerName").value("HR Officer"))
                .andExpect(jsonPath("$.data.decision").value("PENDING"));
    }

    @Test
    @WithMockUser(username = "recruiter", roles = "RECRUITER")
    @DisplayName("GET /api/v1/screenings/{id} — Should return 404 for unknown ID")
    void getScreening_NotFound_Returns404() throws Exception {
        mockMvc.perform(get("/api/v1/screenings/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // =========================================================
    // GET /api/v1/screenings/resume/{resumeId}
    // =========================================================

    @Test
    @WithMockUser(username = "recruiter", roles = "RECRUITER")
    @DisplayName("GET /api/v1/screenings/resume/{resumeId} — Should return screening for resume")
    void getByResume_Found_Returns200() throws Exception {
        mockMvc.perform(get("/api/v1/screenings/resume/{resumeId}", resumeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.resumeId").value(resumeId));
    }

    @Test
    @WithMockUser(username = "recruiter", roles = "RECRUITER")
    @DisplayName("GET /api/v1/screenings/resume/{resumeId} — Should return 404 for resume with no screening")
    void getByResume_NotFound_Returns404() throws Exception {
        mockMvc.perform(get("/api/v1/screenings/resume/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // =========================================================
    // GET /api/v1/screenings/pending
    // =========================================================

    @Test
    @WithMockUser(username = "recruiter", roles = "RECRUITER")
    @DisplayName("GET /api/v1/screenings/pending — Should return list of pending screenings")
    void getPendingScreenings_Returns200() throws Exception {
        mockMvc.perform(get("/api/v1/screenings/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].decision").value("PENDING"));
    }

    // =========================================================
    // PUT /api/v1/screenings/{id}/decision
    // =========================================================

    @Test
    @WithMockUser(username = "recruiter", roles = "RECRUITER")
    @DisplayName("PUT /api/v1/screenings/{id}/decision — Should return 200 and update resume to ACCEPTED on PASSED")
    void recordDecision_Passed_Returns200() throws Exception {
        DecisionRequest req = new DecisionRequest();
        req.setDecision(ScreeningDecision.PASSED);
        req.setNotes("All criteria met.");

        mockMvc.perform(put("/api/v1/screenings/{id}/decision", screeningId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.decision").value("PASSED"))
                .andExpect(jsonPath("$.data.screenedAt").isNotEmpty());
    }

    @Test
    @WithMockUser(username = "recruiter", roles = "RECRUITER")
    @DisplayName("PUT /api/v1/screenings/{id}/decision — Should return 200 on FAILED decision")
    void recordDecision_Failed_Returns200() throws Exception {
        DecisionRequest req = new DecisionRequest();
        req.setDecision(ScreeningDecision.FAILED);

        mockMvc.perform(put("/api/v1/screenings/{id}/decision", screeningId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.decision").value("FAILED"));
    }

    @Test
    @WithMockUser(username = "recruiter", roles = "RECRUITER")
    @DisplayName("PUT /api/v1/screenings/{id}/decision — Should return 409 when decision already recorded")
    void recordDecision_AlreadyDecided_Returns409() throws Exception {
        // First decision
        DecisionRequest first = new DecisionRequest();
        first.setDecision(ScreeningDecision.PASSED);
        screeningService.recordDecision(screeningId, first);

        // Second attempt
        DecisionRequest second = new DecisionRequest();
        second.setDecision(ScreeningDecision.FAILED);

        mockMvc.perform(put("/api/v1/screenings/{id}/decision", screeningId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DECISION_ALREADY_SET"));
    }

    @Test
    @WithMockUser(username = "recruiter", roles = "RECRUITER")
    @DisplayName("PUT /api/v1/screenings/{id}/decision — Should return 400 when decision field is missing")
    void recordDecision_MissingDecision_Returns400() throws Exception {
        mockMvc.perform(put("/api/v1/screenings/{id}/decision", screeningId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\": \"some notes\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    // =========================================================
    // Helpers
    // =========================================================

    private ResumeRequest buildResumeRequest(String email) {
        ResumeRequest req = new ResumeRequest();
        req.setFirstName("Test");
        req.setLastName("Screener");
        req.setEmail(email);
        req.setNationality("DE");
        req.setPhoneNumber("+4900000000");
        req.setPositionTitle("Policy Analyst");
        req.setDepartment("Legal");
        req.setYearsOfExperience(3);
        req.setCoverLetter("Screening IT test cover letter");
        return req;
    }
}
