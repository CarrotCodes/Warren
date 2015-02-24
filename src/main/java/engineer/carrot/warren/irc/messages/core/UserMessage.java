package engineer.carrot.warren.irc.messages.core;

import engineer.carrot.warren.irc.messages.MessageCodes;
import engineer.carrot.warren.IRCMessage;
import engineer.carrot.warren.irc.messages.IMessage;

import javax.annotation.Nonnull;

public class UserMessage implements IMessage {
    private String user;
    private String mode;
    private String realname;

    public UserMessage(@Nonnull String user, @Nonnull String mode, @Nonnull String realname) {
        this.user = user;
        this.mode = mode;
        this.realname = realname;
    }

    @Override
    public IRCMessage buildServerOutput() {
        return new IRCMessage.Builder().command(this.getCommandID()).parameters(this.user, this.mode, "*", this.realname).build();
    }

    @Override
    public boolean isMessageWellFormed(@Nonnull IRCMessage message) {
        return (!message.isPrefixSet() && message.isParametersExactlyExpectedLength(4));
    }

    @Override
    public String getCommandID() {
        return MessageCodes.USER;
    }
}
