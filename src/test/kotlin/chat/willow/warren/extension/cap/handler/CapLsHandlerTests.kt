package chat.willow.warren.extension.cap.handler

import chat.willow.kale.irc.message.IMessage
import chat.willow.kale.irc.message.extension.cap.CapMessage
import chat.willow.kale.irc.tag.TagStore
import chat.willow.warren.IMessageSink
import chat.willow.warren.extension.cap.CapLifecycle
import chat.willow.warren.extension.cap.CapState
import chat.willow.warren.extension.cap.ICapManager
import com.nhaarman.mockito_kotlin.*
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CapLsHandlerTests {

    lateinit var handler: CapLsHandler
    lateinit var capState: CapState
    lateinit var mockSink: IMessageSink
    lateinit var mockCapManager: ICapManager

    @Before fun setUp() {
        val capLifecycleState = CapLifecycle.NEGOTIATING
        capState = CapState(lifecycle = capLifecycleState, negotiate = setOf(), server = mapOf(), accepted = setOf(), rejected = setOf())
        mockSink = mock()
        mockCapManager = mock()

        handler = CapLsHandler(capState, mockSink, mockCapManager)
    }

    @Test fun test_handle_AddsCapsToStateList() {
        capState.negotiate = setOf("cap1", "cap2", "cap3")

        handler.handle(CapMessage.Ls.Message(caps = mapOf("cap1" to null, "cap2" to "value"), target = ""), TagStore())

        assertEquals(mapOf("cap1" to null, "cap2" to "value"), capState.server)
    }

    @Test fun test_handle_Negotiating_ImplicitlyRejectsMissingCaps() {
        capState.lifecycle = CapLifecycle.NEGOTIATING
        capState.negotiate = setOf("cap1", "cap2", "cap3", "cap4")

        handler.handle(CapMessage.Ls.Message(caps = mapOf("cap1" to null, "cap2" to null), target = ""), TagStore())

        assertEquals(setOf("cap3", "cap4"), capState.rejected)
    }

    @Test fun test_handle_Negotiating_TellsCapManagerRegistrationStateChanged() {
        capState.lifecycle = CapLifecycle.NEGOTIATING

        handler.handle(CapMessage.Ls.Message(caps = mapOf("cap1" to null, "cap2" to null), target = ""), TagStore())

        verify(mockCapManager).onRegistrationStateChanged()
    }

    @Test fun test_handle_Negotiating_SendsCapReqForSupportedCaps() {
        capState.lifecycle = CapLifecycle.NEGOTIATING
        capState.negotiate = setOf("cap1", "cap2")

        handler.handle(CapMessage.Ls.Message(caps = mapOf("cap1" to null, "cap2" to null), target = ""), TagStore())

        verify(mockSink).write(CapMessage.Req.Command(caps = listOf("cap1", "cap2")))
    }

    @Test fun test_handle_Negotiating_MultilineLs_DoesNothingElse() {
        capState.lifecycle = CapLifecycle.NEGOTIATING
        capState.negotiate = setOf("cap1", "cap2")

        handler.handle(CapMessage.Ls.Message(caps = mapOf("cap1" to null, "cap2" to null), isMultiline = true, target = ""), TagStore())

        verify(mockSink, never()).write(any<IMessage>())
    }

    @Test fun test_handle_NotNegotiating_DoesNothingElse() {
        capState.lifecycle = CapLifecycle.NEGOTIATED
        capState.negotiate = setOf("cap1", "cap2")

        handler.handle(CapMessage.Ls.Message(caps = mapOf("cap1" to null, "cap2" to null), target = ""), TagStore())

        verify(mockSink, never()).write(any<IMessage>())
    }

    @Test fun test_handle_Negotiating_TellsCapManagerCapValuesChanged() {
        capState.lifecycle = CapLifecycle.NEGOTIATING

        handler.handle(CapMessage.Ls.Message(caps = mapOf("cap1" to null, "cap2" to "value2"), isMultiline = false, target = ""), TagStore())

        inOrder(mockCapManager) {
            verify(mockCapManager).capValueSet("cap1", value = null)
            verify(mockCapManager).capValueSet("cap2", value = "value2")
        }
    }

}