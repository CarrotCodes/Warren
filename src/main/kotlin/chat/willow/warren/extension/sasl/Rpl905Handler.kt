package chat.willow.warren.extension.sasl

import chat.willow.kale.IKaleHandler
import chat.willow.kale.irc.message.extension.sasl.Rpl905Message
import chat.willow.kale.irc.tag.ITagStore
import chat.willow.warren.extension.cap.ICapManager
import chat.willow.warren.helper.loggerFor
import chat.willow.warren.state.AuthLifecycle

class Rpl905Handler(val capManager: ICapManager, val saslState: SaslState) : IKaleHandler<Rpl905Message> {

    private val LOGGER = loggerFor<Rpl905Handler>()

    override val messageType = Rpl905Message::class.java

    override fun handle(message: Rpl905Message, tags: ITagStore) {
        LOGGER.warn("sasl auth failed: ${message.contents}")

        saslState.lifecycle = AuthLifecycle.AUTH_FAILED

        capManager.onRegistrationStateChanged()
    }

}

