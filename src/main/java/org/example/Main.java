package org.example;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;


public class Main {
    public static void main(String[] args) {
        try {
            String botToken = System.getenv("TELEGRAM_BOT_TOKEN");
            String botUsername = System.getenv("TELEGRAM_BOT_USERNAME");

            if (botToken == null || botUsername == null) {
                throw new IllegalStateException("Не заданы TOKEN или USERNAME бота");
            }
            
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(new HHJobBot(botToken, botUsername));
            System.out.println("Бот успешно запущен!");
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}