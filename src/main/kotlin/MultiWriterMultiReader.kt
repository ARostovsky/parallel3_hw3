import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

const val MW_REGISTERS_COUNT = 10
const val MW_THREADS_COUNT = 3


/**
 * The bounded mutli-writer multi-reader implementation
 * see [https://en.wikipedia.org/wiki/Shared_snapshot_objects]
 */
class MultiWriterMultiReader<T : Comparable<T>>(
    private val registersCount: Int = MW_REGISTERS_COUNT,
    private val threadsCount: Int = MW_THREADS_COUNT
) {
    private val threadCounter = AtomicInteger()
    private val threadId = ThreadLocal.withInitial {
        var tId: Int

        while (true) {
            tId = threadCounter.get()
            if (tId > threadsCount) throw IllegalArgumentException("Too big thread ID - $tId")

            if (threadCounter.compareAndSet(tId, tId + 1)) {
//                println("(${toString()}) $tId was set for thread ${Thread.currentThread().id}")
                break
            }
        }

        tId
    }

    private val pBitMatrix = Array(threadsCount) { Array(registersCount) { AtomicBoolean() } }
    private val qBitMatrix = Array(registersCount) { Array(threadsCount) { AtomicBoolean() } }
    private val registers = Array(registersCount) {
        AtomicReference(Register<T>())
    }
    // + 1 is needed here, because seems like lincheck creates an additional thread for something ¯\_(ツ)_/¯
    private val snapshots = Array(threadsCount + 1) {
        AtomicReference(Array<Data<T>?>(registersCount) { null })
    }

    private val registersRange = IntRange(0, registersCount - 1)
    private val threadsRange = IntRange(0, threadsCount - 1)

    /***
     * Returns a consistent view of the memory.
     */
    fun scan(id: Int): Array<Data<T>?> {
        val moved = IntArray(threadsCount)
        /* Handshake. */
        threadsRange.forEach { j ->
            qBitMatrix[id][j].getAndSet(pBitMatrix[j][id].get())
        }

        val h = BooleanArray(threadsCount)
        while (true) {
            /* (value, id, bit) triples */
            val aRegisters = registers.clone().map { it.get() }
            val bRegisters = registers.clone().map { it.get() }

            /* handshake bits */
            threadsRange.forEach { j ->
                h[j] = pBitMatrix[j][id].get()
            }

            var ak: Register<T>
            var bk: Register<T>

            if (threadsRange.all { j ->
                    qBitMatrix[id][j].get() == h[j]
                }
                && registersRange.all { k ->
                    aRegisters[k].id == bRegisters[k].id /* Nobody moved. */
                }
                && registersRange.all { k ->
                    aRegisters[k].toogle == bRegisters[k].toogle
                }
            ) return bRegisters.map { it.data }.toTypedArray()
            else threadsRange.forEach { j ->
                if (qBitMatrix[id][j].get() != h[j]
                    || registersRange
                        .filter { k -> bRegisters[k].id == j }
                        .any { k ->
                            ak = aRegisters[k]
                            bk = bRegisters[k]
                            ak.id != bk.id || ak.toogle != bk.toogle
                        }
                ) {
                    if (moved[j] == 2) {
                        return snapshots[j].get()
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
        /* Handshake. */
        threadsRange.forEach { j ->
            qBitMatrix[id][j].set(!pBitMatrix[j][id].get())
        }

        val tId = threadId.get()

        /* Embedded scan: view is a single-writer register */
        snapshots[tId].set(scan(id))

        registers[id].getAndUpdate {
            Register(
                Data(data),
                tId,
                !it.toogle /* invert the toggle bit */
            )
        }
    }

    class Data<T : Comparable<T>>(val data: T)

    inner class Register<T : Comparable<T>>(
        val data: Data<T>? = null,
        val id: Int = threadId.get(),
        val toogle: Boolean = false
    )
}
