package chat.willow.warren.handler

import chat.willow.kale.helper.CaseMapping
import chat.willow.kale.irc.message.rfc1459.JoinMessage
import chat.willow.kale.irc.prefix.Prefix
import chat.willow.kale.irc.prefix.prefix
import chat.willow.kale.irc.tag.TagStore
import chat.willow.warren.state.*
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class JoinHandlerTests {

    lateinit var handler: JoinHandler
    lateinit var connectionState: ConnectionState
    lateinit var channelsState: ChannelsState
    val caseMappingState = CaseMappingState(mapping = CaseMapping.RFC1459)

    @Before fun setUp() {
        val lifecycleState = LifecycleState.DISCONNECTED

        connectionState = ConnectionState(server = "test.server", port = 6697, nickname = "test-nick", user = "test-nick", lifecycle = lifecycleState)
        channelsState = emptyChannelsState(caseMappingState)
        handler = JoinHandler(connectionState, channelsState.joining, channelsState.joined, caseMappingState)
    }

    @Test fun test_handle_SourceIsSelf_WellFormed_JoinsCorrectChannel() {
        channelsState.joining += JoiningChannelState("#channel", status = JoiningChannelLifecycle.JOINING)

        handler.handle(JoinMessage.Message(source = Prefix(nick = "test-nick"), channels = listOf("#channel")), TagStore())

        assertEquals(channelsStateWith(listOf(ChannelState(name = "#channel", users = generateUsersFromNicks(nicks = listOf(), mappingState = caseMappingState))), caseMappingState), channelsState)
    }

    @Test fun test_handle_SourceIsSelf_DifferingCase_JoinsCorrectChannel() {
        channelsState.joining += JoiningChannelState("#channel", status = JoiningChannelLifecycle.JOINING)

        handler.handle(JoinMessage.Message(source = Prefix(nick = "Test-Nick"), channels = listOf("#channel")), TagStore())

        assertEquals(channelsStateWith(listOf(ChannelState(name = "#channel", users = generateUsersFromNicks(nicks = listOf(), mappingState = caseMappingState))), caseMappingState), channelsState)
    }

    @Test fun test_handle_SourceIsSelf_AlreadyInChannel() {
        channelsState.joined += ChannelState("#channel", users = generateUsersFromNicks(listOf("test-nick"), mappingState = caseMappingState))

        handler.handle(JoinMessage.Message(source = Prefix(nick = "test-nick"), channels = listOf("#channel")), TagStore())

        assertEquals(channelsStateWith(listOf(ChannelState(name = "#channel", users = generateUsersFromNicks(listOf("test-nick"), mappingState = caseMappingState))), caseMappingState), channelsState)
    }

    @Test fun test_handle_SourceIsSelf_MissingSource_DoesNothing() {
        handler.handle(JoinMessage.Message(channels = listOf("#channel"), source = prefix("")), TagStore())

        assertEquals(emptyChannelsState(caseMappingState), channelsState)
    }

    @Test fun test_handle_SourceIsOther_WellFormed() {
        channelsState.joined += ChannelState("#channel", users = generateUsersFromNicks(listOf("test-nick"), mappingState = caseMappingState))

        handler.handle(JoinMessage.Message(source = Prefix(nick = "someone-else"), channels = listOf("#channel")), TagStore())

        assertEquals(channelsStateWith(listOf(ChannelState(name = "#channel", users = generateUsersFromNicks(listOf("test-nick", "someone-else"), mappingState = caseMappingState))), caseMappingState), channelsState)
    }

    @Test fun test_handle_SourceIsOther_AlreadyInChannel() {
        channelsState.joined += ChannelState("#channel", users = generateUsersFromNicks(listOf("someone-else"), mappingState = caseMappingState))

        handler.handle(JoinMessage.Message(source = Prefix(nick = "someone-else"), channels = listOf("#channel")), TagStore())

        assertEquals(channelsStateWith(listOf(ChannelState(name = "#channel", users = generateUsersFromNicks(listOf("someone-else"), mappingState = caseMappingState))), caseMappingState), channelsState)
    }

    @Test fun test_handle_SourceIsOther_NotInChannel() {
        handler.handle(JoinMessage.Message(source = Prefix(nick = "someone-else"), channels = listOf("#channel")), TagStore())

        assertEquals(emptyChannelsState(caseMappingState), channelsState)
    }

}