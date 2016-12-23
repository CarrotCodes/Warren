package engineer.carrot.warren.warren

import com.nhaarman.mockito_kotlin.*
import engineer.carrot.warren.kale.IKale
import engineer.carrot.warren.kale.IKaleHandler
import engineer.carrot.warren.kale.IKaleParsingStateDelegate
import engineer.carrot.warren.kale.irc.message.IMessage
import engineer.carrot.warren.kale.irc.message.IrcMessage
import engineer.carrot.warren.kale.irc.message.rfc1459.JoinMessage
import engineer.carrot.warren.kale.irc.message.utility.CaseMapping
import engineer.carrot.warren.warren.event.ConnectionLifecycleEvent
import engineer.carrot.warren.warren.event.IWarrenEventDispatcher
import engineer.carrot.warren.warren.event.internal.IWarrenInternalEventGenerator
import engineer.carrot.warren.warren.event.internal.IWarrenInternalEventQueue
import engineer.carrot.warren.warren.extension.cap.CapLifecycle
import engineer.carrot.warren.warren.extension.cap.CapState
import engineer.carrot.warren.warren.extension.sasl.SaslState
import engineer.carrot.warren.warren.handler.*
import engineer.carrot.warren.warren.handler.rpl.*
import engineer.carrot.warren.warren.handler.rpl.Rpl005.Rpl005Handler
import engineer.carrot.warren.warren.helper.ISleeper
import engineer.carrot.warren.warren.registration.IRegistrationManager
import engineer.carrot.warren.warren.state.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.never

class IrcRunnerTests {

    lateinit var connection: IrcConnection
    lateinit var connectionState: ConnectionState
    lateinit var channelModesState: ChannelModesState
    lateinit var userPrefixesState: UserPrefixesState
    lateinit var channelsState: ChannelsState

    lateinit var mockEventDispatcher: IWarrenEventDispatcher
    lateinit var mockInternalEventQueue: IWarrenInternalEventQueue
    lateinit var mockNewLineGenerator: IWarrenInternalEventGenerator
    lateinit var mockKale: MockKale
    lateinit var mockSink: IMessageSink
    lateinit var mockLineSource: ILineSource
    lateinit var mockRegistrationManager: IRegistrationManager
    lateinit var mockSleeper: ISleeper

    @Before fun setUp() {
        val lifecycleState = LifecycleState.DISCONNECTED
        val capLifecycleState = CapLifecycle.NEGOTIATED
        val capState = CapState(lifecycle = capLifecycleState, negotiate = setOf(), server = mapOf(), accepted = setOf(), rejected = setOf())
        connectionState = ConnectionState(server = "test.server", port = 6697, nickname = "test-nick", user = "test-nick", lifecycle = lifecycleState)

        userPrefixesState = UserPrefixesState(prefixesToModes = mapOf('@' to 'o', '+' to 'v'))
        channelModesState = ChannelModesState(typeA = setOf('e', 'I', 'b'), typeB = setOf('k'), typeC = setOf('l'), typeD = setOf('i', 'm', 'n', 'p', 's', 't', 'S', 'r'))

        val channelPrefixesState = ChannelTypesState(types = setOf('#', '&'))
        val caseMappingState = CaseMappingState(mapping = CaseMapping.RFC1459)
        val parsingState = ParsingState(userPrefixesState, channelModesState, channelPrefixesState, caseMappingState)

        channelsState = ChannelsState(joining = JoiningChannelsState(caseMappingState), joined = JoinedChannelsState(caseMappingState))

        val initialState = IrcState(connectionState, parsingState, channelsState)

        mockEventDispatcher = mock()
        mockInternalEventQueue = mock()
        mockNewLineGenerator = mock()
        mockKale = MockKale()

        mockSink = mock()
        mockLineSource = mock()

        mockRegistrationManager = mock()
        mockSleeper = mock()

        val saslState = SaslState(shouldAuth = false, lifecycle = AuthLifecycle.NO_AUTH, credentials = null)

        connection = IrcConnection(mockEventDispatcher, mockInternalEventQueue, mockNewLineGenerator, mockKale, mockSink, initialState, startAsyncThreads = false, initialCapState = capState, initialSaslState = saslState, registrationManager = mockRegistrationManager, sleeper = mockSleeper)
    }

