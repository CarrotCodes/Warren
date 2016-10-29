package engineer.carrot.warren.warren

import engineer.carrot.warren.kale.IKale
import engineer.carrot.warren.kale.IKaleParsingStateDelegate
import engineer.carrot.warren.kale.irc.message.extension.cap.CapLsMessage
import engineer.carrot.warren.kale.irc.message.rfc1459.NickMessage
import engineer.carrot.warren.kale.irc.message.rfc1459.PingMessage
import engineer.carrot.warren.kale.irc.message.rfc1459.UserMessage
import engineer.carrot.warren.warren.event.ConnectionLifecycleEvent
import engineer.carrot.warren.warren.event.IWarrenEventDispatcher
import engineer.carrot.warren.warren.event.internal.IWarrenInternalEventGenerator
import engineer.carrot.warren.warren.event.internal.IWarrenInternalEventQueue
import engineer.carrot.warren.warren.event.internal.IWarrenInternalEventSink
import engineer.carrot.warren.warren.extension.cap.CapManager
import engineer.carrot.warren.warren.extension.cap.CapState
import engineer.carrot.warren.warren.extension.sasl.SaslState
import engineer.carrot.warren.warren.handler.*
import engineer.carrot.warren.warren.handler.rpl.*
import engineer.carrot.warren.warren.handler.rpl.Rpl005.*
import engineer.carrot.warren.warren.state.IStateCapturing
import engineer.carrot.warren.warren.state.IrcState
import engineer.carrot.warren.warren.state.LifecycleState
import kotlin.concurrent.thread

interface IIrcRunner : IStateCapturing<IrcState> {

    fun run()

}

class IrcRunner(val eventDispatcher: IWarrenEventDispatcher, private val internalEventQueue: IWarrenInternalEventQueue, val newLineGenerator: IWarrenInternalEventGenerator, val kale: IKale, val sink: IMessageSink, initialState: IrcState, val startAsyncThreads: Boolean = true, initialCapState: CapState, initialSaslState: SaslState) : IIrcRunner, IKaleParsingStateDelegate {

    private val LOGGER = loggerFor<IrcRunner>()

    var eventSink: IWarrenInternalEventSink = internalEventQueue

    private var internalState = initialState
    @Volatile override var state: IrcState = initialState.copy()

    private val PONG_TIMER_MS: Long = 30 * 1000

    val caps = CapManager(initialCapState, kale, internalState.channels, initialSaslState, sink, internalState.parsing.caseMapping)

    override fun captureStateSnapshot() {
        state = internalState.copy()

        caps.captureStateSnapshot()
    }

    override fun run() {
        if (!sink.setUp()) {
            LOGGER.warn("couldn't set up sink - bailing out")
            return
        }

        kale.parsingStateDelegate = this

        registerRFC1459Handlers()
        caps.setUp()

        sendRegistrationMessages()
        runEventLoop()
    }

    private fun registerRFC1459Handlers() {
        kale.register(JoinHandler(internalState.connection, internalState.channels.joining, internalState.channels.joined, internalState.parsing.caseMapping))
        kale.register(KickHandler(internalState.connection, internalState.channels.joined, internalState.parsing.caseMapping))
        kale.register(ModeHandler(eventDispatcher, internalState.parsing.channelTypes, internalState.channels.joined, internalState.parsing.userPrefixes, internalState.parsing.caseMapping))
        kale.register(NickHandler(internalState.connection, internalState.channels.joined))
        kale.register(NoticeHandler(internalState.parsing.channelTypes))
        kale.register(PartHandler(internalState.connection, internalState.channels.joined, internalState.parsing.caseMapping))
        kale.register(PingHandler(sink, internalState.connection))
        kale.register(PongHandler(sink, internalState.connection))
        kale.register(PrivMsgHandler(eventDispatcher, internalState.channels.joined, internalState.parsing.channelTypes))
        kale.register(QuitHandler(eventDispatcher, internalState.connection, internalState.channels.joined))
        kale.register(TopicHandler(internalState.channels.joined, internalState.parsing.caseMapping))
        kale.register(Rpl005Handler(internalState.parsing, Rpl005PrefixHandler, Rpl005ChanModesHandler, Rpl005ChanTypesHandler, Rpl005CaseMappingHandler))
        kale.register(Rpl332Handler(internalState.channels.joined, internalState.parsing.caseMapping))
        kale.register(Rpl353Handler(internalState.channels.joined, internalState.parsing.userPrefixes, internalState.parsing.caseMapping))
        kale.register(Rpl376Handler(eventDispatcher, sink, internalState.channels.joining.all.mapValues { entry -> entry.value.key }, internalState.connection, caps.internalState))
        kale.register(Rpl471Handler(internalState.channels.joining, internalState.parsing.caseMapping))
        kale.register(Rpl473Handler(internalState.channels.joining, internalState.parsing.caseMapping))
        kale.register(Rpl474Handler(internalState.channels.joining, internalState.parsing.caseMapping))
        kale.register(Rpl475Handler(internalState.channels.joining, internalState.parsing.caseMapping))
    }

