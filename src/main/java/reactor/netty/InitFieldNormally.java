package reactor.netty;

import io.netty.channel.epoll.Epoll;

public class InitFieldNormally {
    public static final boolean isEpollAvailable = Epoll.isAvailable();
}
