import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.TextChannel
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File

object Configuration {

    val token
        get() = this.config.getString("token")

    val admins
        get() = this.config.getJSONArray("admins").map { it as String }

    val channelWhitelist: Map<String, List<String>>
        get() {
            val map = mutableMapOf<String, List<String>>()
            val obj = this.config.getJSONObject("guilds")
            obj.keys().forEach { guildId ->
                map[guildId] = obj.getJSONObject(guildId).getJSONArray(
                        "channels").map { it as String }
            }
            return map.toMap()
        }

    private var config = JSONObject()

    /**
     * Loads the config
     */
    fun load() {
        val file = File("config.json")
        file.inputStream().use {
            this.config = JSONObject(JSONTokener(it))
        }
    }

    /**
     * Saves the configuration file
     */
    fun save() {
        val file = File("config.json")
        file.writeText(this.config.toString(4))
    }

    /**
     * Adds a channel to the whitelist
     *
     * @param guild The guild
     * @param textChannel The text channel
     */
    fun addWhitelist(guild: Guild, textChannel: TextChannel) {
        val guildObj = this.config.optJSONObject("guilds")
                ?: JSONObject().apply { this@Configuration.config.put("guilds", this) }
        val guildSettings = guildObj.optJSONObject(guild.id) ?: JSONObject().apply {
            guildObj.put(guild.id, this)
        }
        val arr = guildSettings.optJSONArray("channels")
                ?: JSONArray().apply { guildSettings.put("channels", this) }
        arr.put(textChannel.id)
        save()
    }

    /**
     * Removes a channel from the whitelist
     *
     * @param guild The guild
     * @param textChannel The text channel
     */
    fun removeWhitelist(guild: Guild, textChannel: TextChannel) {
        val guildObj = this.config.optJSONObject("guilds") ?: return
        val guildSettings = guildObj.optJSONObject(guild.id) ?: return
        val arr = guildSettings.optJSONArray("channels") ?: return
        val it = arr.iterator()
        while (it.hasNext()) {
            if (it.next() as String == textChannel.id)
                it.remove()
        }
        save()
    }
}