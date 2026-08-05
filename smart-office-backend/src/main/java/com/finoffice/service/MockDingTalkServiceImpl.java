package com.finoffice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(name = "dingtalk.enabled", havingValue = "false", matchIfMissing = true)
public class MockDingTalkServiceImpl implements DingTalkService {

    @Override
    public void sendText(String content) {
        log.info("[MockDingTalk] sendText: {}", content);
    }

    @Override
    public void sendMarkdown(String title, String markdown) {
        log.info("[MockDingTalk] sendMarkdown title={}, content={}", title, markdown);
    }

    @Override
    public void sendMeetingSummary(String meetingTitle, String summaryText) {
        String markdown = formatSummaryAsMarkdown(meetingTitle, summaryText);
        log.info("[MockDingTalk] sendMeetingSummary: {}", meetingTitle);
        sendMarkdown("会议纪要 - " + meetingTitle, markdown);
    }

    private String formatSummaryAsMarkdown(String meetingTitle, String summaryText) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 会议纪要: ").append(meetingTitle).append("\n\n");
        sb.append(summaryText);
        return sb.toString();
    }
}