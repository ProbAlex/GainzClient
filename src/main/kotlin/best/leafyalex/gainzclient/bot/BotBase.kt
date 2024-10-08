package best.leafyalex.gainzclient.bot

import best.leafyalex.gainzclient.GainzClient
import best.leafyalex.gainzclient.bot.player.*
import best.leafyalex.gainzclient.core.KeyBindings
import best.leafyalex.gainzclient.utils.*
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiMainMenu
import net.minecraft.client.gui.GuiMultiplayer
import net.minecraft.client.multiplayer.GuiConnecting
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.network.Packet
import net.minecraft.network.play.server.S19PacketEntityStatus
import net.minecraft.network.play.server.S3EPacketTeams
import net.minecraft.network.play.server.S45PacketTitle
import net.minecraft.util.EnumChatFormatting
import net.minecraftforge.client.event.ClientChatReceivedEvent
import net.minecraftforge.event.entity.EntityJoinWorldEvent
import net.minecraftforge.event.entity.player.AttackEntityEvent
import net.minecraftforge.fml.client.FMLClientHandler
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientConnectedToServerEvent
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.Timer
import kotlin.concurrent.thread

/**
 * Base class for all bots
 * @param queueCommand Command to join a new game
 * @param quickRefresh MS for which to quickly refresh opponent entity
 */
open class BotBase(val queueCommand: String, val quickRefresh: Int = 10000) {

    protected val mc = Minecraft.getMinecraft()

    private var toggled = false
    fun toggled() = toggled
    fun toggle() {
        toggled = !toggled
    }

    private var attackedID = -1

    private var statKeys: Map<String, String> = mapOf("wins" to "", "losses" to "", "ws" to "")

    private var playerCache: HashMap<String, String> = hashMapOf()
    private var playersSent: ArrayList<String> = arrayListOf()
    private var playersQuit: ArrayList<String> = arrayListOf()
    private var playersLost: ArrayList<String> = arrayListOf()

    private var opponent: EntityPlayer? = null
    private var opponentTimer: Timer? = null
    private var calledFoundOpponent = false

    protected var combo = 0
    protected var opponentCombo = 0
    protected var ticksSinceHit = 0

    private var reconnectTimer: Timer? = null

    private var ticksSinceGameStart = 0

    private var lastOpponentName = ""

    private var calledGameEnd = false

    fun opponent() = opponent

    /********
     * Methods to override
     ********/

    open fun getName(): String {
        return "Base"
    }

    /**
     * Called when the bot attacks the opponent
     * Triggered by the damage sound, not the clientside attack event
     */
    protected open fun onAttack() {}

    /**
     * Called when the bot is attacked
     * Triggered by the damage sound, not the clientside attack event
     */
    protected open fun onAttacked() {}

    /**
     * Called when the game starts
     */
    protected open fun onGameStart() {}

    /**
     * Called when the game ends
     */
    protected open fun onGameEnd() {}

    /**
     * Called when the bot joins a game
     */
    protected open fun onJoinGame() {}

    /**
     * Called before the game starts (1s)
     */
    protected open fun beforeStart() {}

    /**
     * Called before the bot leaves the game (dodge)
     */
    protected open fun beforeLeave() {}

    /**
     * Called when the opponent entity is found
     */
    protected open fun onFoundOpponent() {}

    /**
     * Called every tick
     */
    protected open fun onTick() {}

    /********
     * Protected Methods
     ********/

    protected fun setStatKeys(keys: Map<String, String>) {
        statKeys = keys
    }

    /********
     * Base Methods
     ********/

