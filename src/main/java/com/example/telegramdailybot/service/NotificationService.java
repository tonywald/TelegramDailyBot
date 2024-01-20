package com.example.telegramdailybot.service;

import com.example.telegramdailybot.config.TelegramDailyBotProperties;
import com.example.telegramdailybot.model.Notification;
import com.example.telegramdailybot.model.ParseResult;
import com.example.telegramdailybot.repository.NotificationRepository;
import com.example.telegramdailybot.util.BotUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Service
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final TelegramDailyBotProperties telegramDailyBotProperties;
    private final ChatService chatService;

    @Autowired
    public NotificationService(NotificationRepository notificationRepository, TelegramDailyBotProperties telegramDailyBotProperties, ChatService chatService) {
        this.notificationRepository = notificationRepository;
        this.telegramDailyBotProperties = telegramDailyBotProperties;
        this.chatService = chatService;
    }

    @Transactional
    public String addNotificationFromText(String text, long chatId) {
        // Parse the notification from the message text
        ParseResult parseResult = BotUtils.parseNotificationText(text, telegramDailyBotProperties.getTimeZone());
        if (parseResult.hasError()) {
            // Send an error message if the text could not be parsed
            return "Error adding notification. " + parseResult.getErrorMessage();
        }
        Notification notification = parseResult.getNotification();
        // Set the chat ID
        notification.setChatid(chatId);

        // Save the notification
        save(notification);

        return "Notification added successfully";
    }

    @Transactional
    public void deleteNotificationsFromText(String text, long chatId, long userId) {
        String[] lines = text.split("\\n");

        for (String line : lines) {
            int notificationIdToDelete = Integer.parseInt(line);
            Notification notification = findById(notificationIdToDelete).orElse(null);

            if (notification != null) {
                if (chatService.isAdmin(userId) || notification.getChatid() == chatId) {
                    deleteById(notificationIdToDelete);
                }
            }
        }
    }

    @Transactional
    public String editNotificationFromText(String text, long chatId, long userId) {
        // Extract the ID from the text
        String regex = "ID:\\s*(\\d+)";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            int id = Integer.parseInt(matcher.group(1));

            // Parse the notification from the message text
            ParseResult parseResult = BotUtils.parseNotificationText(text, telegramDailyBotProperties.getTimeZone());
            if (parseResult.hasError()) {
                // Send an error message if the text could not be parsed
                return "Error editing notification. " + parseResult.getErrorMessage();
            }
            Notification notificationUpdated = parseResult.getNotification();

            Notification notificationCurrent = findById(id).orElse(null);

            if (notificationCurrent != null) {
                if (chatService.isAdmin(userId) || notificationCurrent.getChatid() == chatId) {
                    notificationCurrent.setText(notificationUpdated.getText());
                    notificationCurrent.setDatetime(notificationUpdated.getDatetime());
                    notificationCurrent.setRepetition(notificationUpdated.getRepetition());
                    notificationCurrent.setDatetimexcluded(notificationUpdated.getDatetimexcluded());
                    // Save the notification to the database
                    save(notificationCurrent);
                }
            }
            return "Notification successfully edited";
        } catch (NumberFormatException e) {
            return "Error parsing ID. Please check the format and try again.";
        }
    }

    public Notification save(Notification notification) {
        return notificationRepository.save(notification);
    }

    public List<Notification> findAll() {
        return notificationRepository.findAll();
    }

    public void delete(Notification notification) {
        notificationRepository.delete(notification);
    }

    public void deleteById(int id) {
        notificationRepository.deleteById(id);
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
            return "There are no notifications for this chat";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Notifications for this chat: \n\n");

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
                                sb.append("  - Exclude Sat and Sun\n");
                            }
                            ArrayNode skipDays = (ArrayNode) notification.getDatetimexcluded().get("skip_days");
                            if (skipDays != null) {
                                sb.append("  - Exclude days: \n");
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
