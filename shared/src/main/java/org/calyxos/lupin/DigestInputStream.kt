/*
 * SPDX-FileCopyrightText: 2023 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin

import java.io.FilterInputStream
import java.io.InputStream
import java.security.MessageDigest

/**
 * A [FilterInputStream] that updated the given [messageDigest] while reading from the stream.
 */
internal class DigestInputStream(
    inputStream: InputStream,
    private val messageDigest: MessageDigest,
) : FilterInputStream(inputStream) {

    override fun read(): Int {
        val b = `in`.read()
        if (b != -1) messageDigest.update(b.toByte())
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val numOfBytesRead = `in`.read(b, off, len)
        if (numOfBytesRead != -1) {
            messageDigest.update(b, off, numOfBytesRead)
        }
        return numOfBytesRead
    }

    override fun markSupported(): Boolean {
        return false
    }

    override fun mark(readlimit: Int) {
    }

    override fun reset() {
        throw NotImplementedError()
    }
}
