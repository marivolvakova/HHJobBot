package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;


public class HHJobBot extends TelegramLongPollingBot {
    private final Map<Long, UserProfile> userProfiles = new ConcurrentHashMap<>();
    private final Map<Long, List<Vacancy>> lastSearches = new ConcurrentHashMap<>();
    private final HHApiService hhApiService = new HHApiService();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public enum State {
        IDLE, AWAITING_PROFESSION, AWAITING_EXPERIENCE, AWAITING_SKILLS,
        AWAITING_SALARY, AWAITING_SCHEDULE_TYPE, AWAITING_LOCATION,
        AWAITING_NOTIFICATION_FREQ
    }

    public enum NotificationFrequency {
        HOURLY("Каждый час", 1),
        EVERY_3_HOURS("Каждые 3 часа", 3),
        EVERY_6_HOURS("Каждые 6 часов", 6),
        DAILY("Раз в день", 24);

        private final String description;
        private final int hours;

        NotificationFrequency(String description, int hours) {
            this.description = description;
            this.hours = hours;
        }

        public String getDescription() {
            return description;
        }

        public int getHours() {
            return hours;
        }
    }


    @Override
    public String getBotUsername() {
        return "HhSearchJobBot";
    }

    @Override
    public String getBotToken() {
        return "7614102307:AAH9-CbDZOxMnyd-CA7mybZPpplQ_I-_i38";
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            long chatId = update.getMessage().getChatId();
            String messageText = update.getMessage().getText();

            UserProfile profile = userProfiles.computeIfAbsent(chatId, k -> new UserProfile());

            try {
                handleMessage(chatId, profile, messageText);
            } catch (Exception e) {
                sendMessage(chatId, "⚠️ Произошла ошибка. Пожалуйста, попробуйте позже.");
                e.printStackTrace();
            }
        }
    }

    private void handleMessage(long chatId, UserProfile profile, String text) throws IOException {
        switch (text) {
            case "/start":
                sendWelcomeMessage(chatId);
                break;
            case "🔍 Поиск вакансий":
                startSearchProcess(chatId, profile);
                break;
            case "⚙️ Настройки профиля":
                showProfileSettings(chatId, profile);
                break;
            case "🔔 Управление уведомлениями":
                manageNotifications(chatId, profile);
                break;
            case "Отмена":
                cancelCurrentOperation(chatId, profile);
                break;
            default:
                processUserInput(chatId, profile, text);
        }
    }

    private void startSearchProcess(long chatId, UserProfile profile) {
        profile.setCurrentState(State.AWAITING_PROFESSION);
        sendMessage(chatId, "Введите название профессии или должности:", createCancelKeyboard());
    }

    private void processUserInput(long chatId, UserProfile profile, String text) throws IOException {
        switch (profile.getCurrentState()) {
            case AWAITING_PROFESSION:
                profile.setProfession(text);
                askForExperience(chatId, profile);
                break;
            case AWAITING_EXPERIENCE:
                profile.setExperience(text);
                askForSalary(chatId, profile);
                break;
            case AWAITING_SALARY:
                try {
                    profile.setSalary(Integer.parseInt(text));
                    askForScheduleType(chatId, profile);
                } catch (NumberFormatException e) {
                    sendMessage(chatId, "Пожалуйста, введите число.");
                }
                break;
            case AWAITING_SCHEDULE_TYPE:
                // Обработка выбора графика работы
                ScheduleType schedule = Arrays.stream(ScheduleType.values())
                        .filter(s -> s.getDisplayName().equalsIgnoreCase(text))
                        .findFirst()
                        .orElse(ScheduleType.ANY);
                profile.setScheduleType(schedule);
                askForLocation(chatId, profile); // Переходим к следующему шагу - выбор локации
                break;

            case AWAITING_LOCATION:
                profile.setLocation(text);
                askForNotificationFrequency(chatId, profile);
                break;
            case AWAITING_NOTIFICATION_FREQ:
                NotificationFrequency freq = Arrays.stream(NotificationFrequency.values())
                        .filter(f -> f.getDescription().equalsIgnoreCase(text))
                        .findFirst()
                        .orElse(NotificationFrequency.EVERY_6_HOURS);
                profile.setNotificationFrequency(freq);
                startVacancySearch(chatId, profile);
                break;
            default:
                sendDefaultResponse(chatId);
        }
    }

    private void askForExperience(long chatId, UserProfile profile) {
        profile.setCurrentState(State.AWAITING_EXPERIENCE);

        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        List<KeyboardRow> rows = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add("Нет опыта");
        row1.add("1-3 года");

        KeyboardRow row2 = new KeyboardRow();
        row2.add("3-6 лет");
        row2.add("Более 6 лет");

        rows.add(row1);
        rows.add(row2);

        KeyboardRow row3 = new KeyboardRow();
        row3.add("Отмена");
        rows.add(row3);

        keyboard.setKeyboard(rows);
        sendMessage(chatId, "Выберите ваш опыт работы:", keyboard);
    }

    private void askForSalary(long chatId, UserProfile profile) {
        profile.setCurrentState(State.AWAITING_SALARY);
        sendMessage(chatId, "Введите желаемую зарплату (руб.):", createCancelKeyboard());
    }

    private void askForScheduleType(long chatId, UserProfile profile) {
        profile.setCurrentState(State.AWAITING_SCHEDULE_TYPE);

        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        List<KeyboardRow> rows = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(ScheduleType.FULL_DAY.getDisplayName());
        row1.add(ScheduleType.REMOTE.getDisplayName());

        KeyboardRow row2 = new KeyboardRow();
        row2.add(ScheduleType.FLEXIBLE.getDisplayName());
        row2.add(ScheduleType.ANY.getDisplayName());

        KeyboardRow row3 = new KeyboardRow();
        row3.add("Отмена");

        rows.add(row1);
        rows.add(row2);
        rows.add(row3);

        keyboard.setKeyboard(rows);
        sendMessage(chatId, "Выберите график работы:", keyboard);
    }

    private void askForLocation(long chatId, UserProfile profile) {
        profile.setCurrentState(State.AWAITING_LOCATION);
        sendMessage(chatId, "Введите город для поиска (или 'любой'):", createCancelKeyboard());
    }

    private void askForNotificationFrequency(long chatId, UserProfile profile) {
        profile.setCurrentState(State.AWAITING_NOTIFICATION_FREQ);

        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        List<KeyboardRow> rows = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(NotificationFrequency.HOURLY.getDescription());
        row1.add(NotificationFrequency.EVERY_3_HOURS.getDescription());

        KeyboardRow row2 = new KeyboardRow();
        row2.add(NotificationFrequency.EVERY_6_HOURS.getDescription());
        row2.add(NotificationFrequency.DAILY.getDescription());

        rows.add(row1);
        rows.add(row2);

        KeyboardRow row3 = new KeyboardRow();
        row3.add("Отмена");
        rows.add(row3);

        keyboard.setKeyboard(rows);
        sendMessage(chatId, "Как часто присылать уведомления?", keyboard);
    }

    private void startVacancySearch(long chatId, UserProfile profile) throws IOException {
        sendMessage(chatId, "🔎 Ищем вакансии... Это может занять некоторое время.");

        List<Vacancy> vacancies = hhApiService.searchVacancies(
                profile.getProfession(),
                profile.getExperience(),
                profile.getSalary(),
                profile.getScheduleType(),  // передаем тип графика
                profile.getLocation()
        );

        lastSearches.put(chatId, vacancies);

        if (vacancies.isEmpty()) {
            sendMessage(chatId, "По вашему запросу вакансий не найдено. Попробуйте изменить параметры поиска.");
        } else {
            sendVacancies(chatId, vacancies);
        }

        if (profile.isNotificationsEnabled()) {
            scheduleNotificationCheck(chatId, profile);
        }

        showMainMenu(chatId);
    }

    private void scheduleNotificationCheck(long chatId, UserProfile profile) {
        scheduler.scheduleAtFixedRate(
                () -> {
                    try {
                        checkNewVacancies(chatId, profile);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                },
                0,
                profile.getNotificationFrequency().getHours(),
                TimeUnit.HOURS
        );
    }

    private void checkNewVacancies(long chatId, UserProfile profile) throws IOException {
        List<Vacancy> newVacancies = hhApiService.searchVacancies(
                profile.getProfession(),
                profile.getExperience(),
                profile.getSalary(),
                profile.getScheduleType(),
                profile.getLocation()
        );

        List<Vacancy> lastVacancies = lastSearches.getOrDefault(chatId, Collections.emptyList());
        List<Vacancy> uniqueNew = findNewVacancies(newVacancies, lastVacancies);

        if (!uniqueNew.isEmpty()) {
            sendMessage(chatId, "🔔 Появились новые вакансии по вашему запросу!");
            sendVacancies(chatId, uniqueNew);
            lastSearches.put(chatId, newVacancies);
        }
    }

    private List<Vacancy> findNewVacancies(List<Vacancy> current, List<Vacancy> last) {
        Set<String> lastIds = last.stream().map(Vacancy::getId).collect(Collectors.toSet());
        return current.stream()
                .filter(v -> !lastIds.contains(v.getId()))
                .collect(Collectors.toList());
    }

    private void sendVacancies(long chatId, List<Vacancy> vacancies) {
        vacancies.stream()
                .limit(10)
                .forEach(v -> sendVacancy(chatId, v));
    }


    private void sendVacancy(long chatId, Vacancy vacancy) {
        String messageText = String.format(
                "🏢 *%s*\n" +
                        "🔹 *Должность:* %s\n" +
                        "🔹 *Зарплата:* %s\n" +
                        "🔹 *Опыт:* %s\n" +
                        "🔹 *График:* %s\n" +
                        "🔹 *Локация:* %s\n" +
                        "[Ссылка на вакансию](%s)",
                vacancy.getEmployerName(),
                vacancy.getTitle(),
                vacancy.getSalary(),
                vacancy.getExperience(),
                vacancy.getScheduleType().getDisplayName(),
                vacancy.getLocation(),
                vacancy.getAlternateUrl()
        );

        SendMessage message = SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(messageText)
                .parseMode("Markdown")
                .disableWebPagePreview(true)
                .build();

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void showProfileSettings(long chatId, UserProfile profile) {
        String settings = String.format(
                "⚙️ *Текущие настройки поиска*\n\n" +
                        "🔹 Профессия: %s\n" +
                        "🔹 Опыт: %s\n" +
                        "🔹 Зарплата: %s руб.\n" +
                        "🔹 Тип занятости: %s\n" +
                        "🔹 Локация: %s\n" +
                        "🔹 Уведомления: %s (%s)",
                profile.getProfession() != null ? profile.getProfession() : "не указано",
                profile.getExperience() != null ? profile.getExperience() : "не указано",
                profile.getSalary() != null ? profile.getSalary() : "не указана",
                profile.getScheduleType().getDisplayName(),
                profile.getLocation() != null ? profile.getLocation() : "любая",
                profile.isNotificationsEnabled() ? "включены 🔔" : "выключены 🔕",
                profile.getNotificationFrequency().getDescription()
        );

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(settings);
        message.setParseMode("Markdown");
        message.setReplyMarkup(createProfileSettingsKeyboard());

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void manageNotifications(long chatId, UserProfile profile) {
        profile.setNotificationsEnabled(!profile.isNotificationsEnabled());

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(String.format(
                "Уведомления %s!",
                profile.isNotificationsEnabled() ? "включены 🔔" : "выключены 🔕"
        ));
        message.setReplyMarkup(createProfileSettingsKeyboard());

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void cancelCurrentOperation(long chatId, UserProfile profile) {
        profile.setCurrentState(State.IDLE);
        sendMessage(chatId, "Возвращение в меню", createMainKeyboard());
    }

    private void sendWelcomeMessage(long chatId) {
        String welcomeText = """
            👋 *Добро пожаловать в HH Job Search Bot!*
            
            Я помогу вам найти вакансии с hh.ru по вашим параметрам:
            
            • 🧑‍💻 Профессия/должность
            • 📅 Опыт работы
            • 💰 Зарплата
            • ⏱️ Тип занятости (удаленно/офис)
            • 📍 Локация
            
            🔔 Я буду присылать уведомления о новых подходящих вакансиях!
            """;

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(welcomeText);
        message.setParseMode("Markdown");
        message.setReplyMarkup(createMainKeyboard());

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private ReplyKeyboardMarkup createMainKeyboard() {
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        List<KeyboardRow> rows = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add("🔍 Поиск вакансий");

        KeyboardRow row2 = new KeyboardRow();
        row2.add("⚙️ Настройки профиля");
        row2.add("🔔 Управление уведомлениями");

        rows.add(row1);
        rows.add(row2);

        keyboard.setKeyboard(rows);
        keyboard.setResizeKeyboard(true);
        return keyboard;
    }

    private ReplyKeyboardMarkup createProfileSettingsKeyboard() {
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        List<KeyboardRow> rows = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add("Отмена");

        rows.add(row);

        keyboard.setKeyboard(rows);
        keyboard.setResizeKeyboard(true);
        return keyboard;
    }

    private ReplyKeyboardMarkup createCancelKeyboard() {
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        List<KeyboardRow> rows = new ArrayList<>();

        KeyboardRow row = new KeyboardRow();
        row.add("Отмена");

        rows.add(row);

        keyboard.setKeyboard(rows);
        keyboard.setResizeKeyboard(true);
        return keyboard;
    }

    private void sendMessage(long chatId, String text) {
        sendMessage(chatId, text, null);
    }

    private void sendMessage(long chatId, String text, ReplyKeyboardMarkup keyboard) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        if (keyboard != null) {
            message.setReplyMarkup(keyboard);
        }

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void showMainMenu(long chatId) {
        sendMessage(chatId, "Выберите действие:", createMainKeyboard());
    }

    private void sendDefaultResponse(long chatId) {
        sendMessage(chatId, "Пожалуйста, используйте кнопки меню для навигации.", createMainKeyboard());
    }

    @Override
    public void onClosing() {
        scheduler.shutdown();
        super.onClosing();
    }
}



