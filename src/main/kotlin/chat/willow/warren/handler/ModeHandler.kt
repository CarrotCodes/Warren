package chat.willow.warren.handler

import chat.willow.kale.IMetadataStore
import chat.willow.kale.KaleHandler
import chat.willow.kale.irc.message.rfc1459.ModeMessage
import chat.willow.warren.IClientMessageSending
import chat.willow.warren.WarrenChannel
import chat.willow.warren.event.ChannelModeEvent
import chat.willow.warren.event.IWarrenEventDispatcher
import chat.willow.warren.event.UserModeEvent
import chat.willow.warren.helper.loggerFor
import chat.willow.warren.state.CaseMappingState
import chat.willow.warren.state.ChannelTypesState
import chat.willow.warren.state.JoinedChannelsState
import chat.willow.warren.state.UserPrefixesState

class ModeHandler(val eventDispatcher: IWarrenEventDispatcher,
                  val client: IClientMessageSending,
                  val channelTypesState: ChannelTypesState,
                  val channelsState: JoinedChannelsState,
                  val userPrefixesState: UserPrefixesState,
                  val caseMappingState: CaseMappingState) : KaleHandler<ModeMessage.Message>(ModeMessage.Message.Parser) {

    private val LOGGER = loggerFor<ModeHandler>()


    override fun handle(message: ModeMessage.Message, metadata: IMetadataStore) {
        val target = message.target

        if (channelTypesState.types.any { char -> target.startsWith(char) }) {
            // Channel mode

            val channelState = channelsState[target]
            if (channelState == null) {
                LOGGER.warn("user mode changed for a channel we don't think we're in, bailing: $message")
                return
            }

            for (modifier in message.modifiers) {
                if (userPrefixesState.prefixesToModes.values.contains(modifier.mode)) {
                    // User mode changed

                    val nick = modifier.parameter
                    if (nick == null) {
                        LOGGER.warn("user mode changed but missing users name from mode modifier, bailing: $message")
                        continue
                    }

                    val user = channelState.users[nick]
                    if (user == null) {
                        LOGGER.warn("user mode changed but not tracking that user, bailing: $message")
                        continue
                    }

                    when {
                        modifier.isAdding -> {
                            user.modes += modifier.mode
                        }

                        modifier.isRemoving -> {
                            user.modes -= modifier.mode
                        }
                    }

                    LOGGER.debug("user mode state changed: $user")
                }

                val channel = WarrenChannel(state = channelState, client = client)
                eventDispatcher.fire(ChannelModeEvent(user = message.source, channel = channel, modifier = modifier, metadata = metadata))
            }
        } else {
            // User mode

            LOGGER.info("user changed modes: $message")

            for (modifier in message.modifiers) {
                eventDispatcher.fire(UserModeEvent(user = target, modifier = modifier, metadata = metadata))
            }
        }
    }

}