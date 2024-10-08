package best.leafyalex.gainzclient.events.packet

import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import net.minecraft.network.Packet
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.network.FMLNetworkEvent

class PacketListener : ChannelDuplexHandler() {

    @Throws(Exception::class)
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        var msg: Any? = msg
        var get = true

        if (msg is Packet<*>) {
            val inPacket = (msg as Packet<*>?)?.let { PacketEvent.Incoming(it) }
            MinecraftForge.EVENT_BUS.post(inPacket)

            if (inPacket?.isCanceled == true) {
                get = false
            }
            msg = inPacket?.getPacket()
        }
        if (get) super.channelRead(ctx, msg)
    }

    @Throws(Exception::class)
    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        var msg: Any? = msg
        var send = true

        if (msg is Packet<*>) {
            val outPacket = (msg as Packet<*>?)?.let { PacketEvent.Outgoing(it) }
            MinecraftForge.EVENT_BUS.post(outPacket)

            if (outPacket?.isCanceled == true) {
                send = false
            }
            msg = outPacket?.getPacket()
        }
        if (send) super.write(ctx, msg, promise)
    }

    @SubscribeEvent
    fun joinEvent(event: FMLNetworkEvent.ClientConnectedToServerEvent) {
        if (event.manager.channel().pipeline().get("duelsbooster_packet_handler") == null) {
            event.manager.channel().pipeline().addBefore(
                "packet_handler",
                "gainzclient_packet_handler",
                PacketListener()
            )
        }
    }
}
