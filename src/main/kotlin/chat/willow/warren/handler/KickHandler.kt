package chat.willow.warren.handler

import chat.willow.kale.IMetadataStore
import chat.willow.kale.KaleHandler
import chat.willow.kale.helper.equalsIgnoreCase
import chat.willow.kale.irc.message.rfc1459.KickMessage
import chat.willow.warren.helper.loggerFor
import chat.willow.warren.state.CaseMappingState
import chat.willow.warren.state.ConnectionState
import chat.willow.warren.state.JoinedChannelsState

class KickHandler(val connectionState: ConnectionState, val channelsState: JoinedChannelsState, val caseMappingState: CaseMappingState) : KaleHandler<KickMessage.Message>(KickMessage.Message.Parser) {

    private val LOGGER = loggerFor<KickHandler>()


    override fun handle(message: KickMessage.Message, metadata: IMetadataStore) {
        val kickedNicks = message.users
        val channels = message.channels

        for (kickedNick in kickedNicks) {
            if (equalsIgnoreCase(caseMappingState.mapping, kickedNick, connectionState.nickname)) {
                // We were forcibly kicked

                val removedChannels = channels.map { channel -> channelsState.remove(channel) }
                LOGGER.debug("we were kicked from channels: $removedChannels")
            } else {
                // Someone else was kicked

                for ((name, channel) in channelsState.all) {
                    if (channel.users.contains(kickedNick)) {
                        channel.users.remove(kickedNick)
                    }
                }
            }
        }

        LOGGER.trace("kicks happened - new channels state: $channelsState")
    }

}