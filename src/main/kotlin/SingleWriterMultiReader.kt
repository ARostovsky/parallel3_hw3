import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

const val SW_REGISTERS_COUNT = 10
const val SW_THREADS_COUNT = 3


/**
 * The bounded single-writer multi-reader implementation
 * see [https://en.wikipedia.org/wiki/Shared_snapshot_objects]
 */
class SingleWriterMultiReader<T : Comparable<T>>(private val registersCount: Int = SW_REGISTERS_COUNT) {
    private val handshakeBitMatrix = Array(registersCount) { Array(registersCount) { AtomicBoolean() } }
    private val registers = Array(registersCount) {
        AtomicReference(Register<T>())
    }

    private val registersRange = IntRange(0, registersCount - 1)

    /***
     * Returns a consistent view of the memory.
     */
    fun scan(id: Int): Array<Data<T>?> {
        val moved = IntArray(registersCount)

        while (true) {
            /* Handshake */
            registersRange.forEach { j ->
                handshakeBitMatrix[id][j].getAndSet(registers[j].get().handshakeBits[id])
            }

            /* (value, bit vector, bit, view) tuples */
            val aRegisters = registers.clone().map { it.get() }
            val bRegisters = registers.clone().map { it.get() }

            var a: Register<T>
            var b: Register<T>
            var q: Boolean

            if (registersRange.all { j ->
                    a = aRegisters[j]
                    b = bRegisters[j]
                    q = handshakeBitMatrix[id][j].get()

                    a.handshakeBits[id] == b.handshakeBits[id]
                            && a.handshakeBits[id] == q
                            && a.toogle == b.toogle /* Nobody moved. */
                }) return bRegisters.map { it.data }.toTypedArray()
            else registersRange.forEach { j ->
                a = aRegisters[j]
                b = bRegisters[j]
                q = handshakeBitMatrix[id][j].get()
                if (a.handshakeBits[id] != q || b.handshakeBits[id] != q /* Pj moved */
                    || a.toogle != b.toogle
                ) {
                    if (moved[j] == 1) { /* Pj moved once before! */
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
        registersRange.forEach { j ->
            newHandshakes[j] = !handshakeBitMatrix[j][id].get() /* Collect handshake values. */
        }
        val snapshot = scan(id) /* Embedded scan. */
        registers[id].getAndUpdate {
            Register(
                Data(data),
                !it.toogle,
                newHandshakes,
                snapshot
            )
        }
    }

    class Data<T : Comparable<T>>(val data: T)

    inner class Register<T : Comparable<T>>(
        val data: Data<T>? = null,
        val toogle: Boolean = false,
        val handshakeBits: BooleanArray = BooleanArray(registersCount),
        val snapshot: Array<Data<T>?> = arrayOfNulls(registersCount)
    )
}
