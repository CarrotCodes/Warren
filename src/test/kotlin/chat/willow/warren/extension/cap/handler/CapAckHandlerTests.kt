package chat.willow.warren.extension.cap.handler

import chat.willow.kale.irc.message.extension.cap.CapMessage
import chat.willow.kale.irc.tag.TagStore
import chat.willow.warren.IMessageSink
import chat.willow.warren.extension.cap.CapLifecycle
import chat.willow.warren.extension.cap.CapState
import chat.willow.warren.extension.cap.ICapManager
import chat.willow.warren.extension.sasl.SaslState
import chat.willow.warren.state.AuthLifecycle
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class CapAckHandlerTests {

    lateinit var handler: CapAckHandler
    lateinit var state: CapState
    lateinit var saslState: SaslState
    lateinit var mockSink: IMessageSink
    lateinit var mockCapManager: ICapManager

    @Before fun setUp() {
        val capLifecycleState = CapLifecycle.NEGOTIATING
        state = CapState(lifecycle = capLifecycleState, negotiate = setOf(), server = mapOf(), accepted = setOf(), rejected = setOf())
        saslState = SaslState(shouldAuth = false, lifecycle = AuthLifecycle.AUTH_FAILED, credentials = null)
        mockSink = mock()
        mockCapManager = mock()

        handler = CapAckHandler(state, saslState, mockSink, mockCapManager)
    }

    @Test fun test_handle_AddsAckedCapsToStateList() {
        state.negotiate = setOf("cap1", "cap2", "cap3")

        handler.handle(CapMessage.Ack.Message(caps = listOf("cap1", "cap2"), target = ""), TagStore())

        assertEquals(setOf("cap1", "cap2"), state.accepted)
    }

    @Test fun test_handle_Negotiating_TellsCapManagerRegistrationStateChanged() {
        state.lifecycle = CapLifecycle.NEGOTIATING

        handler.handle(CapMessage.Ack.Message(caps = listOf("cap 1", "cap 2"), target = ""), TagStore())

        verify(mockCapManager).onRegistrationStateChanged()
    }

    @Test fun test_handle_ACKedSasl_NoAuth_DoesNotWriteAuthenticateMessage() {
        saslState.shouldAuth = false

        handler.handle(CapMessage.Ack.Message(caps = listOf("sasl"), target = ""), TagStore())

        verify(mockSink, never()).write(any())
    }

    @Test fun test_handle_ServerACKedCapThatWeDidntNegotiate_DoesNotAcceptIt() {
        state.negotiate = setOf("cap1", "cap2")

        handler.handle(CapMessage.Ack.Message(caps = listOf("cap3"), target = ""), TagStore())

        assertFalse(state.accepted.contains("cap3"))
        verify(mockCapManager, never()).capEnabled("cap3")
    }

}