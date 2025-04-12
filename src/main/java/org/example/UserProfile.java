package org.example;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserProfile {
    private HHJobBot.State currentState = HHJobBot.State.IDLE;
    private String profession;
    private String experience;
    private Integer salary;
    private String location;
    private ScheduleType scheduleType = ScheduleType.ANY;
    private HHJobBot.NotificationFrequency notificationFrequency = HHJobBot.NotificationFrequency.EVERY_6_HOURS;
    private boolean notificationsEnabled = true;
}