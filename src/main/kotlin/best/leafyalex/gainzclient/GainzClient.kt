package best.leafyalex.gainzclient

import best.leafyalex.gainzclient.bot.BotBase
import best.leafyalex.gainzclient.bot.StateManager
import best.leafyalex.gainzclient.bot.bots.Sumo
import best.leafyalex.gainzclient.bot.player.LobbyMovement
import best.leafyalex.gainzclient.bot.player.Mouse
import best.leafyalex.gainzclient.commands.ConfigCommand
import best.leafyalex.gainzclient.core.Config
import best.leafyalex.gainzclient.core.KeyBindings
import best.leafyalex.gainzclient.events.packet.PacketListener
import com.google.gson.Gson
import net.minecraft.client.Minecraft
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.FMLInitializationEvent

@Mod(
    modid = GainzClient.MOD_ID,
    name = GainzClient.MOD_NAME,
    version = GainzClient.VERSION
)
class GainzClient {

    companion object {
        const val MOD_ID = "chromehud"
        const val MOD_NAME = "GainzClient"
        const val VERSION = "0.2.0"
        const val configLocation = "./config/gainzclient.toml"

        val mc: Minecraft = Minecraft.getMinecraft()
        val gson = Gson()
        var config: Config? = null
        var bot: BotBase? = null

        fun swapBot(b: BotBase) {
            if (bot != null) MinecraftForge.EVENT_BUS.unregister(bot) // make sure to unregister the current bot
            bot = b
            MinecraftForge.EVENT_BUS.register(bot) // register the new bot
        }
    }

    @Mod.EventHandler
    fun init(event: FMLInitializationEvent) {
        config = Config()
        config?.preload()

        ConfigCommand().register()
        KeyBindings.register()

        MinecraftForge.EVENT_BUS.register(PacketListener())
        MinecraftForge.EVENT_BUS.register(StateManager)
        MinecraftForge.EVENT_BUS.register(Mouse)
        MinecraftForge.EVENT_BUS.register(LobbyMovement)
        MinecraftForge.EVENT_BUS.register(KeyBindings)

        swapBot(config?.bots?.get(config?.currentBot ?: 0) ?: Sumo())
    }
}
