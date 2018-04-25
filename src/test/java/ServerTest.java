import com.irc.ChannelHandler;
import com.irc.Messages;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Before;
import org.junit.Test;
import java.util.stream.IntStream;

import static com.irc.Messages.*;
import static org.junit.Assert.assertEquals;

/**
 * Tests.
 */
public class ServerTest  {

    private EmbeddedChannel channel;

    @Before
    public void setUp() {
        channel = new EmbeddedChannel(new ChannelHandler());
    }

    @Test
    public void bannerTest() {
        assertEquals(Messages.format(WELCOME_BANNER,"Server"), channel.readOutbound());
    }

    @Test
    public void loginNewUserTest() {
        // clean buffer
        channel.releaseOutbound();
        channel.writeInbound("/login newuser password" + CRLF);
        assertEquals(Messages.format(USER_SUCCESSFULLY_REGISTERED,"Server"), channel.readOutbound());
    }

    @Test
    public void loginExistingUserTest() {
        // clean buffer
        channel.writeInbound("/login existinguser password" + CRLF);
        channel.writeInbound("/leave" + CRLF);
        channel = new EmbeddedChannel(new ChannelHandler());

        channel.releaseOutbound();
        channel.writeInbound("/login existinguser password" + CRLF);
        assertEquals(Messages.format(USER_SUCCESSFULLY_LOGGED_IN,"Server"), channel.readOutbound());
    }

    @Test
    public void loginWrongPasswordTest() {
        // clean buffer
        channel.writeInbound("/login userwrongpassword password" + CRLF);
        channel.writeInbound("/leave" + CRLF);
        channel = new EmbeddedChannel(new ChannelHandler());
        channel.releaseOutbound();
        channel.writeInbound("/login userwrongpassword anotherpassword" + CRLF);
        assertEquals(Messages.format(WRONG_PASSWORD,"Server"), channel.readOutbound());
    }

    @Test
    public void joinTest(){
        channel.writeInbound("/login userjoinok password" + CRLF);
        // clean buffer
        channel.releaseOutbound();
        channel.writeInbound("/join room_1" + CRLF);
        assertEquals(Messages.format(JOINNED_CHANNEL,"Server","room_1"), channel.readOutbound());
    }

    @Test
    public void joinNotLoggedTest(){
        channel.writeInbound("/login userjoinnotlogged password" + CRLF);
        channel.writeInbound("/leave" + CRLF);
        channel = new EmbeddedChannel(new ChannelHandler());
        channel.releaseOutbound();
        channel.writeInbound("/join room_not_logged" + CRLF);
        assertEquals(Messages.format(YOU_ARE_NOT_LOGGED_IN,"Server"), channel.readOutbound());
    }

    @Test
    public void joinFullRoomTest(){
        channel = new EmbeddedChannel(new ChannelHandler());
        IntStream.rangeClosed(1,11).forEach( (index) -> {
            channel.writeInbound("/login full" + index +  " password" + CRLF);
            channel.releaseOutbound();
            channel.writeInbound("/join room_full" + CRLF);
        });
        channel.writeInbound("/join room_full" + CRLF);
        assertEquals(Messages.format(JOINNED_CHANNEL,"Server", "room_full"), channel.readOutbound());
    }

    @Test
    public void leaveTest(){
        channel.writeInbound("/login userleave password" + CRLF);
        // clean buffer
        channel.releaseOutbound();
        channel.writeInbound("/leave" + CRLF);
        assertEquals(Messages.format(LEAVING,"Server"), channel.readOutbound());
    }

    @Test
    public void usersTest(){
        channel.writeInbound("/login users1 pass1" + CRLF);
        channel.writeInbound("/join room_1" + CRLF);
        channel.writeInbound("/login users2 pass2" + CRLF);
        channel.writeInbound("/join room_1" + CRLF);
        channel.writeInbound("/login users3 pass3" + CRLF);
        channel.writeInbound("/join room_1" + CRLF);
        // clean buffer
        channel.releaseOutbound();
        channel.writeInbound("/users" + CRLF);

        String read;
        StringBuilder sb = new StringBuilder();
        while((read = channel.readOutbound()) != null)
            sb.append(read);

        assertEquals(Messages.format(LIST_OF_USERS_IN_CHANNEL ,"Server","room_1")+"users3\r\n" + CRLF, sb.toString());
    }

    @Test
    public void invalidCommandTest() {
        // clean buffer
        channel.releaseOutbound();
        channel.writeInbound("/login a b c d e" + CRLF);
        assertEquals(Messages.format(INVALID_COMMAND,"Server"), channel.readOutbound());
    }

    @Test
    public void sendingMessageNotLoggedTest() {
        // clean buffer
        channel.writeInbound("/login userssendingmessagenotlogged password" + CRLF);
        channel.releaseOutbound();
        channel.writeInbound("SENDING MESSAGE" + CRLF);
        assertEquals(Messages.format(YOU_ARE_NOT_IN_A_CHANNEL,"Server"), channel.readOutbound());
    }

}
