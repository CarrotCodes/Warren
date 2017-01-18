package chat.willow.warren.extension.extended_join

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import engineer.carrot.warren.kale.IKaleHandler
import engineer.carrot.warren.kale.irc.message.extension.extended_join.ExtendedJoinMessage
import engineer.carrot.warren.kale.irc.message.rfc1459.JoinMessage
import engineer.carrot.warren.kale.irc.message.utility.CaseMapping
import engineer.carrot.warren.kale.irc.prefix.Prefix
import chat.willow.warren.state.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ExtendedJoinHandlerTests {

    private lateinit var sut: ExtendedJoinHandler
    private lateinit var channelsState: ChannelsState
    private lateinit var connectionState: ConnectionState
    private lateinit var mockJoinHandler: IKaleHandler<JoinMessage>

    @Before fun setUp() {
        val lifecycleState = LifecycleState.DISCONNECTED
        val caseMappingState = CaseMappingState(CaseMapping.RFC1459)

        mockJoinHandler = mock()
        channelsState = emptyChannelsState(caseMappingState)
        connectionState = ConnectionState(server = "test.server", port = 6697, nickname = "test-nick", user = "test-nick", lifecycle = lifecycleState)

        sut = ExtendedJoinHandler(mockJoinHandler, channelsState, caseMappingState)
    }


    @Test fun test_handle_CallsJoinHandler_WithCorrectMessage() {
        val message = ExtendedJoinMessage(source = Prefix(nick = "test-user"), channel = "#channel", account = "test-account", realName = "real name")

        sut.handle(message, tags = mapOf())

        verify(mockJoinHandler).handle(message = JoinMessage(source = Prefix(nick = "test-user"), channels = listOf("#channel")), tags = mapOf())
    }

    @Test fun test_handle_CallsJoinHandler_WithCorrectTags() {
        val message = ExtendedJoinMessage(source = Prefix(nick = "test-user"), channel = "#channel", account = "test-account", realName = "real name")

        sut.handle(message, tags = mapOf("test-key" to "test-value"))

        verify(mockJoinHandler).handle(message = JoinMessage(source = Prefix(nick = "test-user"), channels = listOf("#channel")), tags = mapOf("test-key" to "test-value"))
    }

    @Test fun test_handle_JoinedChannel_UserAccountUpdatedInAllChannels() {
        val testUser = generateUser("test-user", account = null)
        channelsState.joined["#channel"] = ChannelState("#channel", users = generateChannelUsersState(testUser))
        channelsState.joined["#channel2"] = ChannelState("#channel2", users = generateChannelUsersState(testUser))

        val message = ExtendedJoinMessage(source = Prefix(nick = "test-user"), channel = "#channel", account = "test-account", realName = "real name")

        sut.handle(message, tags = mapOf())

        assertEquals("test-account", channelsState.joined["#channel"]!!.users["test-user"]!!.account)
        assertEquals("test-account", channelsState.joined["#channel2"]!!.users["test-user"]!!.account)
    }

    @Test fun test_handle_JoinedChannel_NonTrackedUser_NothingChanges() {
        val someoneElseUser = generateUser("someone-else", account = "someone-account")
        channelsState.joined["#channel"] = ChannelState("#channel", users = generateChannelUsersState(someoneElseUser))
        channelsState.joined["#channel2"] = ChannelState("#channel2", users = generateChannelUsersState(someoneElseUser))

        val message = ExtendedJoinMessage(source = Prefix(nick = "test-user"), channel = "#channel", account = "test-account", realName = "real name")

        sut.handle(message, tags = mapOf())

        assertEquals("someone-account", channelsState.joined["#channel"]!!.users["someone-else"]!!.account)
        assertEquals("someone-account", channelsState.joined["#channel2"]!!.users["someone-else"]!!.account)
    }

}