    fun onPacket(packet: Packet<*>) {
        if (toggled) {
            when (packet) {
                is S19PacketEntityStatus -> { // use the status packet for attack events
                    if (packet.opCode.toInt() == 2) { // damage
                        val entity = packet.getEntity(mc.theWorld)
                        if (entity != null) {
                            if (entity.entityId == attackedID) {
                                attackedID = -1
                                onAttack()
                                combo++
                                opponentCombo = 0
                                ticksSinceHit = 0
                            } else if (mc.thePlayer != null && entity.entityId == mc.thePlayer.entityId) {
                                onAttacked()
                                combo = 0
                                opponentCombo++
                            }
                        }
                    }
                }
                is S3EPacketTeams -> { // use this for stat checking
                    if (packet.action == 3 && packet.name == "§7§k") { // action 3 is ADD
                        val players = packet.players
                        for (player in players) {
                            if (playersQuit.contains(player)) {
                                playersQuit.remove(player)
                            }
                            TimeUtils.setTimeout(fun () { // timeout to allow ingame state to update
                                handlePlayer(player)
                            }, 1500)
                        }
                    } else if (packet.action == 4 && packet.name == "§7§k") { // action 4 is REMOVE
                        val players = packet.players
                        for (player in players) {
                            playersQuit.add(player)
                        }
                    }
                }
                is S45PacketTitle -> { // use this to determine who won the duel
                    if (mc.theWorld != null) {
                        TimeUtils.setTimeout(fun () {
                            if (packet.message != null) {
                                val unformatted = packet.message.unformattedText.lowercase()
                                if (unformatted.contains("won the duel!") && mc.thePlayer != null) {
                                    var winner = ""
                                    var loser = ""
                                    var iWon = false

                                    val p = ChatUtils.removeFormatting(packet.message.unformattedText).split("won")[0].trim()
                                    if (unformatted.contains(mc.thePlayer.displayNameString.lowercase())) {
                                        Session.wins++
                                        winner = mc.thePlayer.displayNameString
                                        loser = lastOpponentName
                                        iWon = true
                                    } else {
                                        Session.losses++
                                        ChatUtils.info("Adding $p to the list of players to dodge...")
                                        playersLost.add(p)
                                        winner = p
                                        loser = mc.thePlayer.displayNameString
                                        iWon = false
                                    }

                                    ChatUtils.info(Session.getSession())

                                    if (!iWon) {
                                        TimeUtils.setTimeout(this::joinGame, RandomUtils.randomIntInRange(1000, 2000))
                                    }

                                    if ((GainzClient.config?.disconnectAfterGames ?: 0) > 0) {
                                        if (Session.wins + Session.losses >= GainzClient.config?.disconnectAfterGames!!) {
                                            ChatUtils.info("Played ${GainzClient.config?.disconnectAfterGames} games, disconnecting...")
                                            TimeUtils.setTimeout(fun () {
                                                ChatUtils.sendAsPlayer("/l duels")
                                                TimeUtils.setTimeout(fun () {
                                                    toggle()
                                                    disconnect()
                                                }, RandomUtils.randomIntInRange(2300, 5000))
                                            }, RandomUtils.randomIntInRange(900, 1700))
                                        }
                                    }

                                    if ((GainzClient.config?.disconnectAfterMinutes ?: 0) > 0) {
                                        if (System.currentTimeMillis() - Session.startTime >= GainzClient.config?.disconnectAfterMinutes!! * 60 * 1000) {
                                            ChatUtils.info("Played for ${GainzClient.config?.disconnectAfterMinutes} minutes, disconnecting...")
                                            TimeUtils.setTimeout(fun () {
                                                ChatUtils.sendAsPlayer("/l duels")
                                                TimeUtils.setTimeout(fun () {
                                                    toggle()
                                                    disconnect()
                                                }, RandomUtils.randomIntInRange(2300, 5000))
                                            }, RandomUtils.randomIntInRange(900, 1700))
                                        }
                                    }

                                    if (GainzClient.config?.sendWebhookMessages == true) {
                                        if (GainzClient.config?.webhookURL != "") {
                                            val opponentName = if (iWon) loser else winner
                                            val faceUrl = if (playerCache.containsKey(opponentName)) {
                                                "https://crafatar.com/avatars/${playerCache[opponentName]}"
                                            } else {
                                                "https://media.discordapp.net/attachments/1186644651852169248/1282117921564266593/IMG_0259.png?ex=66de30dd&is=66dcdf5d&hm=bc1c022c6bb1ebe888df5f420b439b056ba29436dd8365cf015daeab5bcab237&=&format=webp&quality=lossless&width=529&height=676"
                                            }

                                            val duration = StateManager.lastGameDuration / 1000

                                            // Send the webhook embed
                                            val fields = WebHook.buildFields(arrayListOf(mapOf("name" to "Winner", "value" to winner, "inline" to "true"), mapOf("name" to "Loser", "value" to loser, "inline" to "true"), mapOf("name" to "Bot Started", "value" to "<t:${(Session.startTime / 1000).toInt()}:R>", "inline" to "false")))
                                            val footer = WebHook.buildFooter(ChatUtils.removeFormatting(Session.getSession()), "https://media.discordapp.net/attachments/1186644651852169248/1282117921564266593/IMG_0259.png?ex=66de30dd&is=66dcdf5d&hm=bc1c022c6bb1ebe888df5f420b439b056ba29436dd8365cf015daeab5bcab237&=&format=webp&quality=lossless&width=529&height=676")
                                            val author = WebHook.buildAuthor("GainzClient - ${getName()}", "https://media.discordapp.net/attachments/1186644651852169248/1282117921564266593/IMG_0259.png?ex=66de30dd&is=66dcdf5d&hm=bc1c022c6bb1ebe888df5f420b439b056ba29436dd8365cf015daeab5bcab237&=&format=webp&quality=lossless&width=529&height=676")
                                            val thumbnail = WebHook.buildThumbnail(faceUrl)

                                            WebHook.sendEmbed(
                                                GainzClient.config?.webhookURL!!,
                                                WebHook.buildEmbed("${if (iWon) ":smirk:" else ":confused:"} Game ${if (iWon) "WON" else "LOST"}!", "Game Duration: `${duration}`s", fields, footer, author, thumbnail, if (iWon) 0x66ed8a else 0xed6d66))
                                        } else {
                                            ChatUtils.error("Webhook URL hasn't been set!")
                                        }
                                    }
                                }
                            }
                        }, 1000)
                    }

                }
            }
        }
    }

