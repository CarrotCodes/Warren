package chat.willow.warren.handler.rpl

import chat.willow.kale.helper.CaseMapping
import chat.willow.kale.irc.message.rfc1459.rpl.Rpl473MessageType
import chat.willow.kale.irc.tag.TagStore
import chat.willow.warren.state.*
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class Rpl473HandlerTests {

    lateinit var handler: Rpl473Handler
    lateinit var channelsState: ChannelsState
    val caseMappingState = CaseMappingState(mapping = CaseMapping.RFC1459)

    @Before fun setUp() {
        channelsState = emptyChannelsState(caseMappingState)
        handler = Rpl473Handler(channelsState.joining, caseMappingState)
    }

    @Test fun test_handle_NonexistentChannel_DoesNothing() {
        handler.handle(Rpl473MessageType(source = "", target = "", channel = "#somewhere", content = ""), TagStore())

        assertEquals(emptyChannelsState(caseMappingState), channelsState)
    }

    @Test fun test_handle_ValidChannel_SetsStatusToFailed() {
        channelsState.joining += JoiningChannelState("#channel", status = JoiningChannelLifecycle.JOINING)

        handler.handle(Rpl473MessageType(source = "", target = "", channel = "#channel", content = ""), TagStore())

        val expectedChannelState = JoiningChannelState("#channel", status = JoiningChannelLifecycle.FAILED)

        assertEquals(joiningChannelsStateWith(listOf(expectedChannelState), caseMappingState), channelsState)
    }

}