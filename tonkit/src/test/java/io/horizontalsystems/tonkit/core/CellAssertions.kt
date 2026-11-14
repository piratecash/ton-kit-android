package io.horizontalsystems.tonkit.core

import com.tonapps.blockchain.ton.extensions.base64
import org.junit.Assert.assertEquals
import org.ton.cell.Cell

/** Compares serialized BOCs: ton-kotlin 0.5.0 `Cell.equals` reports some equal non-byte-aligned cells as different. */
internal fun assertSameCell(expected: Cell, actual: Cell?) {
    assertEquals("expected:\n$expected\nactual:\n$actual", expected.base64(), actual?.base64())
}
