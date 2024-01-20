package com.example.telegramdailybot;


import com.example.telegramdailybot.config.TelegramDailyBotProperties;
import com.example.telegramdailybot.controller.ChatManagementController;
import com.example.telegramdailybot.controller.NotificationManagementController;
import com.example.telegramdailybot.controller.UserManagementController;
import com.example.telegramdailybot.model.UserActionState;
import com.example.telegramdailybot.service.ChatGPT3Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.HashMap;
import java.util.Map;


@Component
public class TelegramDailyBot extends TelegramLongPollingBot {

    private static final Logger logger = LoggerFactory.getLogger(TelegramDailyBot.class);
    private final ChatGPT3Service chatGpt3Service;
    private final TelegramDailyBotProperties properties;

    private final Map<Long, UserActionState> userActionStates = new HashMap<>();

    private final UserManagementController userManagementController;
    private final NotificationManagementController notificationManagementController;
    private final ChatManagementController chatManagementController;

    @Autowired
    public TelegramDailyBot(ChatGPT3Service chatGpt3Service,
                            TelegramDailyBotProperties properties,
                            UserManagementController userManagementController,
                            NotificationManagementController notificationManagementController,
                            ChatManagementController chatManagementController) {
        super(properties.getBotToken());
        this.chatGpt3Service = chatGpt3Service;
        this.properties = properties;
        this.userManagementController = userManagementController;
        this.notificationManagementController = notificationManagementController;
        this.chatManagementController = chatManagementController;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            handleMessageWithText(update);
        } else if (update.hasCallbackQuery()) {
            handleCallbackQuery(update);
        }
    }

    private void handleMessageWithText(Update update) {
        if (update.getMessage().isCommand()) {
            handleCommand(update);
        } else {
            handleNonCommandTextMessage(update);
        }
    }

    private void handleCommand(Update update) {
        String command = update.getMessage().getText().split("@")[0]; // Remove the username from the command
        Long chatId = update.getMessage().getChatId();

        if (!chatManagementController.existsById(chatId) && !"/start".equalsIgnoreCase(command) && !"/getchatid".equalsIgnoreCase(command)) {
            sendChatMessage(chatId, "You are not authorized to use this bot.");
            return;
        }

        switch (command.toLowerCase()) {
            case "/start" -> handleStartCommand(chatId);
            case "/getchatid" -> handleGetChatIdCommand(chatId);
            case "/next" -> sendChatMessage(userManagementController.nextWinner(update));
            case "/resetwinners" -> sendChatMessage(userManagementController.resetWinners(update));
            case "/showusers" -> sendChatMessage(userManagementController.showUsers(update));
            case "/shownotifications" -> sendChatMessage(notificationManagementController.showNotifications(update));
            case "/editusers" -> sendChatMessage(userManagementController.editUsersMessage(update, userActionStates));
            case "/editnotifications" ->
                    sendChatMessage(notificationManagementController.editNotificationsMessage(update, userActionStates));
            case "/editchats" -> sendChatMessage(chatManagementController.editChatsMessage(update));
            case "/askchatgpt3" -> askChatGPT3(update.getMessage(), chatId);
            default -> sendChatMessage(chatId, "Unknown command. Please use /start or /getchatid to get started.");
        }
    }


    private void handleNonCommandTextMessage(Update update) {
        Long userId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText();
        UserActionState userActionState = userActionStates.get(userId);

        if (userActionState == null) {
            logger.warn("UserActionState is null for user: {}", userId);
            // You can either return here or set a default value for userActionState
            sendChatMessage(chatId, "First, select an option from the menu.(?)");
            return;
        }

        switch (userActionState) {
            case WAITING_FOR_USERS_TO_ADD ->
                    sendChatMessage(userManagementController.addUsers(update, userActionStates));
            case WAITING_FOR_USERS_TO_DELETE ->
                    sendChatMessage(userManagementController.deleteUsers(update, userActionStates));
            case WAITING_FOR_USERS_TO_EDIT ->
                    sendChatMessage(userManagementController.editUsers(update, userActionStates));
            case WAITING_FOR_NOTIFICATION_TO_ADD ->
                    sendChatMessage(notificationManagementController.addNotification(update, userActionStates));
            case WAITING_FOR_NOTIFICATION_TO_DELETE ->
                    sendChatMessage(notificationManagementController.deleteNotifications(update, userActionStates));
            case WAITING_FOR_NOTIFICATION_TO_EDIT ->
                    sendChatMessage(notificationManagementController.editNotification(update, userActionStates));
            case WAITING_FOR_CHATS_TO_ADD ->
                    sendChatMessage(chatManagementController.addChats(update, userActionStates));
            case WAITING_FOR_CHATS_TO_DELETE ->
                    sendChatMessage(chatManagementController.deleteChats(update, userActionStates));
            case WAITING_FOR_CHATS_TO_EDIT ->
                    sendChatMessage(chatManagementController.editChats(update, userActionStates));
            case WAITING_FOR_CHATGPT3_QUERY -> {
                sendChatMessage(chatId, "Please wait, ChatGPT3 is processing your query...");
                // Remove the user from the userAddingStates map
                userActionStates.remove(userId);
                chatGpt3Service.chat(text).thenAcceptAsync(responseText -> sendChatMessage(chatId, responseText));
            }
            case WAITING_FOR_CHAT_ID_TO_EDIT_USERS ->
                    sendChatMessage(userManagementController.editUsersByAdmin(update, userActionStates));
            case WAITING_FOR_CHAT_ID_TO_EDIT_NOTIFICATIONS ->
                    sendChatMessage(notificationManagementController.editNotificationsByAdmin(update, userActionStates));
            case WAITING_FOR_CHAT_ID_TO_ADD_USERS ->
                    sendChatMessage(userManagementController.addUsersByAdmin(update, userActionStates));
            case WAITING_FOR_CHAT_ID_TO_ADD_NOTIFICATION ->
                    sendChatMessage(notificationManagementController.addNotificationByAdmin(update, userActionStates));
        }
    }

    private void handleCallbackQuery(Update update) {
        CallbackQuery callbackQuery = update.getCallbackQuery();
        String data = callbackQuery.getData();

        switch (data) {
            case "add_users" ->
                    sendChatMessage(userManagementController.initiateAddUsersProcess(update, userActionStates));
            case "delete_users" ->
                    sendChatMessage(userManagementController.initiateDeleteUsersProcess(update, userActionStates));
            case "edit_users" ->
                    sendChatMessage(userManagementController.initiateEditUsersProcess(update, userActionStates));
            case "add_notification" ->
                    sendChatMessage(notificationManagementController.initiateAddNotificationProcess(update, userActionStates));
            case "delete_notifications" ->
                    sendChatMessage(notificationManagementController.initiateDeleteNotificationsProcess(update, userActionStates));
            case "edit_notification" ->
                    sendChatMessage(notificationManagementController.initiateEditNotificationProcess(update, userActionStates));
            case "add_chats" ->
                    sendChatMessage(chatManagementController.initiateAddChatsProcess(update, userActionStates));
            case "delete_chats" ->
                    sendChatMessage(chatManagementController.initiateDeleteChatsProcess(update, userActionStates));
            case "edit_chats" ->
                    sendChatMessage(chatManagementController.initiateEditChatsProcess(update, userActionStates));
        }

        // Acknowledge the callback query
        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackQuery.getId());
        try {
            execute(answer);
        } catch (TelegramApiException e) {
            logger.error("Error answering callback query", e);
        }
    }

    private void handleStartCommand(Long chatId) {
        String welcomeMessage = """
                🎉 Welcome to AdminNotifier Bot 2.0! 🤖

                🌟 The fastest way to find an admin.. for now. 📅

                🚀 What can we do together:
                1️⃣ User lottery: choose winners and add new participants 🏆
                2️⃣ Personalized notifications: create and edit reminders 🔔
                3️⃣ Smart answers with ChatGPT: ask questions and get detailed answers 🧠💬

                🤩 Enjoy using it! Together we will make your chat more productive and fun! 🎯""";

        sendChatMessage(chatId, welcomeMessage);
    }

    private void handleGetChatIdCommand(Long chatId) {
        sendChatMessage(chatId, "Your chat ID: " + chatId);
    }

    private void askChatGPT3(Message message, Long chatId) {

        Long userId = message.getFrom().getId();

        userActionStates.put(userId, UserActionState.WAITING_FOR_CHATGPT3_QUERY);

        sendChatMessage(chatId, "Write your question to ChatGPT3");
    }


    private void sendChatMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setDisableWebPagePreview(true);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            logger.error("Error sending message to chat: {}", chatId, e);
        }
    }

    private void sendChatMessage(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            logger.error("Error sending message to chat: {}", message.getChatId(), e);
        }
    }

    @Override
    public String getBotUsername() {
        return properties.getBotUsername();
    }
}

