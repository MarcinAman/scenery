package graphics.scenery.textures

import cleargl.GLTypeEnum
import graphics.scenery.utils.Image
import graphics.scenery.utils.Timestamped
import net.imglib2.type.numeric.NumericType
import net.imglib2.type.numeric.integer.*
import net.imglib2.type.numeric.real.DoubleType
import net.imglib2.type.numeric.real.FloatType
import org.joml.Vector3i
import java.io.Serializable
import java.nio.ByteBuffer
import java.nio.ByteOrder


/**
 * Data class for storing renderer-agnostic textures
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class Texture @JvmOverloads constructor(
    /** Dimensions of the texture in pixels */
    var dimensions: Vector3i,
    /** The texture's number of channels */
    var channels: Int = 4,
    /** [GLTypeEnum] declaring the data type stored in [contents] */
    var type: NumericType<*> = UnsignedByteType(),
    /** Byte contents of the texture */
    @Transient var contents: ByteBuffer?,
    /** Shall the texture be repeated on the U/V/W coordinates? */
    var repeatUVW: Triple<RepeatMode, RepeatMode, RepeatMode> = Triple(RepeatMode.Repeat, RepeatMode.Repeat, RepeatMode.Repeat),
    /** Texture border color */
    var borderColor: BorderColor = BorderColor.TransparentBlack,
    /** Should the texture data be interpreted as normalized? Default is true, non-normalisation is better for volume data, though */
    var normalized: Boolean = true,
    /** Should mipmaps be generated? */
    var mipmap: Boolean = true,
    /** Linear or nearest neighbor filtering for scaling down. */
    var minFilter: FilteringMode = FilteringMode.Linear,
    /** Linear or nearest neighbor filtering for scaling up. */
    var maxFilter: FilteringMode = FilteringMode.Linear,
    /** Usage type */
    val usageType: HashSet<UsageType> = hashSetOf(UsageType.Texture)


) : Serializable, Timestamped {
    init {
        contents?.let { c ->
            val buffer = c.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            val bytesPerPixel = when (type) {
                is UnsignedByteType -> 1
                is UnsignedShortType -> 2
                is UnsignedIntType -> 4

                is ByteType -> 1
                is ShortType -> 2
                is IntType -> 4

                is FloatType -> 4
                is DoubleType -> 8

                else -> throw UnsupportedOperationException("Don't know how to handle textures of type ${type.javaClass.simpleName}")
            }

            val remaining = buffer.remaining()
            val expected = bytesPerPixel * dimensions.x * dimensions.y * dimensions.z * channels

            if (remaining != expected) {
                throw IllegalStateException("Buffer for texture does not contain correct number of bytes. Actual: $remaining, expected: $expected for image of size $dimensions and $channels channels of type ${type.javaClass.simpleName}.")
            }
        }
    }

    /**
     * Enum class defining available texture repeat modes.
     */
    enum class RepeatMode {
        Repeat,
        MirroredRepeat,
        ClampToEdge,
        ClampToBorder;

        fun all():  Triple<RepeatMode, RepeatMode, RepeatMode> {
            return Triple(this, this, this)
        }
    }

    /**
     * Enum class defining which colors are available for a texture's border.
     */
    enum class BorderColor {
        TransparentBlack,
        OpaqueBlack,
        OpaqueWhite
    }

    /**
     * Enum class defining texture filtering modes
     */
    enum class FilteringMode {
        NearestNeighbour,
        Linear
    }

    enum class UsageType {
        Texture,
        LoadStoreImage
    }

    /** Companion object of [Texture], containing mainly constant defines */
    companion object {
        /** The textures to be contained in the ObjectTextures texture array */
        val objectTextures = listOf("ambient", "diffuse", "specular", "normal", "alphamask", "displacement")
        /** The ObjectTextures that should be mipmapped */
        val mipmappedObjectTextures = listOf("ambient", "diffuse", "specular")

        @JvmStatic @JvmOverloads fun fromImage(
            image: Image,
            repeatUVW: Triple<RepeatMode, RepeatMode, RepeatMode> = RepeatMode.Repeat.all(),
            borderColor: BorderColor = BorderColor.OpaqueBlack,
            normalized: Boolean = true,
            mipmap: Boolean = true,
            minFilter: FilteringMode = FilteringMode.Linear,
            maxFilter: FilteringMode = FilteringMode.Linear,
            usage: HashSet<UsageType> = hashSetOf(UsageType.Texture)
        ): Texture {
            return Texture(Vector3i(image.width, image.height, image.depth),
                4, UnsignedByteType(), image.contents, repeatUVW, borderColor, normalized, mipmap, usageType = usage, minFilter = minFilter, maxFilter = maxFilter)
        }
    }

    /** When the object was created. */
    override var created: Long = System.nanoTime()

    /** When the object was last modified. */
    override var updated: Long = System.nanoTime()
}