    @SubscribeEvent
    fun onAttackEntityEvent(ev: AttackEntityEvent) {
        if (toggled() && ev.entity == mc.thePlayer) {
            attackedID = ev.target.entityId
        }
    }

    @SubscribeEvent
    fun onClientTick(ev: ClientTickEvent) {
        registerPacketListener()
        if (toggled) {
            onTick()

            if (StateManager.state != StateManager.States.PLAYING) {
                ticksSinceGameStart++
                if (ticksSinceGameStart / 20 > (GainzClient.config?.rqNoGame ?: 30)) {
                    ticksSinceGameStart = 0
                    joinGame()
                }
            } else {
                ticksSinceGameStart = 0
            }

            if (mc.thePlayer != null && opponent != null) {
                ticksSinceHit++

                val distance = EntityUtils.getDistanceNoY(mc.thePlayer, opponent)

                if (distance > 5 && (combo != 0 || opponentCombo != 0)) {
                    combo = 0
                    opponentCombo = 0
                    ChatUtils.info("combo reset")
                }
            }
        }

        if (KeyBindings.toggleBotKeyBinding.isPressed) {
            toggle()
            ChatUtils.info("Fighting Status: ${if (toggled()) "${EnumChatFormatting.GREEN}on" else "${EnumChatFormatting.RED}off"}")
            if (toggled()) {
                ChatUtils.info("Current selected bot: ${EnumChatFormatting.GREEN}${getName()}")
                joinGame()
                Session.startTime = System.currentTimeMillis()
            }
        }
    }

    @SubscribeEvent
    fun onChat(ev: ClientChatReceivedEvent) {
        val unformatted = ev.message.unformattedText
        if (toggled() && mc.thePlayer != null) {

            if (unformatted.contains("The game starts in 2 seconds!")) {
                println(playersSent.joinToString(", "))
                var found = false
                if (playersSent.contains(mc.thePlayer.displayNameString)) {
                    if (playersSent.size > 1) {
                        found = true
                    }
                } else {
                    if (playersSent.size > 0) {
                        found = true
                    }
                }

                if (!found && false == true) {
                    ChatUtils.info("No stats found, dodging...")
                    leaveGame()
                    sendDodgeWebhook("")
                    TimeUtils.setTimeout(this::joinGame, RandomUtils.randomIntInRange(4000, 6000))
                }
            } else if (unformatted.contains("The game starts in 1 second!")) {
                beforeStart()
            }

            if (unformatted.contains("Are you sure? Type /lobby again")) {
                leaveGame()
            }

            if (unformatted.contains("Opponent:")) {
                gameStart()
            }

            if (unformatted.contains("Accuracy") && !calledGameEnd) {
                calledGameEnd = true
                gameEnd()
            }

            // Failsafe
            if (unformatted.lowercase().contains("something went wrong trying") || unformatted.lowercase().contains("please don't spam the command")) {
                TimeUtils.setTimeout(this::joinGame, RandomUtils.randomIntInRange(6000, 8000))
            } else if (unformatted.contains("A disconnect occurred in your connection, so you were put")) {
                Movement.clearAll()
                Mouse.stopLeftAC()
                TimeUtils.setTimeout(this::joinGame, RandomUtils.randomIntInRange(6000, 8000))
            }

            if (unformatted.contains("Woah there, slow down!") && GainzClient.config?.strictDodging == true) {
                disconnect()
                TimeUtils.setTimeout(this::reconnect, RandomUtils.randomIntInRange(4000, 5000))
            }

        }

        if (unformatted.contains("Your new API key is ")) {
            val key = ev.message.unformattedText.split("Your new API key is ")[1]
            GainzClient.config?.apiKey = key
            GainzClient.config?.writeData()
            ChatUtils.info("Saved API Key!")
        }
    }

