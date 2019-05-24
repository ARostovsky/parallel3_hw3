import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

const val REGISTERS_COUNT = 50


/**
 * The bounded single-writer multi-reader implementation
 * see [https://en.wikipedia.org/wiki/Shared_snapshot_objects]
 */
public class SingleWriterMultiReader<T : Comparable<T>>(private val registersCount: Int = REGISTERS_COUNT) {
    private val handshakeBitMatrix = Array(registersCount) { Array(registersCount) { AtomicBoolean() } }
    private val registers = Array(registersCount) {
        AtomicReference(Register<T>(registersCount))
    }

    /***
     * Returns a consistent view of the memory.
     */
    fun scan(id: Int): Array<Data<T>?> {
        val moved = IntArray(registersCount);

        while (true) {
            /* Handshake */
            for (j in 0 until registersCount) {
                handshakeBitMatrix[id][j].getAndSet(registers[j].get().handshakeBits[id])
            }

            /* (value, bit vector, bit, view) tuples */
            val aRegisters = registers.clone()
            val bRegisters = registers.clone()

            var a: Register<T>
            var b: Register<T>
            var q: Boolean

            if (IntRange(0, registersCount - 1).all { j ->
                    a = aRegisters[j].get()
                    b = bRegisters[j].get()
                    q = handshakeBitMatrix[id][j].get()

                    a.handshakeBits[id] == b.handshakeBits[id] && a.handshakeBits[id] == q
                            && a.toogle == b.toogle /* Nobody moved. */
                }) return bRegisters.map { it.get()?.data }.toTypedArray()
            else for (j in 0 until registersCount) {
                a = aRegisters[j].get()
                b = bRegisters[j].get()
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
        val newHandshakes = BooleanArray(registersCount);
        for (i in 0 until registersCount) {
            newHandshakes[i] = !handshakeBitMatrix[i][id].get() /* Collect handshake values. */
        }
        val snapshot = scan(id) /* Embedded scan. */

        val newRegister = Register(
            registersCount,
            Data(data),
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
        val toogle: Boolean = false,
        val handshakeBits: BooleanArray = BooleanArray(registersCount),
        val snapshot: Array<Data<T>?> = arrayOfNulls<Data<T>>(registersCount)
    )
}
