import com.mrkirby153.botcore.command.CommandException
import me.mrkirby153.kcutils.mkdirIfNotExist
import net.dv8tion.jda.core.entities.Guild
import net.dv8tion.jda.core.entities.Member
import okhttp3.Request
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.ArrayList
import javax.imageio.ImageIO
import javax.imageio.metadata.IIOMetadata
import javax.imageio.metadata.IIOMetadataNode


object ProfileModifier {

    private val overlayDir = File("overlays").mkdirIfNotExist()

    val overlayCache = mutableMapOf<String, BufferedImage>()

    fun modify(member: Member, key: String): ByteArrayOutputStream? {
        Bot.debugLog("Modifying pfp for $member")
        val profileUrl = member.user.effectiveAvatarUrl
        if (profileUrl.endsWith("gif")) {
            Bot.debugLog("Picture is a gif, processing gif")
            return modifyGif(member, key)
        }
        Bot.debugLog("Making request to $profileUrl")
        val profileReq = Request.Builder().url(profileUrl).build()
        val profileResp = Bot.client.newCall(profileReq).execute()
        if (profileResp.code() != 200)
            throw CommandException("There was an error retrieving your profile")

        if (profileResp.body()!!.contentLength() > 8e6) {
            Bot.debugLog("Picture is too large")
            throw CommandException(
                    "Your profile picture is too large to process. Please choose a smaller one.")
        }

        Bot.debugLog("Decoding profile picture")
        val profile = ImageIO.read(profileResp.body()!!.byteStream())
        val overlay = overlayCache.computeIfAbsent("${member.guild.id}-$key") {
            getOverlay(member.guild, key)
        }
        Bot.debugLog("Decoded")

        Bot.debugLog("Modifying image")
        val newImage = BufferedImage(profile.width, profile.height, BufferedImage.TYPE_INT_ARGB)
        val g = newImage.graphics
        g.drawImage(profile, 0, 0, null)
        g.drawImage(resizeImage(overlay, profile.width, profile.height), 0, 0, null)
        g.dispose()

        profileResp.close()
        val os = ByteArrayOutputStream()
        ImageIO.write(newImage, "png", os)
        Bot.debugLog("Complete")
        return os
    }

    fun modifyGif(member: Member, key: String): ByteArrayOutputStream? {
        Bot.debugLog("Processing gif")
        val profileUrl = member.user.effectiveAvatarUrl
        Bot.debugLog("Making request to $profileUrl")
        val profileReq = Request.Builder().url(profileUrl).build()
        val profileResp = Bot.client.newCall(profileReq).execute()
        if (profileResp.code() != 200)
            throw CommandException("There was an error retrieving your profile")

        if (profileResp.body()!!.contentLength() > 8e6) {
            Bot.debugLog("Profile is too large")
            throw CommandException(
                    "Your profile picture is too large to process. Please choose a smaller one.")
        }

        Bot.debugLog("Decoding gif")
        val pair = readGif(profileResp.body()!!.byteStream())
        val frames = pair.first
        val metadata = pair.second

        val bos = ByteArrayOutputStream()
        val ios = ImageIO.createImageOutputStream(bos)
        val writer = GifSequenceWriter(ios, frames[0].image.type, frames[0].delay * 10, true,
                frames[0].disposal)

        val overlay = overlayCache.computeIfAbsent("${member.guild.id}-$key") {
            getOverlay(member.guild, key)
        }

        Bot.debugLog("Modifying individual frames (${frames.size})")
        var fi = 0
        frames.forEach {
            val newImage = BufferedImage(it.image.width, it.image.height,
                    BufferedImage.TYPE_INT_ARGB)
            val graphics = newImage.graphics
            graphics.drawImage(it.image, 0, 0, null)
            graphics.drawImage(resizeImage(overlay, it.image.width, it.image.height), 0, 0, null)
            graphics.dispose()
            writer.writeToSequence(newImage, it.delay, it.disposal)
            Bot.debugLog("Modified frame ${++fi}/${frames.size + 1}")
        }
        writer.close()
        profileResp.close()

        ios.seek(0)
        val buff = ByteArray(255)
        while (true) {
            if (ios.read(buff) == -1)
                break
            bos.write(buff)
        }
        Bot.debugLog("Finished")
        return bos
    }

