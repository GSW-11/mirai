/*
 * Copyright 2019-2021 Mamoe Technologies and contributors.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/mamoe/mirai/blob/master/LICENSE
 */

package net.mamoe.mirai.internal.network.handler.components

import kotlinx.serialization.Serializable
import net.mamoe.mirai.internal.network.handler.component.ComponentKey
import net.mamoe.mirai.internal.network.handler.components.ServerList.Companion.DEFAULT_SERVER_LIST
import java.net.InetSocketAddress
import java.util.*

@Serializable
internal data class ServerAddress(
    val host: String,
    val port: Int
) {
    init {
        require(port >= 0) { "port must be positive: '$port'" }
        require(host.isNotBlank()) { "host is invalid: '$host'" }
    }

    fun toSocketAddress(): InetSocketAddress = InetSocketAddress.createUnresolved(host, port)
}

internal interface ServerList {
    fun setPreferred(list: Collection<ServerAddress>)
    fun getPreferred(): Set<ServerAddress>

    fun refresh()

    /**
     * [Poll][Queue.poll] from current address list. Returns `null` if current address list is empty.
     */
    fun pollCurrent(): ServerAddress?

    /**
     * [Poll][Queue.poll] from current address list, before which the list is filled with preferred addresses or default list if empty.
     */
    fun pollAny(): ServerAddress

    companion object : ComponentKey<ServerList> {
        val DEFAULT_SERVER_LIST: Set<ServerAddress> =
            """msfwifi.3g.qq.com:8080, 14.215.138.110:8080, 113.96.12.224:8080,
                |157.255.13.77:14000, 120.232.18.27:443, 
                |183.3.235.162:14000, 163.177.89.195:443, 183.232.94.44:80, 
                |203.205.255.224:8080, 203.205.255.221:8080""".trimMargin()
                .splitToSequence(",").filterNot(String::isBlank)
                .map { it.trim() }
                .map {
                    val host = it.substringBefore(':')
                    val port = it.substringAfter(':').toInt()
                    ServerAddress(host, port)
                }.shuffled().toMutableSet()
    }
}

/**
 * Queue of servers. Pop each time when trying to connect.
 */
internal class ServerListImpl(
    initial: Collection<ServerAddress> = emptyList()
) : ServerList {
    @Volatile
    private var preferred: Set<ServerAddress> = DEFAULT_SERVER_LIST

    @Volatile
    private var current: Queue<ServerAddress> = ArrayDeque(initial)

    @Synchronized
    override fun setPreferred(list: Collection<ServerAddress>) {
        require(list.isNotEmpty()) { "list cannot be empty." }
        preferred = list.toSet()
    }

    override fun getPreferred() = preferred

    init {
        refresh()
    }

    @Synchronized
    override fun refresh() {
        current = preferred.toCollection(ArrayDeque(current.size))
        check(current.isNotEmpty()) {
            "Internal error: failed to fill server list. No server available."
        }
    }

    /**
     * [Poll][Queue.poll] from current address list. Returns `null` if current address list is empty.
     */
    @Synchronized
    override fun pollCurrent(): ServerAddress? {
        return current.poll()
    }

    /**
     * [Poll][Queue.poll] from current address list, before which the list is filled with preferred addresses or default list if empty.
     */
    @Synchronized
    override fun pollAny(): ServerAddress {
        if (current.isEmpty()) refresh()
        return current.remove()
    }
}