    @SubscribeEvent
    fun onJoinWorld(ev: EntityJoinWorldEvent) {
        if (GainzClient.mc.thePlayer != null && ev.entity == GainzClient.mc.thePlayer) {
            if (toggled()) {
                resetVars()
                LobbyMovement.stop()
                Movement.clearAll()
                Combat.stopRandomStrafe()
                Mouse.stopLeftAC()
                calledGameEnd = false
            }
        }
    }

    @SubscribeEvent
    fun onConnect(ev: ClientConnectedToServerEvent) {
        if (toggled()) {
            println("Reconnect successful!")

            val author = WebHook.buildAuthor("GainzClient - ${getName()}", "https://media.discordapp.net/attachments/1186644651852169248/1282117921564266593/IMG_0259.png?ex=66de30dd&is=66dcdf5d&hm=bc1c022c6bb1ebe888df5f420b439b056ba29436dd8365cf015daeab5bcab237&=&format=webp&quality=lossless&width=529&height=676")
            val thumbnail = WebHook.buildThumbnail("https://media.discordapp.net/attachments/1186644651852169248/1282117921564266593/IMG_0259.png?ex=66de30dd&is=66dcdf5d&hm=bc1c022c6bb1ebe888df5f420b439b056ba29436dd8365cf015daeab5bcab237&=&format=webp&quality=lossless&width=529&height=676")

            WebHook.sendEmbed(
                GainzClient.config?.webhookURL!!,
                WebHook.buildEmbed("Reconnected!", "The bot successfully reconnected!", JsonArray(), JsonObject(), author, thumbnail, 0x66ed8a))


            reconnectTimer?.cancel()
            TimeUtils.setTimeout(this::joinGame, RandomUtils.randomIntInRange(6000, 8000))
        }
    }

    @SubscribeEvent
    fun onDisconnect(ev: ClientDisconnectionFromServerEvent) {
        if (toggled()) { // well that wasn't supposed to happen, try and reconnect
            println("Disconnected from server, reconnecting...")

            val author = WebHook.buildAuthor("GainzClient - ${getName()}", "https://media.discordapp.net/attachments/1186644651852169248/1282117921564266593/IMG_0259.png?ex=66de30dd&is=66dcdf5d&hm=bc1c022c6bb1ebe888df5f420b439b056ba29436dd8365cf015daeab5bcab237&=&format=webp&quality=lossless&width=529&height=676")
            val thumbnail = WebHook.buildThumbnail("https://media.discordapp.net/attachments/1186644651852169248/1282117921564266593/IMG_0259.png?ex=66de30dd&is=66dcdf5d&hm=bc1c022c6bb1ebe888df5f420b439b056ba29436dd8365cf015daeab5bcab237&=&format=webp&quality=lossless&width=529&height=676")

            WebHook.sendEmbed(
                GainzClient.config?.webhookURL!!,
                WebHook.buildEmbed("Disconnected!", "The bot was disconnected! Attempting to reconnect...", JsonArray(), JsonObject(), author, thumbnail, 0xed6d66))

            TimeUtils.setTimeout(fun () {
                reconnectTimer = TimeUtils.setInterval(this::reconnect, 0, 30000)
            }, RandomUtils.randomIntInRange(5000, 7000))
        }
    }

    /********
     * Private Methods
     ********/

