import com.mrkirby153.botcore.command.Command
import com.mrkirby153.botcore.command.CommandException
import com.mrkirby153.botcore.command.Context
import com.mrkirby153.botcore.command.args.CommandContext
import me.mrkirby153.kcutils.Time
import net.dv8tion.jda.core.MessageBuilder
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.IIOException

class Commands {

    private val cooldownDelay = 30 * 1000L
    private val cooldowns = ConcurrentHashMap<String, Long>()

    private var processedGifs = 0
    private var processedStatic = 0

    private var processedBytes = 0

    @Command(name = "overlays")
    fun overlays(context: Context, cmdContext: CommandContext) {
        val overlays = buildString {
            appendln("The following overlays are available: Use `!profile <overlay>`")
            appendln("```")
            OverlayManager.getOverlays(context.guild).forEach {
                appendln(" - $it")
            }
            appendln("```")
        }
        context.channel.sendMessage(overlays).queue()
    }

    @Command(name = "profile", arguments = ["[overlay:string]"])
    fun profile(context: Context, cmdContext: CommandContext) {
        val whitelist = Configuration.channelWhitelist[context.guild.id] ?: emptyList()
        val overlay = cmdContext.get<String>("overlay") ?: "default"
        if (context.channel.id !in whitelist && whitelist.isNotEmpty())
            return
        val expiresAt = cooldowns[context.author.id] ?: 0
        if (expiresAt > System.currentTimeMillis() && context.author.id !in Configuration.admins)
            return
        val os =
                try {
                    ProfileModifier.modify(context.member, overlay) ?: throw CommandException(
                            "Could not modify your profile")
                } catch (e: IIOException) {
                    // TODO 1/6/2019 Remove this eventually™
                    if (e.message?.contains("Unexpected block type") == true) {
                        throw CommandException(
                                "Your profile picture has been previously processed by me. Due to a recently fixed bug, I cannot process your current profile picture. Please change your profile picture and try again")
                    } else {
                        e.printStackTrace()
                        throw CommandException("An unknown error occurred")
                    }
                }

        processedBytes += os.size()

        val inputStream = ByteArrayInputStream(os.toByteArray())
        val gif = context.author.effectiveAvatarUrl.endsWith("gif")
        val name = if (gif) "profile.gif" else "profile.png"

        if (gif)
            processedGifs++
        else
            processedStatic++

        Bot.debugLog("Uploading file")
        context.channel.sendFile(inputStream, name,
                MessageBuilder(
                        "${context.author.asMention} here is your picture!").build()).queue {
            Bot.debugLog("File uploaded")
            os.close()
            inputStream.close()
        }
        cooldowns[context.author.id] = System.currentTimeMillis() + cooldownDelay
    }

    @Command(name = "shutdown", clearance = 100)
    fun shutdown(context: Context, cmdContext: CommandContext) {
        context.channel.sendMessage("Shutting down...").queue {
            Bot.manager.shutdownAll()
        }
    }

    @Command(name = "reset", parent = "cooldown", clearance = 100,
            arguments = ["<user:snowflake>"])
    fun cooldownReset(context: Context, cmdContext: CommandContext) {
        val id = cmdContext.getNotNull<String>("user")
        cooldowns.remove(id)
        context.channel.sendMessage(":ok_hand: Reset the cooldown for $id").queue()
    }

    @Command(name = "stats", clearance = 100)
    fun stats(context: Context, cmdContext: CommandContext) {
        context.channel.sendMessage(buildString {
            appendln("```py")
            appendln("$processedGifs gifs processed")
            appendln("$processedStatic pngs processed")
            appendln("${processedBytes / 1000000.0}MB of data processed")
            appendln("Uptime: ${Time.formatLong(System.currentTimeMillis() - Bot.startTime)}")
            append("```")
        }).queue()
    }

    @Command(name = "whitelist", arguments = ["<channel:string>"], parent = "hb", clearance = 100)
    fun whitelist(context: Context, cmdContext: CommandContext) {
        val channel = cmdContext.getNotNull<String>("channel")
        val regex = Regex("\\d{17,18}")
        val result = regex.find(channel)?.value ?: throw CommandException(
                "Could not extract channel id")

        val textChannel = Bot.manager.shards.flatMap { it.guilds }.flatMap { it.textChannels }.firstOrNull { it.id == result }
                ?: throw CommandException("Text channel not found!")

        Configuration.addWhitelist(textChannel.guild, textChannel)
        context.channel.sendMessage(
                ":ok_hand: Channel ${textChannel.asMention} has bee whitelisted!").queue()
    }

    @Command(name = "unwhitelist", arguments = ["<channel:string>"], parent = "hb", clearance = 100)
    fun unwhitelist(context: Context, cmdContext: CommandContext) {
        val channel = cmdContext.getNotNull<String>("channel")
        val regex = Regex("\\d{17,18}")
        val result = regex.find(channel)?.value ?: throw CommandException(
                "Could not extract channel id")

        val textChannel = Bot.manager.shards.flatMap { it.guilds }.flatMap { it.textChannels }.firstOrNull { it.id == result }
                ?: throw CommandException("Text channel not found!")

        Configuration.removeWhitelist(textChannel.guild, textChannel)
        context.channel.sendMessage(
                ":ok_hand: Channel ${textChannel.asMention} has bee unwhitelisted!").queue()
    }

    @Command(name = "overlay add",
            arguments = ["<key:string>", "[guild:snowflake]", "[url:string]"], parent = "hb",
            clearance = 100)
    fun addOverlay(context: Context, cmdContext: CommandContext) {
        val url = cmdContext.get<String>("url") ?: context.attachments.firstOrNull()?.url
        ?: throw CommandException("Upload or link a file")
        val key = cmdContext.getNotNull<String>("key")
        val guildId = cmdContext.get<String>("guild") ?: context.guild.id
        val guild = Bot.manager.getGuild(guildId) ?: throw CommandException("Invalid guild")
        try {
            OverlayManager.addOverlay(guild, url, key)
        } catch (e: Exception) {
            throw CommandException(e.message ?: "An unknown error occurred")
        }
        context.channel.sendMessage(
                "Registered `$url` as an overlay on `${guild.name}` under the key `$key`").queue()
    }

    @Command(name = "overlay remove", arguments = ["<key:string>", "[guild:snowflake]"],
            parent = "hb", clearance = 100)
    fun removeOverlay(context: Context, cmdContext: CommandContext) {
        val key = cmdContext.getNotNull<String>("key")
        val guildId = cmdContext.get<String>("guild") ?: context.guild.id
        val guild = Bot.manager.getGuild(guildId) ?: throw CommandException("Invalid guild")

        OverlayManager.deleteOverlay(guild, key)
        context.channel.sendMessage("Removed `$key` as an overlay in `${guild.name}`").queue()
    }

    @Command(name = "debug", arguments = ["<state:string>"], parent = "hb", clearance = 100)
    fun toggleDebug(context: Context, cmdContext: CommandContext) {
        Bot.debug = cmdContext.getNotNull<String>("state").toBoolean()
        context.channel.sendMessage(
                "Debug set to ${cmdContext.getNotNull<String>("state")}").queue()
    }

    @Command(name = "ping", parent = "hb", clearance = 100)
    fun ping(context: Context, cmdContext: CommandContext) {
        val t = System.currentTimeMillis()
        context.channel.sendTyping().queue {
            val time = System.currentTimeMillis() - t
            context.channel.sendMessage(":ping_pong: Pong `${Time.format(1, time)}`").queue()
        }
    }
}