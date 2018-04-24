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
 * Handles a server-side channel.
 **/
public class ChannelHandler extends SimpleChannelInboundHandler<String> {

    private static final String IRC_USER = "Server";
    private static final String CRLF = "\r\n";
    private static final String CRLF_DOUBLE = CRLF + CRLF;
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
        incoming.writeAndFlush("[" + IRC_USER + "] - Welcome to this IRC Server" + CRLF
                + "Commands: " + CRLF
                + "/login username password" + CRLF
                + "/join channel" + CRLF
                + "/leave" + CRLF
                + "/users" + CRLF_DOUBLE);

        channelsGroup.add(incoming);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        Channel incoming = ctx.channel();
        channelsGroup.remove(incoming);
    }

    private void invalidCommand(Channel incoming) {
        // always combine operation if possible when act on the Channel from outise the eventloop
        incoming.eventLoop().execute(() -> incoming.writeAndFlush("[" + IRC_USER + "] - Invalid command." + CRLF_DOUBLE));
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
        incoming.writeAndFlush("[" + IRC_USER + "] - Leaving..." + CRLF_DOUBLE);

        // If the user is inside a channel, notify other users (Logged activity)
        String previousChannelName = channels.get(incoming.id());
        if (previousChannelName != null) {
            String msg = "[" + IRC_USER + "] - " + users.get(incoming.id()) + " has left the channel.";
            addMessageToActivityLogs(previousChannelName, msg);

            channelsGroup.parallelStream()
                    .filter(channel -> previousChannelName.equals(channel.id()))
                    .filter(channel -> !channel.equals(incoming))
                    .forEach(channel -> channel.writeAndFlush(msg + CRLF_DOUBLE)
                    );

            channelsGroup.parallelStream()
                    .filter(channel -> channels.get(channel.id()).equals(previousChannelName) && !channel.equals(incoming))
                    .forEach(channel -> channel.writeAndFlush(msg + CRLF_DOUBLE)
            );
        }
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
            channelsGroup.parallelStream()
                    .filter(channel -> channels.get(channel.id()).equals(channelName) && !channel.equals(incoming))
                    .forEach(channel -> channel.writeAndFlush(message + CRLF));
        } else {
            incoming.writeAndFlush("[" + IRC_USER + "] - You are not in a channel." + CRLF_DOUBLE);
        }
    }

    private synchronized void login(ChannelHandlerContext ctx, String username, String password) {
        Channel incoming = ctx.channel();
        if (userProfiles.containsKey(username)) {
            if (userProfiles.get(username).equals(password)) {
                incoming.writeAndFlush("[" + IRC_USER + "] - User successfully logged in." + CRLF_DOUBLE);
            } else {
                incoming.writeAndFlush("[" + IRC_USER + "] - Wrong password." + CRLF_DOUBLE);
                return;
            }
        } else {
            incoming.writeAndFlush("[" + IRC_USER + "] - User successfully registered." + CRLF_DOUBLE);
            userProfiles.put(username, password);
        }

        users.put(incoming.id(), username);
        channels.remove(incoming.id());
    }

    private synchronized void join(ChannelHandlerContext ctx, String channelName) {
        Channel incoming = ctx.channel();

        // Check if the user is logged in
        if (!users.containsKey(incoming.id())) {
            incoming.writeAndFlush("[" + IRC_USER + "] - You are not logged in." + CRLF_DOUBLE);
            return;
        }

        // If the channel's active client limit is not exceeded, the user can join the channel
        int counter = 0;
        for (String value : channels.values()) {
            if (channelName.equals(value) && ++counter >= MAX_CLIENTS_PER_CHANNEL) {
                incoming.writeAndFlush("[" + IRC_USER + "] - Channel " + channelName + " is currently full." + CRLF_DOUBLE);
                return;
            }
        }

        // If the user was previously in a different channel notify leave to others (Logged activity)
        String previousChannelName = channels.get(incoming.id());
        if (previousChannelName != null) {
            incoming.writeAndFlush("[" + IRC_USER + "] - Left channel " + previousChannelName + "." + CRLF);
            final String msgLeft = "[" + IRC_USER + "] - " + users.get(incoming.id()) + " has left the channel.";
            addMessageToActivityLogs(previousChannelName, msgLeft);
            channelsGroup.parallelStream()
                    .filter(channel -> channel.id().equals(previousChannelName))
                    .filter(channel -> !channel.equals(incoming))
                    .forEach(channel -> channel.writeAndFlush(msgLeft + CRLF_DOUBLE));
        }

        // User joins channel
        incoming.writeAndFlush("[" + IRC_USER + "] - Joined channel " + channelName + "." + CRLF);
        channels.put(incoming.id(), channelName);

        // Notification to other users (Logged activity)
        final String msgJoined = "[" + IRC_USER + "] - " + users.get(incoming.id()) + " has joined the channel.";
        addMessageToActivityLogs(channelName, msgJoined);
        channelsGroup.parallelStream()
                .filter(channel -> channels.get(channel.id()).equals(channelName) && !channel.equals(incoming))
                .forEach(channel -> channel.writeAndFlush(msgJoined + CRLF_DOUBLE));

        // Show this channel's last N messages of activity to the joining user
        if (lastActivityLogs.get(channelName) != null) {
            lastActivityLogs.get(channelName)
                    .parallelStream()
                    .forEach(str -> incoming.writeAndFlush(str + CRLF));
        }
        incoming.writeAndFlush(CRLF);
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
            incoming.writeAndFlush("[" + IRC_USER + "] - List of users in channel " + channelName + ":" + CRLF);
            channelsGroup.parallelStream()
                    .filter(channel -> channelName.equals(channels.get(channel.id())))
                    .forEach(channel -> incoming.writeAndFlush(users.get(channel.id()) + CRLF)
            );
            incoming.writeAndFlush(CRLF);
        } else {
            incoming.writeAndFlush("[" + IRC_USER + "] - You are not in a channel." + CRLF_DOUBLE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // Close the connection when an exception is raised.
        cause.printStackTrace();
        ctx.close();
    }

}