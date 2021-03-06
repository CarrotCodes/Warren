package chat.willow.warren.handler

import chat.willow.kale.irc.message.rfc1459.PongMessage
import chat.willow.kale.irc.tag.TagStore
import chat.willow.warren.IMessageSink
import chat.willow.warren.state.ConnectionState
import chat.willow.warren.state.LifecycleState
import com.nhaarman.mockito_kotlin.mock
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PongHandlerTests {

    lateinit var handler: PongHandler
    lateinit var mockSink: IMessageSink
    lateinit var connectionState: ConnectionState

    @Before fun setUp() {
        mockSink = mock()
        val lifecycleState = LifecycleState.DISCONNECTED

        connectionState = ConnectionState(server = "test.server", port = 6697, nickname = "test-nick", user = "test-nick", lifecycle = lifecycleState)
        handler = PongHandler(mockSink, connectionState)
    }

    @Test fun test_handle_UpdatesLastPingPongTimeToNow() {
        val expectedTime = System.currentTimeMillis()
        val tolerance = 1000

        handler.handle(PongMessage.Message(token = "TestToken"), TagStore())

        assertTrue(connectionState.lastPingOrPong > tolerance)
        assertTrue(connectionState.lastPingOrPong > expectedTime - tolerance)
        assertTrue(connectionState.lastPingOrPong < expectedTime + tolerance)
    }

}