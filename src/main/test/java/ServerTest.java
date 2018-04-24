import com.irc.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ServerTest  {

    private EmbeddedChannel channel;

    @Before
    public void setUp() {
        channel = new EmbeddedChannel(new ChannelHandler());
    }

    @Test
    public void loginTest() {
        // clean buffer
        channel.releaseOutbound();
        channel.writeInbound("/login user1 pass1\r\n");
        assertEquals("[Server] - User successfully registered.\r\n\r\n", channel.readOutbound());
    }

    @Test
    public void joinTest(){
        channel.writeInbound("/login user1 pass1\r\n");
        // clean buffer
        channel.releaseOutbound();
        channel.writeInbound("/join room_1\r\n");
        assertEquals("[Server] - Joined channel room_1.\r\n", channel.readOutbound());
    }

    @Test
    public void leaveTest(){
        channel.writeInbound("/login user1 pass1\r\n");
        // clean buffer
        channel.releaseOutbound();
        channel.writeInbound("/leave\r\n");
        assertEquals("[Server] - Leaving...\r\n\r\n", channel.readOutbound());
    }

    @Test
    public void usersTest(){
        channel.writeInbound("/login user1 pass1\r\n");
        channel.writeInbound("/join room_1\r\n");
        channel.writeInbound("/login user2 pass2\r\n");
        channel.writeInbound("/join room_1\r\n");
        channel.writeInbound("/login user3 pass3\r\n");
        channel.writeInbound("/join room_1\r\n");
        // clean buffer
        channel.releaseOutbound();
        channel.writeInbound("/users\r\n");

        String read;
        StringBuilder sb = new StringBuilder();
        while((read = channel.readOutbound()) != null)
            sb.append(read);

        assertEquals("[Server] - List of users in channel room_1:\r\nuser3\r\n\r\n", sb.toString());
    }

    @Test
    public void invalidCommandTest() {
        // clean buffer
        channel.releaseOutbound();
        channel.writeInbound("/login a b c d e\r\n");
        assertEquals("[Server] - Invalid command.\r\n\r\n", channel.readOutbound());
    }

}
