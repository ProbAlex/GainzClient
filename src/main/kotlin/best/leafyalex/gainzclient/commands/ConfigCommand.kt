package best.leafyalex.gainzclient.commands

import best.leafyalex.gainzclient.GainzClient
import gg.essential.api.EssentialAPI
import gg.essential.api.commands.Command
import gg.essential.api.commands.DefaultHandler

class ConfigCommand : Command("gainzclient") {

    @DefaultHandler
    fun handle() {
        EssentialAPI.getGuiUtil().openScreen(GainzClient.config?.gui())
    }
}
