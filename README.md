# R2DBC via domain socket in native image issue reproduce demo

To reproduce the problem talking in the https://github.com/spring-projects/spring-framework/issues/31141

# How this demo created

With [Spring Initializr](https://start.spring.io/#!type=gradle-project&language=kotlin&platformVersion=3.1.3&packaging=jar&jvmVersion=17&groupId=com.example&artifactId=r2dbc-native-image-domain-socket-issue-reproduce&name=r2dbc-native-image-domain-socket-issue-reproduce&description=r2dbc%20native%20image%20with%20domain%20socket&packageName=com.example.r2dbc-native-image-domain-socket-issue-reproduce&dependencies=native,webflux,data-r2dbc,postgresql)

# Steps

Firstly, make sure you have Graalvm with native image installed, in this demo we will
use [17.0.8-graal](https://github.com/graalvm/graalvm-ce-builds/releases/tag/jdk-17.0.8)

Executing environment: Ubuntu22.04(compile and run in same machine)

Compile application to native(or use `bootBuildImage` to get a docker image):

```bash
./gradlew nativeCompile
```

During the compile processing, these logs are printed:

```
Field reactor.netty.resources.DefaultLoopIOUring#isIoUringAvailable set to false at build time
Field reactor.netty.resources.DefaultLoopEpoll#isEpollAvailable set to false at build time
Field reactor.netty.resources.DefaultLoopKQueue#isKqueueAvailable set to false at build time
Field reactor.netty.InitFieldNormally#isEpollAvailable set to false at build time
Field reactor.netty.InitFieldInStaticBlock#isEpollAvailable set to false at build time
```

Last two logs we will talk them later. Please notice first three logs.

Especially the `reactor.netty.resources.DefaultLoopEpoll#isEpollAvailable set to false`, this is an obvious mistake,
**there's impossible don't have epoll on Linux**.

These logs are print by `org.springframework.aot.nativex.feature.PreComputeFieldFeature`, which
use `registerFieldValueTransformer` to early baking field value in build time.

The program is **now in the compilation phase**, and has not yet move to the runtime phase. But the field value already
wrong! This problem is **NOT** related to native image runtime reflection.

After compile, execute the ELF or run the docker image:

```bash
./build/native/nativeCompile/r2dbc-native-image-domain-socket-issue-reproduce
```

An exception will throw out:

```
Caused by: java.lang.IllegalArgumentException: Unsupported channel type: DomainSocketChannel
        at reactor.netty.resources.DefaultLoopNIO.getChannel(DefaultLoopNIO.java:50) ~[na:na]
        at reactor.netty.resources.LoopResources.onChannel(LoopResources.java:243) ~[r2dbc-native-image-domain-socket-issue-reproduce:1.1.10]
        at reactor.netty.tcp.TcpResources.onChannel(TcpResources.java:251) ~[r2dbc-native-image-domain-socket-issue-reproduce:1.1.10]
        at reactor.netty.transport.TransportConfig.lambda$connectionFactory$1(TransportConfig.java:277) ~[r2dbc-native-image-domain-socket-issue-reproduce:1.1.10]
        at reactor.netty.transport.TransportConnector.doInitAndRegister(TransportConnector.java:277) ~[na:na]
        at reactor.netty.transport.TransportConnector.connect(TransportConnector.java:164) ~[na:na]
        at reactor.netty.transport.TransportConnector.connect(TransportConnector.java:123) ~[na:na]
        at reactor.netty.resources.NewConnectionProvider.lambda$acquire$0(NewConnectionProvider.java:81) ~[na:na]
        ... 14 common frames omitted
```

Note that react-netty uses `DefaultLoopNIO` since epoll is not available. This problem broken R2DBC connection via
domain
socket.

logs from reactor-netty:

```
2023-08-31T23:05:26.526+08:00 DEBUG 20645 --- [           main] r.netty.resources.DefaultLoopIOUring     : Default io_uring support : false
2023-08-31T23:05:26.527+08:00 DEBUG 20645 --- [           main] r.netty.resources.DefaultLoopEpoll       : Default Epoll support : false
2023-08-31T23:05:26.527+08:00 DEBUG 20645 --- [           main] r.netty.resources.DefaultLoopKQueue      : Default KQueue support : false
```

# Localization problem

There are such field match pattern in `org.springframework.aot.nativex.feature.PreComputeFieldFeature`:

```java
Pattern.compile(Pattern.quote("reactor.")+".*#.*Available")
```

All field in reactor package and name end with 'Available' will be selected. This is very useful
for [reactor](https://github.com/reactor/reactor-core), but will
destroy [reactor-netty](https://github.com/reactor/reactor-netty) since all reactor-netty class also in reactor package.
Availability check of netty native transport should always happen in runtime, not build time.

Consider following code in ApplicationRunner:

```kotlin
//same code in reactor.netty.resources.DefaultLoopEpoll
val epollClass = Class.forName("io.netty.channel.epoll.Epoll")
logger.info("Reflect Epoll successful without exception. Class name: ${epollClass.name}")   //io.netty.channel.epoll.Epoll
val epollAvailable = Epoll.isAvailable()
logger.info("Epoll availability: $epollAvailable")  //true
//build time field baking
logger.info("InitFieldNormally: ${InitFieldNormally.isEpollAvailable}") //false
logger.info("InitFieldInStaticBlock: ${InitFieldInStaticBlock.isEpollAvailable}")   //false
logger.info("Unbaked: ${UnbakedNormally.otherFieldName}")   //true
logger.info("UnbakedInStaticBlock: ${UnbakedInStaticBlock.otherFieldName}") //true
```

Output:

```
2023-08-31T23:05:26.548+08:00  INFO 20645 --- [           main] Application                              : Reflect Epoll successful without exception. Class name: io.netty.channel.epoll.Epoll
2023-08-31T23:05:26.548+08:00  INFO 20645 --- [           main] Application                              : Epoll availability: true
2023-08-31T23:05:26.548+08:00  INFO 20645 --- [           main] Application                              : InitFieldNormally: false
2023-08-31T23:05:26.548+08:00  INFO 20645 --- [           main] Application                              : InitFieldInStaticBlock: false
2023-08-31T23:05:26.548+08:00  INFO 20645 --- [           main] Application                              : Unbaked: true
2023-08-31T23:05:26.548+08:00  INFO 20645 --- [           main] Application                              : UnbakedInStaticBlock: true
```

In the first four line, we do the same thing in `reactor.netty.resources.DefaultLoopEpoll` static block. Origin code:

```
static final boolean isEpollAvailable;
  
static {
    boolean epollCheck = false;
    try {
        Class.forName("io.netty.channel.epoll.Epoll");
        epollCheck = Epoll.isAvailable();
    }
    catch (ClassNotFoundException cnfe) {
        // noop
     }
    isEpollAvailable = epollCheck;
    if (log.isDebugEnabled()) {
        log.debug("Default Epoll support : " + isEpollAvailable);
    }
}
```

Whether you get the Epoll through reflection or `Epoll.isAvailable()` works fine in runtime.

Then design two classes for testing the baking results under different initialization ways:

```java
package reactor.netty;

import io.netty.channel.epoll.Epoll;

public class InitFieldNormally {
    public static final boolean isEpollAvailable = Epoll.isAvailable();
}

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
```

These two class are located in package `reactor.netty` and have a field end with `Available`. All fields value **always
be false** due to `PreComputeFieldFeature`.

And we have another two class without field name matched:

```java
package reactor.netty;

import io.netty.channel.epoll.Epoll;

public class UnbakedNormally {
    public static final boolean otherFieldName = Epoll.isAvailable();
}

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
```

**All fields value will be true**.

So the problem is that `PreComputeFieldFeature` incorrectly selects all reactor-netty fields whose value should be
determined at runtime, not build time.