    private fun resetVars() {
        playersSent.clear()
        playersQuit.clear()
        calledFoundOpponent = false
        opponentTimer?.cancel()
        opponent = null
        combo = 0
        opponentCombo = 0
        ticksSinceHit = 0
        ticksSinceGameStart = 0
    }

    private fun gameStart() {
        if (toggled()) {
            if (GainzClient.config?.sendStartMessage == true) {
                TimeUtils.setTimeout(fun () {
                    ChatUtils.sendAsPlayer("/ac " + (GainzClient.config?.startMessage ?: "glhf!"))
                }, GainzClient.config?.startMessageDelay ?: 100)
            }

            val quickRefreshTimer = TimeUtils.setInterval(this::bakery, 200, 50)
            TimeUtils.setTimeout(fun () {
                quickRefreshTimer?.cancel()
                opponentTimer = TimeUtils.setInterval(this::bakery, 0, 500)
            }, quickRefresh)

            onGameStart()
        }
    }

    private fun gameEnd() {
        if (toggled()) {
            onGameEnd()
            resetVars()

            if (GainzClient.config?.sendAutoGG == true) {
                TimeUtils.setTimeout(fun () {
                    ChatUtils.sendAsPlayer("/ac " + (GainzClient.config?.ggMessage ?: "gg"))
                }, GainzClient.config?.ggDelay ?: 100)
            }

            val delay = GainzClient.config?.autoRqDelay ?: 2000
            if (GainzClient.config?.fastRequeue == true) {
                TimeUtils.setTimeout(this::joinGame, RandomUtils.randomIntInRange(300, 500))
            } else {
                TimeUtils.setTimeout(this::joinGame, delay)
            }
        }
    }

    private fun bakery() {
        if (StateManager.state == StateManager.States.PLAYING) {
            val entity = EntityUtils.getOpponentEntity()
            if (entity != null) {
                opponent = entity
                lastOpponentName = opponent!!.displayNameString
                if (!calledFoundOpponent) {
                    calledFoundOpponent = true
                    onFoundOpponent()
                }
            }
        }
    }

