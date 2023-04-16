package com.example.telegramdailybot.service;

import com.example.telegramdailybot.config.TelegramDailyBotProperties;
import com.example.telegramdailybot.model.Notification;
import com.example.telegramdailybot.repository.NotificationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final TelegramDailyBotProperties telegramDailyBotProperties;

    @Autowired
    public NotificationService(NotificationRepository notificationRepository, TelegramDailyBotProperties telegramDailyBotProperties) {
        this.notificationRepository = notificationRepository;
        this.telegramDailyBotProperties = telegramDailyBotProperties;
    }

    public Notification save(Notification notification) {
        return notificationRepository.save(notification);
    }

    public Optional<Notification> findById(int id) {
        return notificationRepository.findById(id);
    }

    public List<Notification> findByChatid(long chatId) {
        return notificationRepository.findByChatid(chatId);
    }

    public String generateNotificationListMessage(Long chatId, List<String> fieldsToDisplay, Map<String, String> customHeaders) {
        List<Notification> notifications = findByChatid(chatId);
        if (notifications.isEmpty()) {
            return "Уведомления для этого чата отсутствуют";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Уведомления для этого чата: \n\n");

        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

        for (Notification notification : notifications) {
            for (String field : fieldsToDisplay) {
                switch (field) {
                    case "id" -> {
                        String customHeader = customHeaders.getOrDefault(field, field);
                        sb.append(customHeader).append(notification.getId()).append("\n");
                    }
                    case "chatid" -> {
                        String customHeader = customHeaders.getOrDefault(field, field);
                        sb.append(customHeader).append(notification.getChatid()).append("\n");
                    }
                    case "text" -> {
                        String customHeader = customHeaders.getOrDefault(field, field);
                        sb.append(customHeader).append(notification.getText()).append("\n");
                    }
                    case "datetime" -> {
                        String customHeader = customHeaders.getOrDefault(field, field);
                        sb.append(customHeader).append(notification.getDatetime().withZoneSameInstant(telegramDailyBotProperties.getTimeZone()).format(dateTimeFormatter)).append("\n");
                    }
                    case "repetition" -> {
                        String customHeader = customHeaders.getOrDefault(field, field);
                        sb.append(customHeader).append(notification.getRepetition()).append("\n");
                    }
                    case "datetimexcluded" -> {
                        if (notification.getDatetimexcluded() != null) {
                            String customHeader = customHeaders.getOrDefault(field, field);
                            sb.append(customHeader);
                            if (notification.getDatetimexcluded().get("weekends").asBoolean()) {
                                sb.append("  - Исключить СБ и ВС\n");
                            }
                            ArrayNode skipDays = (ArrayNode) notification.getDatetimexcluded().get("skip_days");
                            if (skipDays != null) {
                                sb.append("  - Исключить дни: \n");
                                for (JsonNode skipDay : skipDays) {
                                    int frequency = skipDay.get("frequency").asInt();
                                    String dayStr = skipDay.get("day").asText();
                                    sb.append("    * ").append(dayStr).append(" (every ").append(frequency).append(" days)\n");
                                }
                            }
                        }
                    }
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
