package chat.willow.warren.extension.sasl

import chat.willow.kale.IMetadataStore
import chat.willow.kale.KaleHandler
import chat.willow.kale.irc.message.extension.sasl.AuthenticateMessage
import chat.willow.warren.IMessageSink
import chat.willow.warren.helper.loggerFor
import chat.willow.warren.state.AuthLifecycle
import java.util.*

class AuthenticateHandler(val state: SaslState, val sink: IMessageSink) : KaleHandler<AuthenticateMessage.Message>(AuthenticateMessage.Message.Parser) {

    private val LOGGER = loggerFor<AuthenticateHandler>()


    override fun handle(message: AuthenticateMessage.Message, metadata: IMetadataStore) {
        if (state.lifecycle != AuthLifecycle.AUTHING) {
            LOGGER.warn("got an auth challenge, but we don't think we're authenticating - ignoring: $message")
            return
        }

        val credentials = state.credentials
        if (credentials == null) {
            LOGGER.warn("wanted to do SASL auth, but don't have credentials set - bailing: $state")
            return
        }

        val saslBytes = ("${credentials.account}\u0000${credentials.account}\u0000${credentials.password}").toByteArray()
        val saslString = Base64.getEncoder().encode(saslBytes).toString(Charsets.UTF_8)

        LOGGER.debug("replied to sasl auth request for ${credentials.account}")
        sink.write(AuthenticateMessage.Command(payload = saslString))
    }

}