package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.io.IOException;
import java.util.*;

public class HHApiService {
    private static final String API_URL = "https://api.hh.ru/vacancies";
    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public List<Vacancy> searchVacancies(String profession, String experience,
                                         Integer salary,
                                         ScheduleType scheduleType,
                                         String location) throws IOException {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(API_URL).newBuilder();

        // Добавляем параметры
        if (profession != null && !profession.isEmpty()) {
            urlBuilder.addQueryParameter("text", profession);
        }

        if (experience != null) {
            urlBuilder.addEncodedQueryParameter("experience", convertExperience(experience));
        }

        if (salary != null && salary > 0) {
            urlBuilder.addQueryParameter("salary", String.valueOf(salary));
            urlBuilder.addQueryParameter("currency", "RUR");
            urlBuilder.addQueryParameter("only_with_salary", "true");
        }

        if (scheduleType != null && scheduleType != ScheduleType.ANY && scheduleType.getApiId() != null) {
            urlBuilder.addQueryParameter("schedule", scheduleType.getApiId());
        }

        if (location != null && !location.isEmpty() && !location.equalsIgnoreCase("любой")) {
            urlBuilder.addQueryParameter("area", getAreaId(location));
        }

        urlBuilder.addQueryParameter("per_page", "100");

        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .addHeader("User-Agent", "HHJobBot/1.0")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response.code() +
                        ", message: " + response.message() +
                        ", body: " + (response.body() != null ? response.body().string() : "null"));
            }

            JsonNode root = mapper.readTree(response.body().byteStream());
            return parseVacancies(root);
        }
    }

    // Остальные методы остаются без изменений
    private String convertExperience(String experience) {
        switch (experience) {
            case "Нет опыта": return "noExperience";
            case "1-3 года": return "between1And3";
            case "3-6 лет": return "between3And6";
            case "Более 6 лет": return "moreThan6";
            default: return "";
        }
    }

    private String getAreaId(String location) {
        switch (location.toLowerCase()) {
            case "москва": return "1";
            case "санкт-петербург": return "2";
            case "новосибирск": return "4";
            case "екатеринбург": return "3";
            default: return "113"; // Россия
        }
    }


    private List<Vacancy> parseVacancies(JsonNode root) {
        List<Vacancy> vacancies = new ArrayList<>();

        for (JsonNode item : root.path("items")) {
            Vacancy vacancy = new Vacancy();
            vacancy.setId(item.path("id").asText());
            vacancy.setTitle(item.path("name").asText());
            vacancy.setAlternateUrl(item.path("alternate_url").asText());

            // Обработка работодателя
            JsonNode employerNode = item.path("employer");
            vacancy.setEmployerName(employerNode.path("name").asText("Не указано"));

            // Обработка зарплаты
            JsonNode salaryNode = item.path("salary");
            if (!salaryNode.isMissingNode()) {
                String from = salaryNode.path("from").isNull() ? "0" : salaryNode.path("from").asText();
                String to = salaryNode.path("to").isNull() ? "0" : salaryNode.path("to").asText();
                String currency = salaryNode.path("currency").asText("RUR");

                vacancy.setSalary(from.equals("0") && to.equals("0") ? "не указана" :
                        String.format("%s %s - %s %s", from, currency, to, currency));
            } else {
                vacancy.setSalary("не указана");
            }

            // Опыт работы
            vacancy.setExperience(item.path("experience").path("name").asText("Не указан"));

            // График работы
            JsonNode scheduleNode = item.path("schedule");
            vacancy.setScheduleType(scheduleNode.isMissingNode() ? ScheduleType.ANY :
                    Arrays.stream(ScheduleType.values())
                            .filter(s -> s.getDisplayName().equalsIgnoreCase(scheduleNode.path("name").asText()))
                            .findFirst()
                            .orElse(ScheduleType.ANY));

            // Локация
            vacancy.setLocation(item.path("area").path("name").asText("Не указана"));
            vacancies.add(vacancy);
        }
        return vacancies;
    }
}