    @Test fun test_run_RegistersBaseHandlers() {
        whenever(mockSink.setUp()).thenReturn(true)

        connection.run()

        assertEquals(22, mockKale.spyRegisterHandlers.size)

        assertTrue(arrayContainsHandlerOfType<CapLsHandler>(mockKale.spyRegisterHandlers)) // FIXME: move to other test
        assertTrue(arrayContainsHandlerOfType<CapAckHandler>(mockKale.spyRegisterHandlers))
        assertTrue(arrayContainsHandlerOfType<CapNakHandler>(mockKale.spyRegisterHandlers))

        assertTrue(arrayContainsHandlerOfType<JoinHandler>(mockKale.spyRegisterHandlers))
        assertTrue(arrayContainsHandlerOfType<KickHandler>(mockKale.spyRegisterHandlers))
        assertTrue(arrayContainsHandlerOfType<ModeHandler>(mockKale.spyRegisterHandlers))
        assertTrue(arrayContainsHandlerOfType<NickHandler>(mockKale.spyRegisterHandlers))
        assertTrue(arrayContainsHandlerOfType<NoticeHandler>(mockKale.spyRegisterHandlers))
        assertTrue(arrayContainsHandlerOfType<PartHandler>(mockKale.spyRegisterHandlers))
        assertTrue(arrayContainsHandlerOfType<PingHandler>(mockKale.spyRegisterHandlers))
        assertTrue(arrayContainsHandlerOfType<PongHandler>(mockKale.spyRegisterHandlers))
        assertTrue(arrayContainsHandlerOfType<PrivMsgHandler>(mockKale.spyRegisterHandlers))
        assertTrue(arrayContainsHandlerOfType<QuitHandler>(mockKale.spyRegisterHandlers))
        assertTrue(arrayContainsHandlerOfType<TopicHandler>(mockKale.spyRegisterHandlers))
        assertTrue(arrayContainsHandlerOfType<Rpl005Handler>(mockKale.spyRegisterHandlers))
        assertTrue(arrayContainsHandlerOfType<Rpl332Handler>(mockKale.spyRegisterHandlers))
        assertTrue(arrayContainsHandlerOfType<Rpl353Handler>(mockKale.spyRegisterHandlers))
        assertTrue(arrayContainsHandlerOfType<Rpl376Handler>(mockKale.spyRegisterHandlers))
        assertTrue(arrayContainsHandlerOfType<Rpl471Handler>(mockKale.spyRegisterHandlers))
        assertTrue(arrayContainsHandlerOfType<Rpl473Handler>(mockKale.spyRegisterHandlers))
        assertTrue(arrayContainsHandlerOfType<Rpl474Handler>(mockKale.spyRegisterHandlers))
        assertTrue(arrayContainsHandlerOfType<Rpl475Handler>(mockKale.spyRegisterHandlers))
    }

    private inline fun <reified T: IKaleHandler<*>> arrayContainsHandlerOfType(iterable: Iterable<*>): Boolean {
        return iterable.any {
            it?.javaClass?.isAssignableFrom(T::class.java) ?: false
        }
    }

    @Test fun test_run_startsRegistration() {
        whenever(mockSink.setUp()).thenReturn(true)

        connection.run()

        verify(mockRegistrationManager).startRegistration()
    }

    @Test fun test_modeTakesAParameter_TypeDAlwaysFalse() {
        channelModesState.typeD = setOf('x')

        assertFalse(connection.modeTakesAParameter(isAdding = true, token = 'x'))
        assertFalse(connection.modeTakesAParameter(isAdding = false, token = 'x'))
    }

    @Test fun test_modeTakesAParameter_TypeABAlwaysTrue() {
        channelModesState.typeA = setOf('x')
        channelModesState.typeB = setOf('y')

        assertTrue(connection.modeTakesAParameter(isAdding = true, token = 'x'))
        assertTrue(connection.modeTakesAParameter(isAdding = false, token = 'x'))
        assertTrue(connection.modeTakesAParameter(isAdding = true, token = 'y'))
        assertTrue(connection.modeTakesAParameter(isAdding = false, token = 'y'))
    }

    @Test fun test_modeTakesAParameter_TypeCTrueIfAdding() {
        channelModesState.typeC = setOf('c')

        assertTrue(connection.modeTakesAParameter(isAdding = true, token = 'c'))
        assertFalse(connection.modeTakesAParameter(isAdding = false, token = 'c'))
    }

    @Test fun test_modeTakesAParameter_PrefixRelated_ReturnsTrue() {
        userPrefixesState.prefixesToModes = mapOf('+' to 'v')

        assertTrue(connection.modeTakesAParameter(isAdding = true, token = 'v'))
        assertTrue(connection.modeTakesAParameter(isAdding = false, token = 'v'))
    }

