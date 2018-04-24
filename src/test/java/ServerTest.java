import com.irc.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Before;
import org.junit.Test;

import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

public class ServerTest  {

    private EmbeddedChannel channel = new EmbeddedChannel(new ChannelHandler());

    @Before
    public void setUp() {
    }

    @Test
    public void loginNewUserTest() {
        // clean buffer
        channel.releaseOutbound();
        channel.writeInbound("/login newuser password\r\n");
        assertEquals("[Server] - User successfully registered.\r\n\r\n", channel.readOutbound());
    }

    @Test
    public void loginExistingUserTest() {
        // clean buffer
        channel.writeInbound("/login existinguser password\r\n");
        channel.writeInbound("/leave\r\n");
        channel = new EmbeddedChannel(new ChannelHandler());

        channel.releaseOutbound();
        channel.writeInbound("/login existinguser password\r\n");
        assertEquals("[Server] - User successfully logged in.\r\n\r\n", channel.readOutbound());
    }

    @Test
    public void loginWrongPasswordTest() {
        // clean buffer
        channel.writeInbound("/login userwrongpassword password\r\n");
        channel.writeInbound("/leave\r\n");
        channel = new EmbeddedChannel(new ChannelHandler());
        channel.releaseOutbound();
        channel.writeInbound("/login userwrongpassword anotherpassword\r\n");
        assertEquals("[Server] - Wrong password.\r\n\r\n", channel.readOutbound());
    }

    @Test
    public void joinTest(){
        channel.writeInbound("/login userjoinok password\r\n");
        // clean buffer
        channel.releaseOutbound();
        channel.writeInbound("/join room_1\r\n");
        assertEquals("[Server] - Joined channel room_1.\r\n", channel.readOutbound());
    }

    @Test
    public void joinNotLoggedTest(){
        channel.writeInbound("/login userjoinnotlogged password\r\n");
        channel.writeInbound("/leave\r\n");
        channel = new EmbeddedChannel(new ChannelHandler());
        channel.releaseOutbound();
        channel.writeInbound("/join room_not_logged\r\n");
        assertEquals("[Server] - You are not logged in.\r\n\r\n", channel.readOutbound());
    }

    @Test
    public void joinFullRoomTest(){
        channel = new EmbeddedChannel(new ChannelHandler());
        IntStream.rangeClosed(1,11).forEach( (index) -> {
            channel.writeInbound("/login full" + index +  " password\r\n");
            channel.releaseOutbound();
            channel.writeInbound("/join room_full\r\n");
        });
        channel.writeInbound("/join room_full\r\n");
        assertEquals("[Server] - Joined channel room_full.\r\n", channel.readOutbound());
    }

    @Test
    public void leaveTest(){
        channel.writeInbound("/login userleave password\r\n");
        // clean buffer
        channel.releaseOutbound();
        channel.writeInbound("/leave\r\n");
        assertEquals("[Server] - Leaving...\r\n\r\n", channel.readOutbound());
    }

    @Test
    public void usersTest(){
        channel = new EmbeddedChannel(new ChannelHandler());
        channel.writeInbound("/login users1 pass1\r\n");
        channel.writeInbound("/join room_1\r\n");
        channel.writeInbound("/login users2 pass2\r\n");
        channel.writeInbound("/join room_1\r\n");
        channel.writeInbound("/login users3 pass3\r\n");
        channel.writeInbound("/join room_1\r\n");
        // clean buffer
        channel.releaseOutbound();
        channel.writeInbound("/users\r\n");

        String read;
        StringBuilder sb = new StringBuilder();
        while((read = channel.readOutbound()) != null)
            sb.append(read);

        assertEquals("[Server] - List of users in channel room_1:\r\nusers3\r\n\r\n", sb.toString());
    }

    @Test
    public void invalidCommandTest() {
        // clean buffer
        channel.releaseOutbound();
        channel.writeInbound("/login a b c d e\r\n");
        assertEquals("[Server] - Invalid command.\r\n\r\n", channel.readOutbound());
    }

}
