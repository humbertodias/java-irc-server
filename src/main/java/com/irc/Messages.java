package com.irc;

import java.text.MessageFormat;

public final class Messages {

    public static final String CRLF = "\r\n";
    public static final String CRLF_DOUBLE = CRLF + CRLF;

    public static String BANNER = "[{0}] - Welcome to this IRC Server" + CRLF
            + "Commands: " + CRLF
            + "/login username password" + CRLF
            + "/join channel" + CRLF
            + "/leave" + CRLF
            + "/users" + CRLF_DOUBLE;

    public static String INVALID_COMMAND = "[{0}] - Invalid command." + CRLF_DOUBLE;
    public static final String LEAVING = "[{0}] - Leaving..." + CRLF_DOUBLE;
    public static final String USER_HAS_LEFT = "[{0}] - {1} has left the channel.";
    public static final String YOU_ARE_NOT_IN_A_CHANNEL = "[{0}] - You are not in a channel." + CRLF_DOUBLE;
    public static final String USER_SUCCESSFULLY_LOGGED_IN = "[{0}] - User successfully logged in." + CRLF_DOUBLE;
    public static final String WRONG_PASSWORD = "[{0}] - Wrong password." + CRLF_DOUBLE;
    public static final String USER_SUCCESSFULLY_REGISTERED = "[{0}] - User successfully registered." + CRLF_DOUBLE;
    public static final String YOU_ARE_NOT_LOGGED_IN = "[{0}] - You are not logged in." + CRLF_DOUBLE;
    public static final String CHANNEL_IS_CURRENTLY_FULL = "[{0}] - Channel {1} is currently full." + CRLF_DOUBLE;
    public static final String LEFT_CHANNEL = "[{0}] - Left channel {1}." + CRLF;
    public static final String USER_HAS_LEFT_CHANNEL = "[{0}] - {1} has left the channel.";
    public static final String JOINNED_CHANNEL = "[{0}] - Joined channel {1}." + CRLF;
    public static final String USER_HAS_JOINNED_CHANNEL = "[{0}] - {1} has joined the channel.";
    public static final String LIST_OF_USERS_IN_CHANNEL = "[{0}] - List of users in channel {1}:" + CRLF;

    public static String format(String message, Object ... parameters){
        MessageFormat messageFormat = new MessageFormat(message);
        return messageFormat.format(parameters);
    }
}
