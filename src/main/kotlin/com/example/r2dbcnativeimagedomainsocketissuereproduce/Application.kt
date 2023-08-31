package com.example.r2dbcnativeimagedomainsocketissuereproduce

import io.netty.channel.epoll.Epoll
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.r2dbc.core.DatabaseClient
import reactor.netty.InitFieldInStaticBlock
import reactor.netty.InitFieldNormally
import reactor.netty.UnbakedInStaticBlock
import reactor.netty.UnbakedNormally
import java.util.logging.Logger

private val logger = Logger.getLogger("Application")

@SpringBootApplication
class R2dbcNativeImageDomainSocketIssueReproduceApplication(
        private val databaseClient: DatabaseClient
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        //same code in reactor.netty.resources.DefaultLoopEpoll
        val epollClass = Class.forName("io.netty.channel.epoll.Epoll")
        logger.info("Reflect Epoll successful without exception. Class name: ${epollClass.name}")
        val epollAvailable = Epoll.isAvailable()
        logger.info("Epoll availability: $epollAvailable")
        //build time field baking
        logger.info("InitFieldNormally: ${InitFieldNormally.isEpollAvailable}")
        logger.info("InitFieldInStaticBlock: ${InitFieldInStaticBlock.isEpollAvailable}")
        logger.info("Unbaked: ${UnbakedNormally.otherFieldName}")
        logger.info("UnbakedInStaticBlock: ${UnbakedInStaticBlock.otherFieldName}")
        //database connect test
        logger.info("Connecting to database via domain socket")
        databaseClient.sql("SELECT 1").fetch().rowsUpdated().subscribe()
    }
}

fun main(args: Array<String>) {
    runApplication<R2dbcNativeImageDomainSocketIssueReproduceApplication>(*args)
}
