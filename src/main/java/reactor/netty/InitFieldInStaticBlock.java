package reactor.netty;

import io.netty.channel.epoll.Epoll;

public class InitFieldInStaticBlock {
    public static final boolean isEpollAvailable;

    static {
        boolean epollCheck = false;
        try {
            Class.forName("io.netty.channel.epoll.Epoll");
            epollCheck = Epoll.isAvailable();
        } catch (ClassNotFoundException cnfe) {
            // noop
        }
        isEpollAvailable = epollCheck;
    }
}
