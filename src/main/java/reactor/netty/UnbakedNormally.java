package reactor.netty;

import io.netty.channel.epoll.Epoll;

public class UnbakedNormally {
    public static final boolean otherFieldName = Epoll.isAvailable();
}
