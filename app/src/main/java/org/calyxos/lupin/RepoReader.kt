/*
 * SPDX-FileCopyrightText: Copyright 2022 The Calyx Institute
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.fdroid.index.IndexConverter
import org.fdroid.index.IndexParser
import org.fdroid.index.parseV1
import org.fdroid.index.v1.IndexV1Verifier
import org.fdroid.index.v2.IndexV2
import java.io.File

internal const val REPO_PATH = "/product/fdroid/repo"

class RepoReader {

    suspend fun readRepo() = flow {
        emit(getIndex())
    }

    private suspend fun getIndex(): IndexV2 = withContext(Dispatchers.IO) {
        val indexFile = File(REPO_PATH, "index-v1.jar")
        val verifier = IndexV1Verifier(indexFile, CERT, null)
        val (_, index) = verifier.getStreamAndVerify { inputStream ->
            IndexParser.parseV1(inputStream)
        }
        IndexConverter().toIndexV2(index)
    }

}

private const val CERT = "30820503308202eba00302010202046d902e92300d06092a864886f70d01010b" +
        "050030323110300e060355040b1307462d44726f6964311e301c060355040313" +
        "156c6f63616c686f73742e6c6f63616c646f6d61696e301e170d313930313130" +
        "3134303235355a170d3436303532383134303235355a30323110300e06035504" +
        "0b1307462d44726f6964311e301c060355040313156c6f63616c686f73742e6c" +
        "6f63616c646f6d61696e30820222300d06092a864886f70d0101010500038202" +
        "0f003082020a028202010087fc14522eb8d57da8e05121574345edf69574973d" +
        "64585df2292c23acf81bc42c98f1cbfdf9fe7a1976bc575d28f6b606dbf3228b" +
        "110cfc7ddc823722a279cd69b0f846ae5500ecd9884556209eacbbab30159a6e" +
        "a1eaf2f64967849369a10adba65c8738b2c82b676e1367bb7b43a62dc1a5438a" +
        "7c46ae2d971eafebca606a4960e0b7b1795a2a314e25759d06a20755b36b7bf0" +
        "d9a6868c08aae63e389dd68f450ee093b02b28e830018dbcbbfd48ac757d8cda" +
        "87549f9c41836608595e9ebfa09c128acc3c7dfd1d17a67eb6a5c99281fba696" +
        "52ec27f4df3406b886e00881e6d6a4feb8fd1c5b84a5c773a631b1b2d6eab5c5" +
        "ebe503c599d40f15d3de313b0d0c96d3c63802ef4346036791b2b793b3874ca7" +
        "3a70565ba7a768c3062679aab0e289e98b9ba16a77c8747b80820618863fe202" +
        "8f02afc55914c8d6c4bcc13dd0dda61834b728875b9682ee9724589bbe216cf2" +
        "b0655f62976dbf07c91514e1c342e8e397ba4458eaa5aa98703517264ef47a09" +
        "72458fee928d67e06c34ecdbf7307a157928567d799c34f2a657cee034bd3fbb" +
        "b717387f12e70871f2dc378687b7889bb727b92d69c8a9996257b8404e93e53e" +
        "b187c807a154d95b5690eb053c249613cedad9edea857b168d41864c892c33cf" +
        "ecbb969cc6199e7215e82dc5810a1785ebdad509e0254daa2b2acda1093bda4f" +
        "b389a8a2db5f526c5b23c10203010001a321301f301d0603551d0e04160414ba" +
        "8990d4010764f81c86c769df075400c198f15e300d06092a864886f70d01010b" +
        "05000382020100489ce26311568b78e4fa07951f5fcf77322ff1f4e688594e28" +
        "21a20e3986d8c433b092f360930fed95d9ff206cee9070120e3ddceca1ce2221" +
        "c5493dc892f1df87ae3f9a45f3e3f29dd41f852daaec9cd7b9bbe754cab9c18b" +
        "0a4d0f8687915befadb27cce7ca9fd4f6061b43295568792eabd82a885ddd34c" +
        "ed64e9b3473b82069de6571f1bf8c292e5c599fbd37ce1103f8f95c0f644b091" +
        "ba227706c53a1952959a1685a410221d374924079144d8da9536a4bbab8e9af5" +
        "70468c81059a78a59d212b6a07883bc5f04adf019ab922e2ab1ee23ebb3cba0e" +
        "4e2987e81538b385fa8dd28486a05d53f128dfc9d18ab6bd1f2b7c92abf447ea" +
        "b70d3f4a73279c5fac6ec0e499cb07f4c03613836361f39cffdb75a09744b4bb" +
        "37bf8d54967d0bee745bb0f39a7397faab9a79cd7b81fc2ee089814a8c18198f" +
        "bf3d5d7a0e7823305bfc5339e4c61ccb64eee822acf9bc6a82e79fbce091ec91" +
        "daac508970ef20e8bd4b3c2aa3dc3cd5af676d0fcfa2f4339f68a52a4f81087a" +
        "2807fc3aa701bbbf80f92e8e1a3e458fe558c99d34ae94de21b211f6402606da" +
        "aa1791c3be5f94730b3fa9d3e99ae34fc5682127c58fd4eb4d5b1e8f8b2848b3" +
        "dbb0c1556d2c6043cceee5952e0a4f2b83c2b21ed472fd596d0f3b74e56d640f" +
        "65b7cf471959c1d90d46986e598b49cd799d0793d6397f8e295a908301728291" +
        "a68a14df561735"
