import com.mrkirby153.botcore.command.Command
import com.mrkirby153.botcore.command.CommandException
import com.mrkirby153.botcore.command.Context
import com.mrkirby153.botcore.command.args.CommandContext
import me.mrkirby153.kcutils.Time
import net.dv8tion.jda.core.MessageBuilder
import java.io.ByteArrayInputStream

class Commands {

    private val cooldownDelay = 30 * 1000L
    private val cooldowns = mutableMapOf<String, Long>()

    private var processedGifs = 0
    private var processedStatic = 0

    private var processedBytes = 0

    @Command(name = "profile")
    fun profile(context: Context, cmdContext: CommandContext) {
        val whitelist = Configuration.channelWhitelist[context.guild.id]!!
        if (context.channel.id !in whitelist && whitelist.isNotEmpty())
            return
        val expiresAt = cooldowns[context.author.id] ?: 0
        if (expiresAt > System.currentTimeMillis() && context.author.id !in Configuration.admins)
            return
        val os = ProfileModifier.modify(context.member) ?: throw CommandException(
                "An error occurred, try again later")

        processedBytes += os.size()

        val inputStream = ByteArrayInputStream(os.toByteArray())
        val gif = context.author.effectiveAvatarUrl.endsWith("gif")
        val name = if (gif) "profile.gif" else "profile.png"

        if (gif)
            processedGifs++
        else
            processedStatic++

        context.channel.sendFile(inputStream, name,
                MessageBuilder("${context.author.asMention} here is your picture!").build()).queue {
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

    @Command(name = "whitelist", arguments = ["<channel:string>"], parent = "hb")
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

    @Command(name = "unwhitelist", arguments = ["<channel:string>"], parent = "hb")
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

}