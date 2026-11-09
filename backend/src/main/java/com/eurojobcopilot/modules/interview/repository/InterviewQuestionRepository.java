package com.eurojobcopilot.modules.interview.repository;

import com.eurojobcopilot.modules.interview.entity.InterviewQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InterviewQuestionRepository extends JpaRepository<InterviewQuestion, Long> {

    List<InterviewQuestion> findAllBySessionIdOrderByIdAsc(Long sessionId);

    List<InterviewQuestion> findAllBySessionIdOrderByOrderIndexAscIdAsc(Long sessionId);

    List<InterviewQuestion> findAllBySessionIdInOrderByOrderIndexAscIdAsc(Collection<Long> sessionIds);

    Optional<InterviewQuestion> findByIdAndSessionId(Long id, Long sessionId);

    void deleteAllBySessionId(Long sessionId);
}
