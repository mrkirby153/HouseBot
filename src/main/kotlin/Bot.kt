
import com.mrkirby153.botcore.command.CommandExecutor
import com.mrkirby153.botcore.shard.ShardManager
import me.mrkirby153.kcutils.mkdirIfNotExist
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.core.hooks.ListenerAdapter
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.Executors

object Bot {

    lateinit var manager: ShardManager
    lateinit var executor: CommandExecutor

    val cacheDir = File("okhttp-cache").mkdirIfNotExist()
    val client = OkHttpClient.Builder().run {
        cache(Cache(cacheDir, 10 * 1024 * 1024))
    }.build()

    var startTime = 0L

    val profileExecutor = Executors.newFixedThreadPool(5)

    var debug = false;

    @JvmStatic
    fun main(args: Array<String>) {
        Configuration.load()
        manager = ShardManager(Configuration.token)
        manager.startAllShards(false)
        val us = manager.getShard(0).selfUser
        println("Logged in as ${us.name}#${us.discriminator}!")
        executor = CommandExecutor(prefix = "!", mentionMode = CommandExecutor.MentionMode.DISABLED,
                shardManager = manager)
        executor.clearanceResolver = {
            if (it.user.id in Configuration.admins)
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

    fun debugLog(msg: String) {
        if(!debug)
            return
        println("[DEBUG] $msg")
    }

    class Listener : ListenerAdapter() {

        override fun onGuildMessageReceived(event: GuildMessageReceivedEvent) {
            Bot.profileExecutor.submit {
                executor.execute(event.message)
            }
        }
    }
}