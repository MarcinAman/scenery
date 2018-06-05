package graphics.scenery.backends.vulkan

import graphics.scenery.Node
import graphics.scenery.backends.UBO
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.VK10.*
import java.nio.ByteBuffer
import java.nio.IntBuffer

/**
 * UBO class for Vulkan, providing specific functionality, such as buffer making and UBO buffer creation.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class VulkanUBO(val device: VulkanDevice, var backingBuffer: VulkanBuffer? = null): AutoCloseable, UBO() {
    var descriptor = UBODescriptor()
        private set
    var offsets: IntBuffer = memAllocInt(1).put(0, 0)
    var requiredOffsetCount = 0
    private var closed = false

    private var ownedBackingBuffer: VulkanBuffer? = null

    class UBODescriptor {
        internal var memory: Long = 0
        internal var allocationSize: Long = 0
        internal var buffer: Long = 0
        internal var offset: Long = 0
        internal var range: Long = 0
    }

    fun copy(data: ByteBuffer, offset: Long = 0) {
        val dest = memAllocPointer(1)

        VU.run("Mapping buffer memory/vkMapMemory", { vkMapMemory(device.vulkanDevice, descriptor.memory, offset, descriptor.allocationSize* 1L, 0, dest) })
        memCopy(memAddress(data), dest.get(0), data.remaining().toLong())

        vkUnmapMemory(device.vulkanDevice, descriptor.memory)
        memFree(dest)
    }

    fun populate(offset: Long = 0L) {
        if(backingBuffer == null) {
            val data = memAlloc(getSize())

            super.populate(data, offset, elements = null)

            data.flip()
            copy(data, offset = offset)
            memFree(data)
        } else {
            super.populate(backingBuffer!!.stagingBuffer, offset, elements = null)
        }
    }

    fun populateParallel(bufferView: ByteBuffer, offset: Long, elements: LinkedHashMap<String, () -> Any>) {
        bufferView.position(0)
        bufferView.limit(bufferView.capacity())
        super.populate(bufferView, offset, elements)
    }

    fun fromInstance(node: Node) {
        node.instancedProperties.forEach { members.putIfAbsent(it.key, it.value) }
    }

    fun createUniformBuffer(): UBODescriptor {
        if(backingBuffer == null) {
            ownedBackingBuffer = VulkanBuffer(device,
                this.getSize() * 1L,
                VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
                wantAligned = true)

            ownedBackingBuffer?.let { buffer ->
                descriptor = UBODescriptor()
                descriptor.memory = buffer.memory
                descriptor.allocationSize = buffer.size
                descriptor.buffer = buffer.vulkanBuffer
                descriptor.offset = 0L
                descriptor.range = this.getSize() * 1L
            }
        } else {
            descriptor.memory = backingBuffer!!.memory
            descriptor.allocationSize = backingBuffer!!.size
            descriptor.buffer = backingBuffer!!.vulkanBuffer
            descriptor.offset = 0L
            descriptor.range = this.getSize() * 1L
        }

        return this.descriptor
    }

    @Suppress("unused")
    fun updateBackingBuffer(newBackingBuffer: VulkanBuffer) {
        descriptor.memory = newBackingBuffer.memory
        descriptor.allocationSize = newBackingBuffer.size
        descriptor.buffer = newBackingBuffer.vulkanBuffer
        descriptor.offset = 0L
        descriptor.range = this.getSize() * 1L

        backingBuffer = newBackingBuffer
    }

    @Suppress("unused")
    fun copyFromStagingBuffer() {
        backingBuffer?.copyFromStagingBuffer()
    }

    override fun close() {
        if(closed) {
            return
        }

        logger.trace("Closing UBO $this ...")
        if(backingBuffer == null) {
            ownedBackingBuffer?.let {
                logger.trace("Destroying self-owned buffer of $this/$it  ${it.memory.toHexString()})...")
                it.close()
            }
        }

        memFree(offsets)
        closed = true
    }
}
