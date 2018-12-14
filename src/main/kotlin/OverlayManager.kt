
import me.mrkirby153.kcutils.child
import me.mrkirby153.kcutils.createFileIfNotExist
import me.mrkirby153.kcutils.utils.IdGenerator
import net.dv8tion.jda.core.entities.Guild
import okhttp3.Request
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File

object OverlayManager {

    val basePath = File("overlays")

    private val idGen = IdGenerator(IdGenerator.ALPHA)

    /**
     * Gets all available overlays on the guild
     *
     * @param guild The guild
     * @return A list of overlays
     */
    fun getOverlays(guild: Guild): List<String> {
        val jsonFiles = basePath.child(guild.id).child("overlays.json")
        jsonFiles.inputStream().use {
            val json = JSONObject(JSONTokener(jsonFiles.inputStream()))
            return json.keySet().toList()
        }
    }

    /**
     * Gets an overlay
     *
     * @param guild The guild
     * @param name The name of the overlay
     * @return The file for the overlay or null if it doesn't exist
     */
    fun getOverlay(guild: Guild, name: String): File? {
        val guildFolder = basePath.child(guild.id)
        val json = guildFolder.child("overlays.json")
        val obj = JSONObject(JSONTokener(json.inputStream()))
        val overlay = obj.optString(name.toLowerCase(), null) ?: return null
        val child = guildFolder.child(overlay)
        return if (child.exists())
            child
        else
            null
    }

    /**
     * Adds an overlay from the given URL
     *
     * @param guild The guild
     * @param url The URL of the overlay
     * @param name The name of the overlay
     */
    fun addOverlay(guild: Guild, url: String, name: String) {
        val request = Request.Builder().url(url).build()
        val resp = Bot.client.newCall(request).execute()
        if (resp.code() != 200)
            throw IllegalStateException("Received unexpected error code ${resp.code()}")

        val body = resp.body()?.bytes() ?: throw IllegalStateException(
                "Response body was null")

        val folder = basePath.child(guild.id).apply { mkdirs() }
        val indexFile = folder.child("overlays.json").apply {
            if(!this.exists()) {
                this.createNewFile()
                this.writeText("{}")
            }
        }

        val outputFile = folder.child(idGen.generate(5) + ".png")
        outputFile.createNewFile()

        outputFile.writeBytes(body)

        val json = JSONObject(JSONTokener(indexFile.inputStream()))
        json.put(name, outputFile.name)
        indexFile.writeText(json.toString(4))
    }

    /**
     * Deletes an overlay
     *
     * @param guild The guild
     * @param name The name of the overlay to delete
     */
    fun deleteOverlay(guild: Guild, name: String) {
        val folder = basePath.child(guild.id).apply { mkdirs() }
        val indexFile = folder.child("overlays.json").createFileIfNotExist()
        val json = JSONObject(JSONTokener(indexFile.inputStream()))
        val fileName = json.remove(name) as? String ?: return

        folder.child(fileName).delete()
        indexFile.writeText(json.toString(4))
        ProfileModifier.overlayCache.remove("${guild.id}-$name")
    }
}