    @Test fun test_modeTakesAParameter_Unknown_NonPrefix_ReturnsFalse() {
        assertFalse(connection.modeTakesAParameter(isAdding = true, token = 'z'))
        assertFalse(connection.modeTakesAParameter(isAdding = false, token = 'z'))
    }

    // IRegistrationListener

    @Test fun test_onRegistrationEnded_Connecting_NickservAuthOn_WithCredentials_SendsIdentifyMessage() {
        connectionState.lifecycle = LifecycleState.CONNECTING
        connectionState.nickServ.shouldAuth = true
        connectionState.nickServ.credentials = AuthCredentials(account = "test-user", password = "test-password")

        connection.onRegistrationEnded()

        verify(mockSink).writeRaw("NICKSERV identify test-user test-password")
    }

    @Test fun test_onRegistrationEnded_Registering_NickservAuthOn_WithCredentials_SendsIdentifyMessage() {
        connectionState.lifecycle = LifecycleState.REGISTERING
        connectionState.nickServ.shouldAuth = true
        connectionState.nickServ.credentials = AuthCredentials(account = "test-user", password = "test-password")

        connection.onRegistrationEnded()

        verify(mockSink).writeRaw("NICKSERV identify test-user test-password")
    }

    @Test fun test_onRegistrationEnded_AllSuccessful_WaitsBeforeJoiningChannels() {
        connectionState.lifecycle = LifecycleState.REGISTERING
        connectionState.nickServ.shouldAuth = true
        connectionState.nickServ.credentials = AuthCredentials(account = "test-user", password = "test-password")

        connection.onRegistrationEnded()

        verify(mockSleeper).sleep(connectionState.nickServ.channelJoinWaitSeconds * 1000L)
    }

    @Test fun test_onRegistrationEnded_NickservAuthOn_NoCredentials_DoesNotWriteAnything() {
        connectionState.lifecycle = LifecycleState.REGISTERING
        connectionState.nickServ.shouldAuth = true
        connectionState.nickServ.credentials = null

        connection.onRegistrationEnded()

        verify(mockSink, never()).writeRaw(any())
    }

    @Test fun test_onRegistrationEnded_AtLeastOneChannel_JoinsChannels() {
        connectionState.lifecycle = LifecycleState.REGISTERING
        channelsState.joining += JoiningChannelState("#test", status = JoiningChannelLifecycle.JOINING)
        channelsState.joining += JoiningChannelState("#test2", key = "testpass", status = JoiningChannelLifecycle.JOINING)

        connection.onRegistrationEnded()

        inOrder(mockSink) {
            verify(mockSink).write(JoinMessage(channels = listOf("#test")))
            verify(mockSink).write(JoinMessage(channels = listOf("#test2"), keys = listOf("testpass")))
        }
    }

    @Test fun test_onRegistrationEnded_NoChannelsToJoin_DoesNotWriteJoinMessages() {
        connectionState.lifecycle = LifecycleState.REGISTERING
        channelsState.joining.clear()

        connection.onRegistrationEnded()

        verify(mockSink, never()).write(any<JoinMessage>())
    }

    @Test fun test_onRegistrationEnded_SetsConnectionLifecycleToConnected() {
        connection.onRegistrationEnded()

        assertEquals(LifecycleState.CONNECTED, connectionState.lifecycle)
    }

    @Test fun test_onRegistrationEnded_FiresConnectionLifecycleEvent_WithConnected() {
        connection.onRegistrationEnded()

        verify(mockEventDispatcher).fire(ConnectionLifecycleEvent(LifecycleState.CONNECTED))
    }

}

class MockKale : IKale {

    var spyRegisterHandlers = mutableListOf<IKaleHandler<*>>()

    override fun <T : IMessage> register(handler: IKaleHandler<T>) {
        spyRegisterHandlers.add(handler)
    }

    override fun <T : IMessage> unregister(handler: IKaleHandler<T>) {
        throw UnsupportedOperationException()
    }

    override fun <M : IMessage> handlerFor(messageClass: Class<M>): IKaleHandler<M> {
        throw UnsupportedOperationException()
    }

    override fun serialise(message: Any): IrcMessage? {
        throw UnsupportedOperationException()
    }

    override fun process(line: String) {
        throw UnsupportedOperationException()
    }

    override var parsingStateDelegate: IKaleParsingStateDelegate? = null

}