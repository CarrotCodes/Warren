package chat.willow.warren.handler

import chat.willow.kale.KaleHandler
import chat.willow.kale.irc.message.rfc1459.PrivMsgMessage
import chat.willow.warren.IClientMessageSending
import chat.willow.warren.WarrenChannel
import chat.willow.warren.WarrenChannelUser
import chat.willow.warren.event.*
import chat.willow.warren.helper.loggerFor
import chat.willow.warren.state.ChannelTypesState
import chat.willow.warren.state.JoinedChannelsState
import chat.willow.warren.state.generateUser

class PrivMsgHandler(val eventDispatcher: IWarrenEventDispatcher, val client: IClientMessageSending, val channelsState: JoinedChannelsState, val channelTypesState: ChannelTypesState) : KaleHandler<PrivMsgMessage.Message>(PrivMsgMessage.Message.Parser) {

    private val LOGGER = loggerFor<PrivMsgHandler>()


    override fun handle(message: PrivMsgMessage.Message, metadata: IMetadataStore) {
        val source = message.source
        val target = message.target
        var messageContents = message.message

        var ctcp = CtcpEnum.NONE

        if (CtcpHelper.isMessageCTCP(messageContents)) {
            ctcp = CtcpEnum.from(messageContents)
            messageContents = CtcpHelper.trimCTCP(messageContents)

            if (ctcp === CtcpEnum.UNKNOWN) {
                LOGGER.warn("dropping unknown CTCP message: $target $messageContents")
                return
            }
        }

        if (channelTypesState.types.any { char -> target.startsWith(char) }) {
            // Channel message

            val channelState = channelsState[target]
            if (channelState == null) {
                LOGGER.warn("got a privmsg for a channel we don't think we're in, bailing: $message")
                return
            }

            var userState = channelState.users[source.nick]
            if (userState == null) {
                userState = generateUser(nick = source.nick)

                LOGGER.debug("got a privmsg for a user we don't think's in the channel, making a fake user $userState: $message")
            }

            val channel = WarrenChannel(state = channelState, client = client)
            val user = WarrenChannelUser(state = userState, channel = channel)

            when (ctcp) {
                CtcpEnum.NONE -> {
                    eventDispatcher.fire(ChannelMessageEvent(user = user, channel = channel, message = messageContents, metadata = metadata))

                    LOGGER.debug("$target <${source.nick}> $messageContents")
                }

                CtcpEnum.ACTION -> {
                    eventDispatcher.fire(ChannelActionEvent(user = user, channel = channel, message = messageContents, metadata = metadata))

                    LOGGER.debug("$target ${source.nick} * $messageContents")
                }

                else -> Unit
            }
        } else {
            // Private message

            when (ctcp) {
                CtcpEnum.NONE -> {
                    eventDispatcher.fire(PrivateMessageEvent(user = source, message = messageContents, metadata = metadata))

                    LOGGER.debug("PM: <${source.nick}> $messageContents")
                }

                CtcpEnum.ACTION -> {
                    eventDispatcher.fire(PrivateActionEvent(user = source, message = messageContents, metadata = metadata))

                    LOGGER.debug("PM: ${source.nick} * $messageContents")
                }

                else -> Unit
            }
        }
    }

}