    private fun sendRegistrationMessages() {
        sink.write(CapLsMessage(caps = mapOf())) // FIXME: Cap Extension should do this as part of registration
        sink.write(NickMessage(nickname = internalState.connection.nickname))
        sink.write(UserMessage(username = internalState.connection.user, mode = "8", realname = internalState.connection.user))

        internalState.connection.lifecycle = LifecycleState.REGISTERING
        eventDispatcher.fire(ConnectionLifecycleEvent(LifecycleState.REGISTERING))
    }

    private fun runEventLoop() {
        val lineThread = createLineThread(internalEventQueue, internalState)
        val pingThread = createPingThread(internalEventQueue, internalState, sink)

        if (startAsyncThreads) {
            lineThread.start()
            pingThread.start()
        }

        eventLoop@ while (true) {
            val event = internalEventQueue.grab()

            if (Thread.currentThread().isInterrupted || event == null) {
                LOGGER.warn("interrupted or null event, bailing")
                break@eventLoop
            }

            event.execute()

            synchronized(state) {
                captureStateSnapshot()
            }

            if (internalState.connection.lifecycle == LifecycleState.DISCONNECTED) {
                eventDispatcher.fire(ConnectionLifecycleEvent(LifecycleState.DISCONNECTED))

                LOGGER.trace("we disconnected, bailing")
                break@eventLoop
            }
        }

        if (lineThread.isAlive) {
            LOGGER.trace("line thread still alive - interrupting and assuming it'll bail out")
            lineThread.interrupt()
        } else {
            LOGGER.trace("line thread not active - not killing it")
        }

        if (pingThread.isAlive) {
            LOGGER.trace("Ping thread still alive - interrupting and assuming it'll bail out")
            pingThread.interrupt()
        } else {
            LOGGER.trace("ping thread not active - not killing it")
        }

        sink.tearDown()

        LOGGER.info("ending")
    }

    private fun createLineThread(eventQueue: IWarrenInternalEventQueue, state: IrcState): Thread {
        val lineThread = thread(start = false, name = "line thread") {
            LOGGER.debug("new line thread starting up")
            newLineGenerator.run()
            LOGGER.warn("new line generator ended")

            eventQueue.clear()
            eventQueue.add {
                state.connection.lifecycle = LifecycleState.DISCONNECTED
            }
        }

        lineThread.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { thread, exception ->
            LOGGER.warn("uncaught exception in line generator, forcing a disconnect: $exception")

            eventQueue.clear()
            eventQueue.add {
                state.connection.lifecycle = LifecycleState.DISCONNECTED
            }
        }

        return lineThread
    }

    private fun createPingThread(eventQueue: IWarrenInternalEventQueue, state: IrcState, sink: IMessageSink): Thread {
        return thread(start = false) {
            pingLoop@ while (!Thread.currentThread().isInterrupted) {
                try {
                    Thread.sleep(10 * 1000)
                } catch(exception: InterruptedException) {
                    LOGGER.info("ping thread interrupted - bailing out")
                    break@pingLoop
                }

                eventQueue.add {
                    if (state.connection.lifecycle == LifecycleState.CONNECTED) {
                        val currentTime = System.currentTimeMillis()

                        val msSinceLastPing = currentTime - state.connection.lastPingOrPong
                        if (msSinceLastPing > PONG_TIMER_MS) {
                            sink.write(PingMessage(token = "$currentTime"))
                        }
                    }
                }
            }
        }
    }

    // IKaleParsingStateDelegate

    override fun modeTakesAParameter(isAdding: Boolean, token: Char): Boolean {
        val prefixState = internalState.parsing.userPrefixes

        if (prefixState.prefixesToModes.containsValue(token)) {
            return true
        }

        val modesState = internalState.parsing.channelModes

        if (modesState.typeD.contains(token)) {
            return false
        }

        if (modesState.typeA.contains(token) || modesState.typeB.contains(token)) {
            return true
        }

        if (isAdding) {
            return modesState.typeC.contains(token)
        }

        return false
    }

}