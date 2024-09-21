package best.leafyalex.gainzclient.utils

import best.leafyalex.gainzclient.GainzClient
import net.minecraft.util.ChatComponentText
import net.minecraft.util.EnumChatFormatting

object ChatUtils {

    fun removeFormatting(text: String): String{
        var t = ""
        var skip = false
        for (i in text.indices) {
            if (!skip) {
                if (text[i] == '§') {
                    skip = true
                } else {
                    t += text[i]
                }
            } else {
                skip = false
            }
        }
        return t
    }

    fun sendAsPlayer(message: String) {
        if (GainzClient.mc.thePlayer != null) {
            GainzClient.mc.thePlayer.sendChatMessage(message)
        }
    }

    fun info(message: String) {
        sendChatMessage("[Bench] ${EnumChatFormatting.WHITE}$message")
    }

    fun error(message: String) {
        sendChatMessage("[Bench] ${EnumChatFormatting.RED}$message")
    }

    private fun sendChatMessage(message: String) {
        if (GainzClient.mc.thePlayer != null && GainzClient.config?.disableChatMessages != true) {
            GainzClient.mc.thePlayer.addChatMessage(ChatComponentText(message))
        }
    }

}