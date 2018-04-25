package com.irc;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.DefaultEventExecutor;

import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Handle a server-side channel.
 **/
public class ChannelHandler extends SimpleChannelInboundHandler<String> {

    private static final String IRC_USER = "Server";
    private static final int MAX_LAST_MESSAGES = 5;
    private static final int MAX_CLIENTS_PER_CHANNEL = 10;

    private static final ChannelGroup channelsGroup = new DefaultChannelGroup(new DefaultEventExecutor());
    private static final ConcurrentMap<ChannelId, String> channels = new ConcurrentHashMap();
    private static final ConcurrentMap<ChannelId, String> users = new ConcurrentHashMap();
    private static final ConcurrentMap<String, String> userProfiles = new ConcurrentHashMap();
    private static final ConcurrentMap<String, Vector<String>> lastActivityLogs = new ConcurrentHashMap();

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        Channel incoming = ctx.channel();
        incoming.writeAndFlush(Messages.format(Messages.BANNER, IRC_USER));
        channelsGroup.add(incoming);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        Channel incoming = ctx.channel();
        channelsGroup.remove(incoming);
    }

    private void invalidCommand(Channel incoming) {
        // always combine operation if possible when act on the Channel from outise the eventloop
        incoming.eventLoop().execute(() -> incoming.writeAndFlush( Messages.format(Messages.INVALID_COMMAND, IRC_USER)) );
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String str) {
        Channel incoming = ctx.channel();
        String[] tokens = str.split("\\s+");

        switch (tokens[0]) {
            case "/login":
                if (tokens.length == 3)
                    login(ctx, tokens[1], tokens[2]);
                else
                    invalidCommand(incoming);
                break;
            case "/join":
                if (tokens.length == 2)
                    join(ctx, tokens[1]);
                else
                    invalidCommand(incoming);
                break;
            case "/leave":
                if (tokens.length == 1)
                    leave(ctx);
                else
                    invalidCommand(incoming);
                break;
            case "/users":
                if (tokens.length == 1)
                    showUsers(ctx);
                else
                    invalidCommand(incoming);
                break;
            default:
                sendMessage(ctx, str);
        }
    }

    private void leave(ChannelHandlerContext ctx) {
        Channel incoming = ctx.channel();
        incoming.writeAndFlush(Messages.format(Messages.LEAVING, IRC_USER));
        String username = users.get(incoming.id());
        // If the user is inside a channel, notify other users (Logged activity)
        String previousChannelName = channels.get(incoming.id());
        if (previousChannelName != null) {
            String msg = Messages.format(Messages.USER_HAS_LEFT, IRC_USER, username);
            addMessageToActivityLogs(previousChannelName, msg);

            channelsGroup.stream()
                    .filter(channel -> previousChannelName.equals(channel.id()))
                    .filter(channel -> !channel.equals(incoming))
                    .forEach(channel -> channel.writeAndFlush(msg + Messages.CRLF_DOUBLE)
                    );

            channelsGroup.stream()
                    .filter(channel -> channels.get(channel.id()).equals(previousChannelName) && !channel.equals(incoming))
                    .forEach(channel -> channel.writeAndFlush(msg + Messages.CRLF_DOUBLE)
            );
        }

        // Check if the user is logged in and remove it
        if (users.containsKey(incoming.id())) {
            users.remove(incoming.id());
        }


        ctx.disconnect();
        // Close connection
        ctx.close();

    }

    private void sendMessage(ChannelHandlerContext ctx, String str) {
        Channel incoming = ctx.channel();
        String channelName = channels.get(incoming.id());
        String message = "[" + users.get(incoming.id()) + "] - " + str;

        // Send text message to the other users in the same channel (Logged activity)
        if (channelName != null) {
            addMessageToActivityLogs(channelName, message);
            channelsGroup.stream()
                    .filter(channel -> channels.get(channel.id()).equals(channelName) && !channel.equals(incoming))
                    .forEach(channel -> channel.writeAndFlush(message + Messages.CRLF));
        } else {
            incoming.writeAndFlush(Messages.format(Messages.YOU_ARE_NOT_IN_A_CHANNEL, IRC_USER));
        }
    }

    private synchronized void login(ChannelHandlerContext ctx, String username, String password) {
        Channel incoming = ctx.channel();
        if (userProfiles.containsKey(username)) {
            if (userProfiles.get(username).equals(password)) {
                incoming.writeAndFlush(Messages.format(Messages.USER_SUCCESSFULLY_LOGGED_IN, IRC_USER));
            } else {
                incoming.writeAndFlush(Messages.format(Messages.WRONG_PASSWORD, IRC_USER));
                return;
            }
        } else {
            incoming.writeAndFlush(Messages.format(Messages.USER_SUCCESSFULLY_REGISTERED, IRC_USER));
            userProfiles.put(username, password);
        }

        users.put(incoming.id(), username);
        channels.remove(incoming.id());
    }

    private synchronized void join(ChannelHandlerContext ctx, String channelName) {
        Channel incoming = ctx.channel();

        // Check if the user is logged in
        if (!users.containsKey(incoming.id())) {
            incoming.writeAndFlush(Messages.format(Messages.YOU_ARE_NOT_LOGGED_IN, IRC_USER));
            return;
        }

        // If the channel's active client limit is not exceeded, the user can join the channel
        int counter = 0;
        for (String value : channels.values()) {
            if (channelName.equals(value) && ++counter >= MAX_CLIENTS_PER_CHANNEL) {
                incoming.writeAndFlush(Messages.format(Messages.CHANNEL_IS_CURRENTLY_FULL,IRC_USER,channelName));
                return;
            }
        }

        // If the user was previously in a different channel notify leave to others (Logged activity)
        String previousChannelName = channels.get(incoming.id());
        if (previousChannelName != null) {
            incoming.writeAndFlush(Messages.format(Messages.LEFT_CHANNEL,IRC_USER,previousChannelName));
            final String msgLeft = Messages.format(Messages.USER_HAS_LEFT_CHANNEL,IRC_USER,users.get(incoming.id()));
            addMessageToActivityLogs(previousChannelName, msgLeft);
            channelsGroup.stream()
                    .filter(channel -> channel.id().equals(previousChannelName))
                    .filter(channel -> !channel.equals(incoming))
                    .forEach(channel -> channel.writeAndFlush(msgLeft + Messages.CRLF_DOUBLE));
        }

        // User joins channel
        incoming.writeAndFlush(Messages.format(Messages.JOINNED_CHANNEL,IRC_USER,channelName));
        channels.put(incoming.id(), channelName);

        // Notification to other users (Logged activity)
        final String msgJoined = Messages.format(Messages.USER_HAS_JOINNED_CHANNEL,IRC_USER, users.get(incoming.id()));
        addMessageToActivityLogs(channelName, msgJoined);
        channelsGroup.stream()
                .filter(channel -> channels.get(channel.id()).equals(channelName) && !channel.equals(incoming))
                .forEach(channel -> channel.writeAndFlush(msgJoined + Messages.CRLF_DOUBLE));

        // Show this channel's last N messages of activity to the joining user
        if (lastActivityLogs.get(channelName) != null) {
            lastActivityLogs.get(channelName)
                    .stream()
                    .forEach(str -> incoming.writeAndFlush(str + Messages.CRLF));
        }
        incoming.writeAndFlush(Messages.CRLF);
    }

    private synchronized void addMessageToActivityLogs(String channelName, String msg) {
        // Add activity to last channel activity logs
        Vector activity = lastActivityLogs.get(channelName);
        if (activity == null) {
            activity = new Vector();
            lastActivityLogs.put(channelName, activity);
        }
        activity.add(msg);
        while (activity.size() > MAX_LAST_MESSAGES)
            activity.remove(0);
    }

    private void showUsers(ChannelHandlerContext ctx) {
        Channel incoming = ctx.channel();
        String channelName = channels.get(incoming.id());
        if (channelName != null) {
            incoming.writeAndFlush(Messages.format(Messages.LIST_OF_USERS_IN_CHANNEL,IRC_USER,channelName));
            channelsGroup.stream()
                    .filter(channel -> channelName.equals(channels.get(channel.id())))
                    .forEach(channel -> incoming.writeAndFlush(users.get(channel.id()) + Messages.CRLF)
            );
            incoming.writeAndFlush(Messages.CRLF);
        } else {
            incoming.writeAndFlush(Messages.format(Messages.YOU_ARE_NOT_IN_A_CHANNEL,IRC_USER));
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // Close the connection when an exception is raised.
        cause.printStackTrace();
        ctx.close();
    }

}