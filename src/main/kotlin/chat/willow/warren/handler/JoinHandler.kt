package chat.willow.warren.handler

import engineer.carrot.warren.kale.IKaleHandler
import engineer.carrot.warren.kale.irc.message.rfc1459.JoinMessage
import chat.willow.warren.helper.loggerFor
import chat.willow.warren.state.*

class JoinHandler(val connectionState: ConnectionState, val joiningChannelsState: JoiningChannelsState, val joinedChannelsState: JoinedChannelsState, val caseMappingState: CaseMappingState) : IKaleHandler<JoinMessage> {

    private val LOGGER = loggerFor<JoinHandler>()

    override val messageType = JoinMessage::class.java

    override fun handle(message: JoinMessage, tags: Map<String, String?>) {
        val channelNames = message.channels
        val source = message.source

        if (source == null) {
            LOGGER.trace("got a JOIN but the source was null - not doing anything with it")
            return
        }

        val nick = source.nick

        if (nick == connectionState.nickname) {
            // Us joining a channel

            LOGGER.debug("we joined channels: ${message.channels}")

            for (channelName in channelNames) {
                if (!joinedChannelsState.contains(channelName)) {
                    LOGGER.trace("adding $channelName to joined channels with 0 users")

                    joinedChannelsState += ChannelState(channelName, users = generateUsers(mappingState = caseMappingState))
                } else {
                    LOGGER.trace("we're already in $channelName - not adding it again")
                }

                LOGGER.trace("removing channel from joining state: $channelName")
                joiningChannelsState -= channelName
            }
        } else {
            // Someone else joined a channel

            for (channelName in channelNames) {
                val channelState = joinedChannelsState[channelName]
                if (channelState != null) {
                    channelState.users += generateUser(nick)

                    LOGGER.trace("new channel state: $channelState")
                } else {
                    LOGGER.warn("we were given a JOIN for a channel we aren't in - not doing anything with it: $channelName")
                }
            }
        }
    }

}