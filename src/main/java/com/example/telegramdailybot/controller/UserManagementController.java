package com.example.telegramdailybot.controller;

import com.example.telegramdailybot.model.User;
import com.example.telegramdailybot.model.UserActionState;
import com.example.telegramdailybot.service.ChatService;
import com.example.telegramdailybot.service.UserService;
import com.example.telegramdailybot.util.BotUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Controller
public class UserManagementController {

    private final UserService userService;
    private final ChatService chatService;

    @Autowired
    public UserManagementController(UserService userService, ChatService chatService) {
        this.userService = userService;
        this.chatService = chatService;
    }

    public SendMessage resetWinners(Update update) {
        userService.resetWinners(update.getMessage().getChatId());
        SendMessage message = new SendMessage();
        message.setChatId(update.getMessage().getChatId());
        message.setText("Winners reset!");
        return message;
    }

    public SendMessage showUsers(Update update) {
        List<String> fieldsToDisplay = Arrays.asList("name", "username", "haswon");
        Map<String, String> customHeaders = new HashMap<>();
        customHeaders.put("name", "Name");
        customHeaders.put("haswon", "Won");
        SendMessage message = new SendMessage();
        message.setChatId(update.getMessage().getChatId());
        message.setText(userService.generateUserListMessage(update.getMessage().getChatId(), fieldsToDisplay, customHeaders));
        return message;
    }

    public SendMessage nextWinner(Update update) {

        User winner = findWinner(update.getMessage().getChatId());
        String text;

        if (winner != null) {
            text = "Entrant " + winner.getName() + ", @" + winner.getUsername() + " won!";
        } else text = "There are no participants for the drawing in this chat";

        SendMessage message = new SendMessage();
        message.setChatId(update.getMessage().getChatId());
        message.setText(text);
        return message;
    }

    public User findWinner(Long chatId) {
        return userService.findWinner(chatId);
    }


    public SendMessage editUsersMessage(Update update, Map<Long, UserActionState> userActionStates) {
        long chatId = update.getMessage().getChatId();
        boolean isUserChat = update.getMessage().getChat().isUserChat();
        boolean isAdmin = chatService.isAdmin(update.getMessage().getFrom().getId());
        if (isUserChat && isAdmin) {
            userActionStates.put(chatId, UserActionState.WAITING_FOR_CHAT_ID_TO_EDIT_USERS);
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText("Enter chat ID to edit users:");
            return message;
        } else {
            List<String> fieldsToDisplay = Arrays.asList("id", "name", "username", "haswon");
            Map<String, String> customHeaders = new HashMap<>();
            customHeaders.put("name", "Name");
            customHeaders.put("haswon", "Won");
            String text = userService.generateUserListMessage(chatId, fieldsToDisplay, customHeaders);
            text = text + "\n Choose an action:";
            InlineKeyboardMarkup inlineKeyboardMarkup = BotUtils.createInlineKeyboardMarkup("add_users", "delete_users", "edit_users");

            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText(text);
            message.setReplyMarkup(inlineKeyboardMarkup);
            return message;
        }
    }


    public SendMessage addUsers(Update update, Map<Long, UserActionState> userActionStates) {
        // Parse and add users from the message text
        String text = update.getMessage().getText();
        userService.addUsersFromText(text, update.getMessage().getChatId());

        // Remove the user from the userAddingStates map
        userActionStates.remove(update.getMessage().getFrom().getId());

        // Send a confirmation message to the user
        SendMessage message = new SendMessage();
        message.setChatId(update.getMessage().getChatId());
        message.setText("Participants added successfully");

        return message;
    }

    public SendMessage deleteUsers(Update update, Map<Long, UserActionState> userActionStates) {
        String text = update.getMessage().getText();
        long chatId = update.getMessage().getChatId();
        long userId = update.getMessage().getFrom().getId();

        userService.deleteUsersFromText(text, chatId, userId);

        // Remove the user from the userDeletingStates map
        userActionStates.remove(userId);

        // Send a confirmation message to the user
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Members successfully deleted");

        return message;
    }

    public SendMessage editUsers(Update update, Map<Long, UserActionState> userActionStates) {
        String text = update.getMessage().getText();
        long chatId = update.getMessage().getChatId();
        long userId = update.getMessage().getFrom().getId();

        userService.editUsersFromText(text, chatId, userId);

        // Remove the user from the userDeletingStates map
        userActionStates.remove(userId);

        // Send a confirmation message to the user
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Members successfully edited");

        return message;
    }