    /**
     * Called for each player that joins
     */
    private fun handlePlayer(player: String) {
        thread { // move into new thread to avoid blocking the main thread
            if (StateManager.state == StateManager.States.GAME) { // make sure we're in-game, to not spam the api
                if (player.length > 2) { // hypixel sends a bunch of fake 1-2 char entities
                    var uuid: String? = null
                    if (playerCache.containsKey(player)) { // check if the player is in the cache
                        uuid = playerCache[player]
                    } else {
                        uuid = HttpUtils.usernameToUUID(player)
                    }
                    println(player)

                    if (uuid == null) { // nicked or fake player
                        if (GainzClient.config?.dodgeLostTo == true && playersLost.contains(player)) {
                            //beforeLeave()
                            //leaveGame()
                            //TimeUtils.setTimeout(this::joinGame, RandomUtils.randomIntInRange(4000, 6000))
                        }
                    } else {
                        playerCache[player] = uuid // cache this player
                        if (!playersSent.contains(player)) { // don't send the same player twice
                            if (mc.thePlayer != null) {
                                if (player == mc.thePlayer.displayNameString) { // if the player is the bot
                                    onJoinGame()
                                } else {
                                    val stats = HttpUtils.getPlayerStats(uuid) ?: return@thread
                                    handleStats(stats, player)
                                }
                            } else {
                                val stats = HttpUtils.getPlayerStats(uuid) ?: return@thread
                                handleStats(stats, player)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Handle the response from the hypixel api
     */
    private fun handleStats(stats: JsonObject, player: String) {
        if (toggled() && stats.get("success").asBoolean) {
            if (statKeys.containsKey("wins") && statKeys.containsKey("losses") && statKeys.containsKey("ws")) {
                fun getStat(key: String): JsonElement? {
                    var tmpObj = stats

                    for (p in key.split(".")) {
                        if (tmpObj.has(p) && tmpObj.get(p).isJsonObject)
                            tmpObj = tmpObj.get(p).asJsonObject
                        else if (tmpObj.has(p))
                            return tmpObj.get(p)
                        else
                            return null
                    }
                    return null
                }

                if (stats.get("player") == JsonNull.INSTANCE) {
                    return
                }

                if (playersQuit.contains(player)) {
                    return
                }

                if (!playersSent.contains(player)) {
                    playersSent.add(player)
                } else {
                    return
                }

                val wins = getStat(statKeys["wins"]!!)?.asInt ?: 0
                val losses = getStat(statKeys["losses"]!!)?.asInt ?: 0
                val ws = getStat(statKeys["ws"]!!)?.asInt ?: 0

                val df = DecimalFormat("#.##")
                df.roundingMode = RoundingMode.DOWN

                val wlr = wins.toDouble() / (if (losses == 0) 1.0 else losses.toDouble())


                ChatUtils.info("$player ${EnumChatFormatting.GOLD} >> ${EnumChatFormatting.GOLD}Wins: ${EnumChatFormatting.GREEN}$wins ${EnumChatFormatting.GOLD}WLR: ${EnumChatFormatting.GREEN}${df.format(wlr)} ${EnumChatFormatting.GOLD}WS: ${EnumChatFormatting.GREEN}$ws")

                if (GainzClient.config?.sendWebhookStats == true) {
                    val fields = WebHook.buildFields(arrayListOf(mapOf("name" to "Wins", "value" to "$wins", "inline" to "true"), mapOf("name" to "W/L", "value" to df.format(wlr), "inline" to "true"), mapOf("name" to "WS", "value" to "$ws", "inline" to "true")))
                    val footer = WebHook.buildFooter(ChatUtils.removeFormatting(Session.getSession()), "https://media.discordapp.net/attachments/1186644651852169248/1282117921564266593/IMG_0259.png?ex=66de30dd&is=66dcdf5d&hm=bc1c022c6bb1ebe888df5f420b439b056ba29436dd8365cf015daeab5bcab237&=&format=webp&quality=lossless&width=529&height=676")
                    val author = WebHook.buildAuthor("GainzClient - ${getName()}", "https://media.discordapp.net/attachments/1186644651852169248/1282117921564266593/IMG_0259.png?ex=66de30dd&is=66dcdf5d&hm=bc1c022c6bb1ebe888df5f420b439b056ba29436dd8365cf015daeab5bcab237&=&format=webp&quality=lossless&width=529&height=676")
                    val thumbnail = WebHook.buildThumbnail("https://media.discordapp.net/attachments/1186644651852169248/1282117921564266593/IMG_0259.png?ex=66de30dd&is=66dcdf5d&hm=bc1c022c6bb1ebe888df5f420b439b056ba29436dd8365cf015daeab5bcab237&=&format=webp&quality=lossless&width=529&height=676")

                    WebHook.sendEmbed(
                        GainzClient.config?.webhookURL!!,
                        WebHook.buildEmbed("Stats of $player:", "", fields, footer, author, thumbnail, 0x581ff2)
                    )
                }


                var dodge = false

                if (GainzClient.config?.enableDodging == true) {
                    val config = GainzClient.config
                    if (wins > config?.dodgeWins!!) {
                        dodge = true
                    } else if (wlr > config.dodgeWLR) {
                        dodge = true
                    } else if (ws > config.dodgeWS) {
                        dodge = true
                    } else if (GainzClient.config?.dodgeLostTo == true) {
                        if (playersLost.contains(player)) {
                            dodge = true
                        }
                    }
                }

                if (dodge) {
                    beforeLeave()
                    leaveGame()
                    sendDodgeWebhook(player)
                    TimeUtils.setTimeout(this::joinGame, RandomUtils.randomIntInRange(4000, 6000))
                }
            }
        } else if (toggled()) {
            ChatUtils.error("Error getting stats! Check the log for more information.")
            println("Error getting stats! success == false")
            println(GainzClient.gson.toJson(stats))
        }
    }

    private fun sendDodgeWebhook(player: String) {
        if (GainzClient.config?.sendWebhookDodge == true) {
            val footer = WebHook.buildFooter(ChatUtils.removeFormatting(Session.getSession()), "https://media.discordapp.net/attachments/1186644651852169248/1282117921564266593/IMG_0259.png?ex=66de30dd&is=66dcdf5d&hm=bc1c022c6bb1ebe888df5f420b439b056ba29436dd8365cf015daeab5bcab237&=&format=webp&quality=lossless&width=529&height=676")
            val author = WebHook.buildAuthor("GainzClient - ${getName()}", "https://media.discordapp.net/attachments/1186644651852169248/1282117921564266593/IMG_0259.png?ex=66de30dd&is=66dcdf5d&hm=bc1c022c6bb1ebe888df5f420b439b056ba29436dd8365cf015daeab5bcab237&=&format=webp&quality=lossless&width=529&height=676")
            val thumbnail = WebHook.buildThumbnail("https://media.discordapp.net/attachments/1186644651852169248/1282117921564266593/IMG_0259.png?ex=66de30dd&is=66dcdf5d&hm=bc1c022c6bb1ebe888df5f420b439b056ba29436dd8365cf015daeab5bcab237&=&format=webp&quality=lossless&width=529&height=676")

            WebHook.sendEmbed(
                GainzClient.config?.webhookURL!!,
                WebHook.buildEmbed("Dodged someone!", if (player != "") "Dodged $player." else "Dodged a nick!", JsonArray(), footer, author, thumbnail, 0x581ff2)
            )
        }
    }

    private fun leaveGame() {
        if (toggled() && StateManager.state != StateManager.States.PLAYING) {
            TimeUtils.setTimeout(fun () {
                ChatUtils.sendAsPlayer("/l")
            }, RandomUtils.randomIntInRange(100, 300))
        }
    }

    private fun joinGame(second: Boolean = false) {
        if (toggled() && StateManager.state != StateManager.States.PLAYING && !StateManager.gameFull) {
            if (StateManager.state == StateManager.States.GAME) {
                val paper = GainzClient.config?.paperRequeue == true && Inventory.setInvItem("paper")
                if (paper) {
                    TimeUtils.setTimeout(fun () {
                        Mouse.rClick(RandomUtils.randomIntInRange(30, 70))
                        TimeUtils.setTimeout(fun () {
                            Mouse.rClick(RandomUtils.randomIntInRange(30, 70))
                        }, RandomUtils.randomIntInRange(100, 300))
                    }, RandomUtils.randomIntInRange(100, 300))
                } else {
                    if (second) {
                        TimeUtils.setTimeout(fun () {
                            ChatUtils.sendAsPlayer(queueCommand)
                        }, RandomUtils.randomIntInRange(100, 300))
                    } else {
                        TimeUtils.setTimeout(fun () {
                            joinGame(true)
                        }, RandomUtils.randomIntInRange(1000, 1400))
                    }
                }
            } else {
                TimeUtils.setTimeout(fun () {
                    ChatUtils.sendAsPlayer(queueCommand)
                }, RandomUtils.randomIntInRange(100, 300))
            }
        }
    }

    private fun disconnect() {
        if (mc.theWorld != null) {
            mc.addScheduledTask(fun () {
                mc.theWorld.sendQuittingDisconnectingPacket()
                mc.loadWorld(null)
                mc.displayGuiScreen(GuiMultiplayer(GuiMainMenu()))
            })
        }
    }

    private fun reconnect() {
        if (mc.theWorld == null) {
            if (mc.currentScreen is GuiMultiplayer) {
                mc.addScheduledTask(fun () {
                    println("Reconnecting...")
                    FMLClientHandler.instance().setupServerList()
                    FMLClientHandler.instance().connectToServer(mc.currentScreen, ServerData("hypixel", "mc.hypixel.net", false))
                })
            } else {
                if (mc.theWorld == null && mc.currentScreen !is GuiConnecting) {
                    mc.addScheduledTask(fun () {
                        println("Attempting to show new multiplayer screen...")
                        mc.displayGuiScreen(GuiMultiplayer(GuiMainMenu()))
                        reconnect()
                    })
                }
            }
        }
    }

    class PacketReader(private val container: BotBase) : SimpleChannelInboundHandler<Packet<*>>(false) {

        override fun channelRead0(ctx: ChannelHandlerContext?, msg: Packet<*>?) {
            if (msg != null) {
                container.onPacket(msg)
            }
            ctx?.fireChannelRead(msg)
        }

    }

    private fun registerPacketListener() {
        val pipeline = mc.thePlayer?.sendQueue?.networkManager?.channel()?.pipeline()
        if (pipeline != null && pipeline.get("${getName()}_packet_handler") == null && pipeline.get("packet_handler") != null) {
            pipeline.addBefore(
                "packet_handler",
                "${getName()}_packet_handler",
                PacketReader(this)
            )
            println("Registered ${getName()}_packet_handler")
        }
    }

}