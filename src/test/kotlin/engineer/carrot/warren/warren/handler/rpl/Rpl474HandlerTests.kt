package engineer.carrot.warren.warren.handler.rpl

import engineer.carrot.warren.kale.irc.message.rpl.Rpl474Message
import engineer.carrot.warren.warren.handler.rpl.Rpl474Handler
import engineer.carrot.warren.warren.state.*
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class Rpl474HandlerTests {

    lateinit var handler: Rpl474Handler
    lateinit var channelsState: ChannelsState

    @Before fun setUp() {
        channelsState = ChannelsState(joined = mutableMapOf())
        handler = Rpl474Handler(channelsState)
    }

    @Test fun test_handle_NonexistentChannel_DoesNothing() {
        handler.handle(Rpl474Message(source = "", target = "", channel = "#somewhere", contents = ""))

        assertEquals(ChannelsState(joining = mutableMapOf(), joined = mutableMapOf()), channelsState)
    }

    @Test fun test_handle_ValidChannel_SetsStatusToFailed() {
        channelsState.joining["#channel"] = JoiningChannelState("#channel", status = JoiningChannelLifecycle.JOINING)

        handler.handle(Rpl474Message(source = "", target = "", channel = "#channel", contents = ""))

        val expectedChannelState = JoiningChannelState("#channel", status = JoiningChannelLifecycle.FAILED)

        assertEquals(ChannelsState(joining = mutableMapOf("#channel" to expectedChannelState), joined = mutableMapOf()), channelsState)
    }

}