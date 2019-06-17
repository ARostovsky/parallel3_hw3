import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import sun.misc.Unsafe


/**
 * The bounded mutli-writer multi-reader implementation
 * see [https://en.wikipedia.org/wiki/Shared_snapshot_objects]
 */
class MultiWriterMultiReader<T : Comparable<T>>(private val registersCount: Int = REGISTERS_COUNT) {
    private val handshakeBitMatrix = Array(registersCount) { Array(registersCount) { AtomicBoolean() } }
    private val registers = Array(registersCount) {
        AtomicReference(Register<T>(registersCount))
    }
    private val registersRange = IntRange(0, registersCount - 1)

    /***
     * Returns a consistent view of the memory.
     */
    fun scan(id: Int): Array<Data<T>?> {
        val moved = IntArray(registersCount)
        /* Handshake. */
        for (j in 0 until registersCount) {
            handshakeBitMatrix[id][j].getAndSet(registers[j].get().handshakeBits[id])
        }

        val h = BooleanArray(registersCount)
        while (true) {
            /* (value, id, bit) triples */
            val aRegisters = registers.clone()
            val bRegisters = registers.clone()

            /* handshake bits */
            registersRange.forEach { j ->
                h[j] = registers[j].get().handshakeBits[id]
            }

            var a: Register<T>
            var b: Register<T>

            if (registersRange.all { j ->
                    handshakeBitMatrix[id][j].get() == h[j]
                }
                && registersRange.all { k ->
                    aRegisters[k].get().id == bRegisters[k].get().id /* Nobody moved. */
                }
                && registersRange.all { k ->
                    aRegisters[k].get().toogle == bRegisters[k].get().toogle
                }
            ) return bRegisters.map { it.get()?.data }.toTypedArray()
            else registersRange.forEach { j ->
                a = aRegisters[j].get()
                b = bRegisters[j].get()
                if (handshakeBitMatrix[id][j].get() != h[j]
                    || (registersRange.filter { k -> bRegisters[k].get().id.toInt() == j } // TODO
                        .any { k -> bRegisters[k].get().id != aRegisters[k].get().id }
                            || a.toogle != b.toogle)
                ) {
                    if (moved[j] == 2) {
                        return b.snapshot
                    }
                    moved[j].inc()
                }
            }

        }
    }

    /** Updates the registers with the data-value, "write-state" of all registers,
     * invert the toggle bit and the embedded scan
     */
    fun update(id: Int, data: T) {
        val newHandshakes = BooleanArray(registersCount)
        /* Handshake. */
        registersRange.forEach { i ->
            newHandshakes[i] = !handshakeBitMatrix[i][id].get()
        }

        val snapshot = scan(id)  /* Embedded scan: view is a single-writer register */
        val obj = Data(data)
        val newRegister = Register(
            registersCount,
            obj,
            getAddress(obj),
            false,
            newHandshakes,
            snapshot
        )
        registers[id].getAndUpdate { newRegister }
    }

    class Data<T : Comparable<T>>(val data: T)

    class Register<T : Comparable<T>>(
        registersCount: Int,
        val data: Data<T>? = null,
        val id: Long = 0,
        val toogle: Boolean = false,
        val handshakeBits: BooleanArray = BooleanArray(registersCount),
        val snapshot: Array<Data<T>?> = arrayOfNulls(registersCount)
        )

    companion object {
        private fun getUnsafe(): Unsafe {
            try {
                val theUnsafe = Unsafe::class.java.getDeclaredField("theUnsafe")
                theUnsafe.isAccessible = true
                return theUnsafe.get(null) as Unsafe
            } catch (e: Exception) {
                throw AssertionError(e)
            }
        }

        fun getAddress(obj: Any): Long {
            val unsafe = getUnsafe()
            val array = arrayOf(obj)
            val baseOffset = unsafe.arrayBaseOffset(Array<Any>::class.java)

            return when (val addressSize = unsafe.addressSize()) {
                4 -> unsafe.getInt(array, baseOffset).toLong()
                8 -> unsafe.getLong(array, baseOffset)
                else -> throw Error("unsupported address size: $addressSize")
            }
        }
    }
}

fun main() {
    val a = MultiWriterMultiReader<Int>()
    MultiWriterMultiReader.getAddress(a).also { println(it) }
}