package org.example;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class Vacancy {
    private String id;
    private String title;
    private String alternateUrl;
    private String employerName;
    private String salary;
    private String experience;
    private ScheduleType scheduleType;
    private String location;
}