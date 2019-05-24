import com.devexperts.dxlab.lincheck.LinChecker
import com.devexperts.dxlab.lincheck.LoggingLevel.INFO
import com.devexperts.dxlab.lincheck.annotations.OpGroupConfig
import com.devexperts.dxlab.lincheck.annotations.Operation
import com.devexperts.dxlab.lincheck.annotations.Param
import com.devexperts.dxlab.lincheck.paramgen.IntGen
import com.devexperts.dxlab.lincheck.strategy.stress.StressCTest
import com.devexperts.dxlab.lincheck.strategy.stress.StressOptions
import org.junit.Test


@Param(name = "data", gen = IntGen::class, conf = "1:${REGISTERS_COUNT - 1}")
@OpGroupConfig(name = "singleWriter", nonParallel = true)
@StressCTest
class MultiWriterMultiReaderTest {
    private val multiWriterMultiReader = MultiWriterMultiReader<Int>()

    @Operation(params = ["data"])
    fun scan(id: Int): List<Int?> {
        return multiWriterMultiReader.scan(id).map { it?.data }
    }

    @Operation(params = ["data", "data"], group = "singleWriter")
    fun update(id: Int, data: Int) {
        return multiWriterMultiReader.update(id, data)
    }

    @Test
    fun test() {
        val opts = StressOptions()
            .iterations(5)
            .threads(2)
            .actorsPerThread(5)
            .logLevel(INFO)

        LinChecker.check(MultiWriterMultiReaderTest::class.java, opts)
    }
}
