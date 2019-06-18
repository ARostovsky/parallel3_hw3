import com.devexperts.dxlab.lincheck.LinChecker
import com.devexperts.dxlab.lincheck.LoggingLevel.INFO
import com.devexperts.dxlab.lincheck.annotations.OpGroupConfig
import com.devexperts.dxlab.lincheck.annotations.Operation
import com.devexperts.dxlab.lincheck.annotations.Param
import com.devexperts.dxlab.lincheck.paramgen.IntGen
import com.devexperts.dxlab.lincheck.strategy.stress.StressCTest
import com.devexperts.dxlab.lincheck.strategy.stress.StressOptions
import org.junit.Test


@Param(name = "data", gen = IntGen::class, conf = "1:${SW_REGISTERS_COUNT - 1}")
@OpGroupConfig(name = "singleWriter", nonParallel = true)
@StressCTest
class SingleWriterMultiReaderTest {
    private val singleWriterMultiReader = SingleWriterMultiReader<Int>()

    @Operation(params = ["data"])
    fun scan(id: Int): List<Int?> {
        return singleWriterMultiReader.scan(id).map { it?.data }
    }

    @Operation(params = ["data", "data"], group = "singleWriter")
    fun update(id: Int, data: Int) {
        return singleWriterMultiReader.update(id, data)
    }

    @Test
    fun test() {
        val opts = StressOptions()
            .iterations(25)
            .threads(SW_THREADS_COUNT)
            .actorsPerThread(7)
            .logLevel(INFO)

        LinChecker.check(SingleWriterMultiReaderTest::class.java, opts)
    }
}
