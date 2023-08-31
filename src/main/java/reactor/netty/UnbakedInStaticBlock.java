package reactor.netty;

import io.netty.channel.epoll.Epoll;

public class UnbakedInStaticBlock {
    public static final boolean otherFieldName;

    static {
        boolean epollCheck = false;
        try {
            Class.forName("io.netty.channel.epoll.Epoll");
            epollCheck = Epoll.isAvailable();
        } catch (ClassNotFoundException cnfe) {
            // noop
        }
        otherFieldName = epollCheck;
    }
}
