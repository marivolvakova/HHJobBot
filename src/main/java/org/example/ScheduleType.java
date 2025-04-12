package org.example;

public enum ScheduleType {
    FULL_DAY("Офис", "fullDay"),
    FLEXIBLE("Гибкий график", "flexible"),
    REMOTE("Удаленная работа", "remote"),
    SHIFT("Сменный график", "shift"),
    FLY_IN_FLY_OUT("Вахтовый метод", "flyInFlyOut"),
    ANY("Любой", null);

    private final String displayName;
    private final String apiId;

    ScheduleType(String displayName, String apiId) {
        this.displayName = displayName;
        this.apiId = apiId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getApiId() {
        return apiId;
    }
}