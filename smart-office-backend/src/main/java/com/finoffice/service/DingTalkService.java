package com.finoffice.service;

public interface DingTalkService {
    void sendText(String content);
    void sendMarkdown(String title, String markdown);
    void sendMeetingSummary(String meetingTitle, String summaryText);
}