package com.eurojobcopilot.modules.recruiter.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RawAiOutreachPayload {

    private String subjectLine;
    private String messageBody;
    private List<String> keyTalkingPoints = new ArrayList<>();
    private Integer suggestedFollowUpDays;
    private List<String> warnings = new ArrayList<>();
}

