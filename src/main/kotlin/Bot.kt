import com.mrkirby153.botcore.command.ClearanceResolver
import com.mrkirby153.botcore.command.CommandExecutor
import me.mrkirby153.kcutils.mkdirIfNotExist
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder
import net.dv8tion.jda.api.sharding.ShardManager
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
        manager = DefaultShardManagerBuilder.createLight(Configuration.token).build()
        // Wait for all the shards to start up
        manager.shards.forEach { it.awaitReady() }
        val us = manager.shards[0].selfUser
        println("Logged in as ${us.name}#${us.discriminator}!")
        executor = CommandExecutor(prefix = "!", mentionMode = CommandExecutor.MentionMode.DISABLED,
                shardManager = manager)
        executor.clearanceResolver = object : ClearanceResolver {
            override fun resolve(member: Member): Int {
                return if (member.user.id in Configuration.admins)
                    100
                else
                    0
            }

        }
        executor.alertUnknownCommand = false
        executor.alertNoClearance = false
        executor.register(Commands::class.java)
        manager.addEventListener(Listener())
        println("Ready to go!")
        startTime = System.currentTimeMillis()
    }

    fun debugLog(msg: String) {
        if (!debug)
            return
        println("[DEBUG] $msg")
    }

    class Listener : ListenerAdapter() {

        override fun onGuildMessageReceived(event: GuildMessageReceivedEvent) {
            profileExecutor.submit {
                executor.execute(event.message)
            }
        }
    }
}