package com.example.telegramdailybot.model;

public enum UserActionState {
    WAITING_FOR_USERS_TO_ADD,
    WAITING_FOR_USERS_TO_DELETE,
    WAITING_FOR_USERS_TO_EDIT,
    WAITING_FOR_NOTIFICATION_TO_ADD,
    WAITING_FOR_NOTIFICATION_TO_DELETE,
    WAITING_FOR_NOTIFICATION_TO_EDIT,
    WAITING_FOR_CHATS_TO_ADD,
    WAITING_FOR_CHATS_TO_DELETE,
    WAITING_FOR_CHATS_TO_EDIT,
    WAITING_FOR_CHATGPT3_QUERY,
    WAITING_FOR_CHAT_ID_TO_EDIT_USERS,
    WAITING_FOR_CHAT_ID_TO_EDIT_NOTIFICATIONS
}
