import com.irc.ChannelHandler;
import com.irc.Messages;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Before;
import org.junit.Test;

import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

public class ServerTest  {

    private EmbeddedChannel channel;

    @Before
    public void setUp() {
        channel = new EmbeddedChannel(new ChannelHandler());
    }

    @Test
    public void bannerTest() {
        assertEquals(Messages.format(Messages.BANNER,"Server"), channel.readOutbound());
    }

    @Test
    public void loginNewUserTest() {
        // clean buffer
        channel.releaseOutbound();
        channel.writeInbound("/login newuser password\r\n");
        assertEquals(Messages.format(Messages.USER_SUCCESSFULLY_REGISTERED,"Server"), channel.readOutbound());
    }

    @Test
    public void loginExistingUserTest() {
        // clean buffer
        channel.writeInbound("/login existinguser password\r\n");
        channel.writeInbound("/leave\r\n");
        channel = new EmbeddedChannel(new ChannelHandler());

        channel.releaseOutbound();
        channel.writeInbound("/login existinguser password\r\n");
        assertEquals(Messages.format(Messages.USER_SUCCESSFULLY_LOGGED_IN,"Server"), channel.readOutbound());
    }

    @Test
    public void loginWrongPasswordTest() {
        // clean buffer
        channel.writeInbound("/login userwrongpassword password\r\n");
        channel.writeInbound("/leave\r\n");
        channel = new EmbeddedChannel(new ChannelHandler());
        channel.releaseOutbound();
        channel.writeInbound("/login userwrongpassword anotherpassword\r\n");
        assertEquals(Messages.format(Messages.WRONG_PASSWORD,"Server"), channel.readOutbound());
    }

    @Test
    public void joinTest(){
        channel.writeInbound("/login userjoinok password\r\n");
        // clean buffer
        channel.releaseOutbound();
        channel.writeInbound("/join room_1\r\n");
        assertEquals(Messages.format(Messages.JOINNED_CHANNEL,"Server","room_1"), channel.readOutbound());
    }

    @Test
    public void joinNotLoggedTest(){
        channel.writeInbound("/login userjoinnotlogged password\r\n");
        channel.writeInbound("/leave\r\n");
        channel = new EmbeddedChannel(new ChannelHandler());
        channel.releaseOutbound();
        channel.writeInbound("/join room_not_logged\r\n");
        assertEquals(Messages.format(Messages.YOU_ARE_NOT_LOGGED_IN,"Server"), channel.readOutbound());
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
        assertEquals(Messages.format(Messages.JOINNED_CHANNEL,"Server", "room_full"), channel.readOutbound());
    }

    @Test
    public void leaveTest(){
        channel.writeInbound("/login userleave password\r\n");
        // clean buffer
        channel.releaseOutbound();
        channel.writeInbound("/leave\r\n");
        assertEquals(Messages.format(Messages.LEAVING,"Server"), channel.readOutbound());
    }

    @Test
    public void usersTest(){
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

        assertEquals(Messages.format(Messages.LIST_OF_USERS_IN_CHANNEL ,"Server","room_1")+"users3\r\n\r\n", sb.toString());
    }

    @Test
    public void invalidCommandTest() {
        // clean buffer
        channel.releaseOutbound();
        channel.writeInbound("/login a b c d e\r\n");
        assertEquals(Messages.format(Messages.INVALID_COMMAND,"Server"), channel.readOutbound());
    }

    @Test
    public void sendingMessageNotLoggedTest() {
        // clean buffer
        channel.writeInbound("/login userssendingmessagenotlogged password\r\n");
        channel.releaseOutbound();
        channel.writeInbound("SENDING MESSAGE\r\n");
        assertEquals(Messages.format(Messages.YOU_ARE_NOT_IN_A_CHANNEL,"Server"), channel.readOutbound());
    }

}
