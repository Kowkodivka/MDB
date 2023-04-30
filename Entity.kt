package io.lucin.core

import arc.net.*
import arc.struct.IntMap
import arc.util.Log.*
import arc.util.Threads
import mindustry.gen.*
import mindustry.net.ArcNetProvider.PacketSerializer
import mindustry.net.Packet
import mindustry.net.Packets.*
import mindustry.net.Streamable.StreamBuilder
import java.net.DatagramPacket

object Entity {
    class EntityBuilder(
        internal val hidden: Boolean,
        internal val packet: ConnectPacket,
        host: String,
        tcpPort: Int,
        udpPort: Int
    ) {
        internal val client: Client = Client(8192, 8192, PacketSerializer())

        init {
            client.setDiscoveryPacket { DatagramPacket(ByteArray(516), 516) }
            client.addListener(EntityListener(this))

            try {
                client.stop()

                Threads.daemon("CLIENT#${packet.uuid}") {
                    try {
                        client.run()
                    } catch (e: Exception) {
                        err(e)
                    }
                }

                client.connect(5000, host, tcpPort, udpPort)
            } catch (e: Exception) {
                err(e)
            }
        }
    }

    private class EntityListener(val entityBuilder: EntityBuilder) : NetListener {
        override fun connected(connection: Connection?) {
            val connect = Connect()

            if (connection != null) {
                connect.addressTCP = connection.remoteAddressTCP.address.hostAddress

                if (connection.remoteAddressTCP != null) {
                    connect.addressTCP = connection.remoteAddressTCP.toString()

                    info("Connecting to ${connect.addressTCP}")

                    val confirmCallPacket = ConnectConfirmCallPacket()

                    entityBuilder.client.sendTCP(entityBuilder.packet)

                    if (!entityBuilder.hidden) {
                        entityBuilder.client.sendTCP(confirmCallPacket)
                        info("Confirmed.")
                    }
                }
            }
        }

        override fun disconnected(connection: Connection?, reason: DcReason?) {
            if (reason != null) {
                warn("Disconnected. Reason: $reason.")
            }
        }
    }

    private class ChatListener : NetListener {
        override fun received(connection: Connection?, packet: Any?) {
            when (packet) {
                is SendMessageCallPacket -> {
                    try {
                        packet.handled()
                    } catch (e: Exception) {
                        err(e)
                    }

                    info(packet.message)
                }

                is SendMessageCallPacket2    -> {
                    try {
                        packet.handled()
                    } catch (e: Exception) {
                        err(e)
                    }

                    info(packet.message)
                }

                is SendChatMessageCallPacket -> {
                    try {
                        packet.handled()
                    } catch (e: Exception) {
                        err(e)
                    }

                    info(packet.message)
                }
            }
        }
    }

    private class DataListener(val entityBuilder: EntityBuilder) : NetListener {
        private val streams: IntMap<StreamBuilder> = IntMap()

        fun handleClientReceived(packet: Packet) {
            packet.handled()

            if (packet is StreamBegin) {
                streams.put(packet.id, StreamBuilder(packet))
            } else if (packet is StreamChunk) {
                val builder = streams.get(packet.id)
                if (builder == null) err("Received StreamChunk without a StreamBegin beforehand!")

                builder.add(packet.data)

                if (builder.isDone) {
                    streams.remove(builder.id)
                    handleClientReceived(builder.build())
                }
            } else {
                packet.handleClient()
            }
        }

        override fun received(connection: Connection?, packet: Any?) {
            handleClientReceived(packet as Packet)
        }
    }
}