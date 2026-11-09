package com.eurojobcopilot.modules.resume.service;

import com.eurojobcopilot.modules.ai.client.AiJsonPromptRequest;
import com.eurojobcopilot.modules.ai.service.AiGatewayService;
import com.eurojobcopilot.modules.ai.service.AiStructuredResponse;
import com.eurojobcopilot.modules.auth.entity.AccountStatus;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.entity.UserRole;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.resume.dto.ApproveMasterResumeRequest;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeJsonDto;
import com.eurojobcopilot.modules.resume.dto.ResumeDownloadResource;
import com.eurojobcopilot.modules.resume.dto.ResumeParseResponse;
import com.eurojobcopilot.modules.resume.dto.ResumeResponse;
import com.eurojobcopilot.modules.resume.dto.StoredResumeFile;
import com.eurojobcopilot.modules.resume.entity.Resume;
import com.eurojobcopilot.modules.resume.entity.ResumeParseStatus;
import com.eurojobcopilot.modules.resume.entity.ResumeType;
import com.eurojobcopilot.modules.resume.entity.StorageProvider;
import com.eurojobcopilot.modules.resume.exception.InvalidResumeFileException;
import com.eurojobcopilot.modules.resume.exception.ResumeConflictException;
import com.eurojobcopilot.modules.resume.exception.ResumeNotFoundException;
import com.eurojobcopilot.modules.resume.parser.ExtractedResumeText;
import com.eurojobcopilot.modules.resume.parser.MasterResumeParsePromptBuilder;
import com.eurojobcopilot.modules.resume.parser.MasterResumeParseValidator;
import com.eurojobcopilot.modules.resume.parser.RawAiParsedResumePayload;
import com.eurojobcopilot.modules.resume.parser.ResumeTextExtractor;
import com.eurojobcopilot.modules.resume.parser.ValidatedMasterResumeParse;
import com.eurojobcopilot.modules.resume.repository.ResumeRepository;
import com.eurojobcopilot.modules.resume.repository.TailoredResumeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResumeServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ResumeRepository resumeRepository;

    @Mock
    private TailoredResumeRepository tailoredResumeRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private ResumeTextExtractor resumeTextExtractor;

    @Mock
    private MasterResumeParsePromptBuilder promptBuilder;

    @Mock
    private AiGatewayService aiGatewayService;

    @Mock
    private MasterResumeParseValidator parseValidator;

    @Mock
    private MasterResumePersistenceHelper persistenceHelper;

    @InjectMocks
    private ResumeServiceImpl resumeService;

    private User ownerUser;
    private AuthenticatedUserPrincipal ownerPrincipal;

    @BeforeEach
    void setUp() {
        ownerUser = User.builder()
                .id(10L)
                .uuid(UUID.randomUUID().toString())
                .email("candidate@eurojob.eu")
                .passwordHash("encoded-pass")
                .firstName("Lukas")
                .lastName("Mueller")
                .role(UserRole.ROLE_USER)
                .accountStatus(AccountStatus.ACTIVE)
                .build();

        ownerPrincipal = AuthenticatedUserPrincipal.from(ownerUser);
    }

    @Test
    void uploadMasterResume_incrementsVersionAndPersistsUnparsedMasterMetadata() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "cv.pdf", "application/pdf", "%PDF-1.7\n...".getBytes(StandardCharsets.US_ASCII)
        );
        StoredResumeFile stored = new StoredResumeFile(
                ownerUser.getUuid() + "/stored.pdf", "cv.pdf", "application/pdf", 128L
        );

        when(userRepository.findById(10L)).thenReturn(Optional.of(ownerUser));
        when(fileStorageService.storeResumeFile(file, ownerUser.getUuid())).thenReturn(stored);
        when(resumeRepository.findMaxVersionNumberByUserId(10L)).thenReturn(2);
        when(resumeRepository.saveAndFlush(any(Resume.class))).thenAnswer(inv -> inv.getArgument(0));

        ResumeResponse response = resumeService.uploadMasterResume(ownerPrincipal, file, "  Senior Backend CV  ");

        ArgumentCaptor<Resume> resumeCaptor = ArgumentCaptor.forClass(Resume.class);
        verify(resumeRepository).saveAndFlush(resumeCaptor.capture());

        Resume saved = resumeCaptor.getValue();
        assertThat(saved.getTitle()).isEqualTo("Senior Backend CV");
        assertThat(saved.getResumeType()).isEqualTo(ResumeType.MASTER);
        assertThat(saved.getStorageProvider()).isEqualTo(StorageProvider.LOCAL);
        assertThat(saved.getVersionNumber()).isEqualTo(3);
        assertThat(saved.isMasterApproved()).isFalse();
        assertThat(saved.getParsedContentJson()).isNull();
        assertThat(saved.getFilePath()).isEqualTo(ownerUser.getUuid() + "/stored.pdf");

        assertThat(response.getVersionNumber()).isEqualTo(3);
        assertThat(response.isMasterApproved()).isFalse();
        assertThat(response.getTitle()).isEqualTo("Senior Backend CV");
    }

    @Test
    void uploadMasterResume_blankOrTooLongTitle_rejectsWithoutCallingStorage() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "cv.pdf", "application/pdf", "%PDF-1.7\n...".getBytes(StandardCharsets.US_ASCII)
        );
        when(userRepository.findById(10L)).thenReturn(Optional.of(ownerUser));

        assertThatThrownBy(() -> resumeService.uploadMasterResume(ownerPrincipal, file, "   "))
                .isInstanceOf(InvalidResumeFileException.class);
        assertThatThrownBy(() -> resumeService.uploadMasterResume(ownerPrincipal, file, "x".repeat(201)))
                .isInstanceOf(InvalidResumeFileException.class);

        verifyNoInteractions(fileStorageService);
    }

    @Test
    void uploadMasterResume_dbPersistenceFailure_cleansUpStoredDiskFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "cv.pdf", "application/pdf", "%PDF-1.7\n...".getBytes(StandardCharsets.US_ASCII)
        );
        String storageKey = ownerUser.getUuid() + "/orphan.pdf";
        StoredResumeFile stored = new StoredResumeFile(storageKey, "cv.pdf", "application/pdf", 128L);

        when(userRepository.findById(10L)).thenReturn(Optional.of(ownerUser));
        when(fileStorageService.storeResumeFile(file, ownerUser.getUuid())).thenReturn(stored);
        when(resumeRepository.findMaxVersionNumberByUserId(10L)).thenReturn(0);
        when(resumeRepository.saveAndFlush(any(Resume.class)))
                .thenThrow(new DataIntegrityViolationException("Simulated DB write failure"));

        assertThatThrownBy(() -> resumeService.uploadMasterResume(ownerPrincipal, file, "Master CV"))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(fileStorageService).deleteResumeFile(storageKey);
    }

    @Test
    void getAndDownloadResume_enforcesStrictUserOwnership() {
        String uuid = UUID.randomUUID().toString();
        when(userRepository.findById(10L)).thenReturn(Optional.of(ownerUser));
        when(resumeRepository.findByUuidAndUserId(uuid, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resumeService.getResume(ownerPrincipal, uuid))
                .isInstanceOf(ResumeNotFoundException.class);
        assertThatThrownBy(() -> resumeService.downloadResume(ownerPrincipal, uuid))
                .isInstanceOf(ResumeNotFoundException.class);

        Resume owned = Resume.builder()
                .id(42L)
                .uuid(uuid)
                .user(ownerUser)
                .title("My CV")
                .fileName("cv.pdf")
                .filePath(ownerUser.getUuid() + "/stored.pdf")
                .fileSizeBytes(12L)
                .mimeType("application/pdf")
                .build();
        when(resumeRepository.findByUuidAndUserId(uuid, 10L)).thenReturn(Optional.of(owned));
        when(fileStorageService.loadResumeAsResource(owned.getFilePath()))
                .thenReturn(new ByteArrayResource("bytes".getBytes(StandardCharsets.UTF_8)));

        ResumeDownloadResource result = resumeService.downloadResume(ownerPrincipal, uuid);
        assertThat(result.fileName()).isEqualTo("cv.pdf");
        assertThat(result.mimeType()).isEqualTo("application/pdf");
    }

    @Test
    void deleteResume_whenTailoredResumesExist_throwsConflictAndKeepsFile() {
        String uuid = UUID.randomUUID().toString();
        Resume owned = Resume.builder()
                .id(55L)
                .uuid(uuid)
                .user(ownerUser)
                .filePath(ownerUser.getUuid() + "/keep.pdf")
                .build();

        when(userRepository.findById(10L)).thenReturn(Optional.of(ownerUser));
        when(resumeRepository.findByUuidAndUserId(uuid, 10L)).thenReturn(Optional.of(owned));
        when(tailoredResumeRepository.existsByMasterResumeId(55L)).thenReturn(true);

        assertThatThrownBy(() -> resumeService.deleteResume(ownerPrincipal, uuid))
                .isInstanceOf(ResumeConflictException.class);

        verify(resumeRepository, never()).delete(any());
        verify(fileStorageService, never()).deleteResumeFile(any());
    }

    @Test
    void deleteResume_unreferenced_deletesDbBeforeDiskFile() {
        String uuid = UUID.randomUUID().toString();
        String storageKey = ownerUser.getUuid() + "/delete.pdf";
        Resume owned = Resume.builder()
                .id(77L)
                .uuid(uuid)
                .user(ownerUser)
                .filePath(storageKey)
                .build();

        when(userRepository.findById(10L)).thenReturn(Optional.of(ownerUser));
        when(resumeRepository.findByUuidAndUserId(uuid, 10L)).thenReturn(Optional.of(owned));
        when(tailoredResumeRepository.existsByMasterResumeId(77L)).thenReturn(false);
        doNothing().when(resumeRepository).delete(owned);
        doNothing().when(resumeRepository).flush();

        resumeService.deleteResume(ownerPrincipal, uuid);

        var order = inOrder(resumeRepository, fileStorageService);
        order.verify(resumeRepository).delete(owned);
        order.verify(resumeRepository).flush();
        order.verify(fileStorageService).deleteResumeFile(storageKey);
    }

    @Test
    void parseMasterResume_returnsCachedDraftWhenForceRefreshFalse() {
        String uuid = UUID.randomUUID().toString();
        Resume owned = Resume.builder()
                .id(12L)
                .uuid(uuid)
                .user(ownerUser)
                .resumeType(ResumeType.MASTER)
                .parseStatus(ResumeParseStatus.PARSED)
                .build();
        ParsedResumeJsonDto cachedDto = ParsedResumeJsonDto.builder().summary("Cached summary").build();
        MasterResumePersistenceHelper.ResumeSnapshot snapshot =
                new MasterResumePersistenceHelper.ResumeSnapshot(owned, ResumeParseStatus.PARSED, cachedDto);
        ResumeParseResponse expected = ResumeParseResponse.builder()
                .resumeUuid(uuid)
                .parseStatus(ResumeParseStatus.PARSED)
                .parsedData(cachedDto)
                .build();

        when(userRepository.findById(10L)).thenReturn(Optional.of(ownerUser));
        when(persistenceHelper.loadMasterResumeSnapshot(10L, uuid)).thenReturn(snapshot);
        when(persistenceHelper.toParseResponse(owned, cachedDto, null)).thenReturn(expected);

        ResumeParseResponse actual = resumeService.parseMasterResume(ownerPrincipal, uuid, false);

        assertThat(actual.getParsedData().getSummary()).isEqualTo("Cached summary");
        verifyNoInteractions(resumeTextExtractor, aiGatewayService, promptBuilder, parseValidator);
    }

    @Test
    void parseMasterResume_runsExtractionPromptAiValidationAndPersistsDraft() {
        String uuid = UUID.randomUUID().toString();
        Resume owned = Resume.builder()
                .id(15L)
                .uuid(uuid)
                .user(ownerUser)
                .resumeType(ResumeType.MASTER)
                .filePath(ownerUser.getUuid() + "/cv.pdf")
                .fileName("cv.pdf")
                .mimeType("application/pdf")
                .parseStatus(ResumeParseStatus.NOT_PARSED)
                .build();
        MasterResumePersistenceHelper.ResumeSnapshot snapshot =
                new MasterResumePersistenceHelper.ResumeSnapshot(owned, ResumeParseStatus.NOT_PARSED, null);
        Resource resource = new ByteArrayResource("pdf bytes".getBytes(StandardCharsets.UTF_8));
        ExtractedResumeText extracted = new ExtractedResumeText("Experienced Java Architect CV text", 34, 1, "PDF", List.of());
        AiJsonPromptRequest promptReq = new AiJsonPromptRequest("master-resume-parsing", "sys", "usr", 0.05, null);
        RawAiParsedResumePayload rawPayload = new RawAiParsedResumePayload();
        AiStructuredResponse<RawAiParsedResumePayload> aiResponse =
                new AiStructuredResponse<>("GEMINI", "gemini-2.5-flash", rawPayload, "{}", 100, 80);
        ParsedResumeJsonDto validatedDto = ParsedResumeJsonDto.builder().summary("Experienced Java Architect").build();
        ValidatedMasterResumeParse validated = new ValidatedMasterResumeParse(validatedDto, List.of());
        ResumeParseResponse savedResponse = ResumeParseResponse.builder()
                .resumeUuid(uuid)
                .parseStatus(ResumeParseStatus.PARSED)
                .parsedData(validatedDto)
                .build();

        when(userRepository.findById(10L)).thenReturn(Optional.of(ownerUser));
        when(persistenceHelper.loadMasterResumeSnapshot(10L, uuid)).thenReturn(snapshot);
        when(fileStorageService.loadResumeAsResource(owned.getFilePath())).thenReturn(resource);
        when(resumeTextExtractor.extract(resource, "application/pdf", "cv.pdf")).thenReturn(extracted);
        when(promptBuilder.buildPromptRequest(owned, extracted))
                .thenReturn(new MasterResumeParsePromptBuilder.PromptBuildResult(promptReq, false));
        when(aiGatewayService.executeStructuredJsonPrompt(promptReq, RawAiParsedResumePayload.class)).thenReturn(aiResponse);
        when(parseValidator.validate(rawPayload, extracted, false)).thenReturn(validated);
        when(persistenceHelper.saveParsedDraft(10L, uuid, validatedDto, List.of(), "GEMINI", "gemini-2.5-flash", 34))
                .thenReturn(savedResponse);

        ResumeParseResponse actual = resumeService.parseMasterResume(ownerPrincipal, uuid, true);

        assertThat(actual.getParseStatus()).isEqualTo(ResumeParseStatus.PARSED);
        verify(persistenceHelper).saveParsedDraft(eq(10L), eq(uuid), eq(validatedDto), eq(List.of()), eq("GEMINI"), eq("gemini-2.5-flash"), eq(34));
    }

    @Test
    void approveMasterResume_delegatesToTransactionalHelper() {
        String uuid = UUID.randomUUID().toString();
        ApproveMasterResumeRequest request = ApproveMasterResumeRequest.builder()
                .resumeUuid(uuid)
                .approvedParsedData(ParsedResumeJsonDto.builder().summary("Approved").build())
                .build();
        ResumeResponse expected = ResumeResponse.builder()
                .uuid(uuid)
                .masterApproved(true)
                .parseStatus(ResumeParseStatus.APPROVED)
                .build();

        when(userRepository.findById(10L)).thenReturn(Optional.of(ownerUser));
        when(persistenceHelper.approveAndSyncMasterResume(10L, request)).thenReturn(expected);

        ResumeResponse actual = resumeService.approveMasterResume(ownerPrincipal, request);
        assertThat(actual.isMasterApproved()).isTrue();
        assertThat(actual.getParseStatus()).isEqualTo(ResumeParseStatus.APPROVED);
        verify(persistenceHelper).approveAndSyncMasterResume(10L, request);
    }
}