    public SendMessage editUsersByAdmin(Update update, Map<Long, UserActionState> userActionStates) {
        long chatId = update.getMessage().getChatId();
        long userId = update.getMessage().getFrom().getId();

        try {
            Long targetChatId = Long.parseLong(update.getMessage().getText());
            userActionStates.remove(userId);

            List<String> fieldsToDisplay = Arrays.asList("id", "name", "username", "haswon");
            Map<String, String> customHeaders = new HashMap<>();
            customHeaders.put("name", "Name");
            customHeaders.put("haswon", "Won");
            String text = userService.generateUserListMessage(targetChatId, fieldsToDisplay, customHeaders);
            text = text + "\n Choose an action:";
            InlineKeyboardMarkup inlineKeyboardMarkup = BotUtils.createInlineKeyboardMarkup("add_users", "delete_users", "edit_users");

            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText(text);
            message.setReplyMarkup(inlineKeyboardMarkup);
            return message;

        } catch (NumberFormatException e) {
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText("Chat ID format is incorrect. Enter the correct chat ID:");
            return message;
        }
    }

    public SendMessage addUsersByAdmin(Update update, Map<Long, UserActionState> userActionStates) {
        // Parse and add users from the message text
        String[] parts = update.getMessage().getText().split("\n", 2);
        try {
            long targetChatId = Long.parseLong(parts[0]);
            String text = parts[1];

            userService.addUsersFromText(text, targetChatId);

            // Remove the user from the userAddingStates map
            userActionStates.remove(update.getMessage().getFrom().getId());

            // Send a confirmation message to the user
            SendMessage message = new SendMessage();
            message.setChatId(update.getMessage().getChatId());
            message.setText("Participants added successfully");

            return message;
        } catch (NumberFormatException e) {
            SendMessage message = new SendMessage();
            message.setChatId(update.getMessage().getChatId());
            message.setText("Chat ID format is incorrect. Enter the correct chat ID:");
            return message;
        }
    }

    public SendMessage initiateAddUsersProcess(Update update, Map<Long, UserActionState> userActionStates) {
        boolean isUserChat = update.getCallbackQuery().getMessage().getChat().isUserChat();
        boolean isAdmin = chatService.isAdmin(update.getCallbackQuery().getFrom().getId());
        if (isUserChat && isAdmin) {
            userActionStates.put(update.getCallbackQuery().getFrom().getId(), UserActionState.WAITING_FOR_CHAT_ID_TO_ADD_USERS);
            String text = """
                    In the first line, send the ID of the chat to which you want to add participants. Next, send from new lines, separated by commas: name, @username. For example:
                                        
                    -123456789
                    Vasya,@vasyatelegram
                    Petya,@evilusername
                    Evelina,@evacool""";
            SendMessage message = new SendMessage();
            message.setChatId(update.getCallbackQuery().getMessage().getChatId());
            message.setText(text);
            return message;
        } else {
            userActionStates.put(update.getCallbackQuery().getFrom().getId(), UserActionState.WAITING_FOR_USERS_TO_ADD);
            String text = """
                    Please send, separated by commas: name, @username. For example:

                    Vasya,@vasyatelegram
                    Petya,@evilusername
                    Evelina,@evacool""";
            SendMessage message = new SendMessage();
            message.setChatId(update.getCallbackQuery().getMessage().getChatId());
            message.setText(text);
            return message;
        }
    }

    public SendMessage initiateDeleteUsersProcess(Update update, Map<Long, UserActionState> userActionStates) {
        userActionStates.put(update.getCallbackQuery().getFrom().getId(), UserActionState.WAITING_FOR_USERS_TO_DELETE);
        String text = """
                Please send the IDs of the members you want to remove, each ID on a new line. For example:

                10
                11
                12""";
        SendMessage message = new SendMessage();
        message.setChatId(update.getCallbackQuery().getMessage().getChatId());
        message.setText(text);
        return message;
    }

    public SendMessage initiateEditUsersProcess(Update update, Map<Long, UserActionState> userActionStates) {
        userActionStates.put(update.getCallbackQuery().getFrom().getId(), UserActionState.WAITING_FOR_USERS_TO_EDIT);
        String text = """
                Please send, separated by commas: ID of the participant you want to change, name, username. For example:

                10,Vasya,vasyatelegram
                11,Petya,evilusername
                12,Evelina,evacool""";
        SendMessage message = new SendMessage();
        message.setChatId(update.getCallbackQuery().getMessage().getChatId());
        message.setText(text);
        return message;
    }

}

