package com.smarttask.notification.service;
import org.springframework.scheduling.annotation.Scheduled;import org.springframework.stereotype.Component;
@Component public class ReminderScheduler{private final NotificationService service;public ReminderScheduler(NotificationService s){service=s;}@Scheduled(fixedDelayString="${REMINDER_POLL_DELAY_MS:60000}")public void process(){service.processDue();}}
