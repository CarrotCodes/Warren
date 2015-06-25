package engineer.carrot.warren.warren.irc.messages.RPL;

import com.google.common.base.Splitter;
import com.google.common.collect.Maps;
import engineer.carrot.warren.warren.irc.CharacterCodes;
import engineer.carrot.warren.warren.irc.messages.AbstractMessage;
import engineer.carrot.warren.warren.irc.messages.IrcMessage;
import engineer.carrot.warren.warren.irc.messages.MessageCodes;

import java.util.List;
import java.util.Map;

public class ISupportMessage extends AbstractMessage {
    private String forUser;
    public Map<String, String> parameters;

    // Inbound

    @Override
    public boolean populate(IrcMessage message) {
        // {"prefix":"chalk.uuid.uk","parameters":["carrot","CHANTYPES\u003d\u0026#","EXCEPTS","INVEX","CHANMODES\u003deIb,k,l,imnpstSr","CHANLIMIT\u003d\u0026#:50","PREFIX\u003d(ov)@+","MAXLIST\u003dbeI:50","MODES\u003d4","NETWORK\u003dImaginaryNet","KNOCK","STATUSMSG\u003d@+","CALLERID\u003dg","are supported by this server"],"command":"005"}
        // {"prefix":"chalk.uuid.uk","parameters":["carrot","SAFELIST","ELIST\u003dU","CASEMAPPING\u003drfc1459","CHARSET\u003dascii","NICKLEN\u003d30","CHANNELLEN\u003d50","TOPICLEN\u003d390","ETRACE","CPRIVMSG","CNOTICE","DEAF\u003dD","MONITOR\u003d100","are supported by this server"],"command":"005"}

        if (!message.hasPrefix() || message.parameters.size() < 2) {
            return false;
        }

        this.forUser = message.parameters.get(0);
        this.parameters = this.parseParameters(message);

        return true;
    }

    private Map<String, String> parseParameters(IrcMessage message) {
        Map<String, String> returnParameters = Maps.newHashMap();

        int messageParametersSize = message.parameters.size();
        // Parameter 0 is the username

        for (int i = 1; i < messageParametersSize - 1; i++) {
            String parameter = message.parameters.get(i);
            // Can be of two types: X=Y or X
            // Y must be parsed depending on the value of X

            List<String> keyValue = Splitter.on(CharacterCodes.EQUALS).limit(2).splitToList(parameter);
            if (keyValue.size() == 1) {
                returnParameters.put(keyValue.get(0), null);
            } else {
                returnParameters.put(keyValue.get(0), keyValue.get(1));
            }
        }

        // Last parameter is "are supported by this server"
        // Ignore it
        String lastParameter = message.parameters.get(messageParametersSize - 1);

        return returnParameters;
    }

    // Shared

    @Override
    public String getCommand() {
        return MessageCodes.RPL.ISUPPORT;
    }
}
