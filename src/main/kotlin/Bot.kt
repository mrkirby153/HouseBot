
import com.mrkirby153.botcore.command.CommandExecutor
import com.mrkirby153.botcore.shard.ShardManager
import me.mrkirby153.kcutils.mkdirIfNotExist
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.core.hooks.ListenerAdapter
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File

object Bot {

    lateinit var manager: ShardManager
    lateinit var executor: CommandExecutor

    val cacheDir = File("okhttp-cache").mkdirIfNotExist()
    val client = OkHttpClient.Builder().run {
        cache(Cache(cacheDir, 10 * 1024 * 1024))
    }.build()

    private val token = File("token").readLines().first()

    private val admins = File("admins").readLines()
    val channels = File("channels").readLines()

    var startTime = 0L

    @JvmStatic
    fun main(args: Array<String>) {
        manager = ShardManager(token)
        manager.startAllShards(false)
        val us = manager.getShard(0).selfUser
        println("Logged in as ${us.name}#${us.discriminator}!")
        executor = CommandExecutor(prefix = "!", mentionMode = CommandExecutor.MentionMode.DISABLED,
                shardManager = manager)
        executor.clearanceResolver = {
            if (it.user.id in admins)
                100
            else
                0
        }
        executor.alertUnknownCommand = false
        executor.alertNoClearance = false
        executor.register(Commands::class.java)
        manager.addListener(Listener())
        println("Ready to go!")
        startTime = System.currentTimeMillis()
    }

    class Listener : ListenerAdapter() {

        override fun onGuildMessageReceived(event: GuildMessageReceivedEvent) {
            executor.execute(event.message)
        }
    }
}