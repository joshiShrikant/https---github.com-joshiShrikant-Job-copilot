package com.eurojobcopilot.modules.assistant.ai;

import com.eurojobcopilot.core.exception.ResourceNotFoundException;
import com.eurojobcopilot.modules.assistant.entity.AssistantActionType;
import com.eurojobcopilot.modules.assistant.entity.JobAssistantMessage;
import com.eurojobcopilot.modules.assistant.repository.JobAssistantMessageRepository;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

@Component
@RequiredArgsConstructor
public class JobAssistantPersistenceHelper {

    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final JobAssistantMessageRepository messageRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public JobAssistantMessage saveAssistantTurn(
            Long userId,
            Long jobId,
            AssistantActionType actionType,
            String userPrompt,
            ValidatedAssistantPayload validated,
            String provider,
            String modelUsed
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user no longer exists"));
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + jobId));

        JobAssistantMessage entity = JobAssistantMessage.builder()
                .user(user)
                .job(job)
                .actionType(actionType)
                .userPrompt(userPrompt)
                .assistantTitle(validated.assistantTitle())
                .assistantContent(validated.assistantContent())
                .editableContent(validated.assistantContent())
                .highlights(new ArrayList<>(validated.highlights()))
                .editedByUser(false)
                .aiGenerated(true)
                .aiProvider(provider)
                .aiModelUsed(modelUsed)
                .build();

        return messageRepository.saveAndFlush(entity);
    }
}

