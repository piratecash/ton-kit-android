package com.tonapps.blockchain.ton.extensions

import org.ton.bitstring.BitString
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.cell.CellSlice

fun String.toBoc(): BagOfCells {
    return try {
        BagOfCells(hex(this))

    } catch (e: Throwable) {
        BagOfCells(base64())
    }
}

fun String.parseCell(): Cell {
    return toBoc().first()
}

fun String.safeParseCell(): Cell? {
    return try {
        parseCell()
    } catch (e: Throwable) {
        null
    }
}

fun Cell.toByteArray(): ByteArray {
    return BagOfCells(this).toByteArray()
}

fun Cell.base64(): String {
    return toByteArray().encodeBase64()
}

@OptIn(ExperimentalStdlibApi::class)
fun Cell.hex(): String {
    return toByteArray().toHexString()
}

fun CellSlice.loadRemainingBits(): BitString {
    return BitString((this.bitsPosition until this.bits.size).map { this.loadBit() })
}