    private fun getOverlay(guild: Guild, key: String): BufferedImage {
        val file = OverlayManager.getOverlay(guild, key) ?: throw CommandException(
                "Overlay not found! Use `!overlays` for a list of overlays")
        return ImageIO.read(file)
    }

    private fun resizeImage(img: BufferedImage, width: Int, height: Int): BufferedImage {
        val tmp = img.getScaledInstance(width, height, Image.SCALE_SMOOTH)
        val dImg = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

        val g2d = dImg.createGraphics()
        g2d.drawImage(tmp, 0, 0, null)
        g2d.dispose()
        return dImg
    }

    private fun readGif(stream: InputStream): Pair<Array<ImageFrame>, IIOMetadata> {
        val reader = ImageIO.getImageReadersByFormatName("gif").next()
        reader.input = ImageIO.createImageInputStream(stream)

        val frames = ArrayList<ImageFrame>(2)

        var width = -1
        var height = -1

        val metadata = reader.streamMetadata
        if (metadata != null) {
            val globalRoot = metadata.getAsTree(
                    metadata.nativeMetadataFormatName) as IIOMetadataNode

            val globalScreenDescriptor = globalRoot.getElementsByTagName("LogicalScreenDescriptor")

            if (globalScreenDescriptor != null && globalScreenDescriptor.length > 0) {
                val screenDescriptor = globalScreenDescriptor.item(0) as IIOMetadataNode?

                if (screenDescriptor != null) {
                    width = Integer.parseInt(screenDescriptor.getAttribute("logicalScreenWidth"))
                    height = Integer.parseInt(screenDescriptor.getAttribute("logicalScreenHeight"))
                }
            }
        }

        var master: BufferedImage? = null
        var masterGraphics: Graphics2D? = null

        var frameIndex = 0
        while (true) {
            Bot.debugLog("Processing frame $frameIndex")
            val image: BufferedImage
            try {
                image = reader.read(frameIndex)
            } catch (io: IndexOutOfBoundsException) {
                break
            }

            if (width == -1 || height == -1) {
                width = image.width
                height = image.height
            }

            val root = reader.getImageMetadata(frameIndex).getAsTree(
                    "javax_imageio_gif_image_1.0") as IIOMetadataNode
            val gce = root.getElementsByTagName("GraphicControlExtension").item(
                    0) as IIOMetadataNode
            val delay = Integer.valueOf(gce.getAttribute("delayTime"))
            val disposal = gce.getAttribute("disposalMethod")

            var x = 0
            var y = 0

            if (master == null) {
                master = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                masterGraphics = master.createGraphics()
                masterGraphics!!.background = Color(0, 0, 0, 0)
            } else {
                val children = root.childNodes
                for (nodeIndex in 0 until children.length) {
                    val nodeItem = children.item(nodeIndex)
                    if (nodeItem.nodeName == "ImageDescriptor") {
                        val map = nodeItem.attributes
                        x = Integer.valueOf(map.getNamedItem("imageLeftPosition").nodeValue)
                        y = Integer.valueOf(map.getNamedItem("imageTopPosition").nodeValue)
                    }
                }
            }
            masterGraphics!!.drawImage(image, x, y, null)

            val copy = BufferedImage(master.colorModel, master.copyData(null),
                    master.isAlphaPremultiplied, null)
            frames.add(ImageFrame(copy, delay, disposal))

            if (disposal == "restoreToPrevious") {
                var from: BufferedImage? = null
                for (i in frameIndex - 1 downTo 0) {
                    if (frames[i].disposal != "restoreToPrevious" || frameIndex == 0) {
                        from = frames[i].image
                        break
                    }
                }

                if (from == null) {
                    from = BufferedImage(master.colorModel, master.copyData(null),
                            master.isAlphaPremultiplied, null)
                }

                master = BufferedImage(from.colorModel, from.copyData(null),
                        from.isAlphaPremultiplied, null)
                masterGraphics = master.createGraphics()
                masterGraphics!!.background = Color(0, 0, 0, 0)
            } else if (disposal == "restoreToBackgroundColor") {
                masterGraphics.clearRect(x, y, image.width, image.height)
            }
            frameIndex++
        }
        reader.dispose()

        return Pair(frames.toTypedArray(), metadata)
    }

    data class ImageFrame(val image: BufferedImage, val delay: Int, val disposal: String)
}