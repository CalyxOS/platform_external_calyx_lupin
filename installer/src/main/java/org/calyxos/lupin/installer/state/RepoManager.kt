/*
 * SPDX-FileCopyrightText: 2022 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

package org.calyxos.lupin.installer.state

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.calyxos.lupin.RepoHelper.downloadIndex
import org.calyxos.lupin.RepoHelper.getIndex
import org.calyxos.lupin.getRequest
import org.calyxos.lupin.installer.BuildConfig.VERSION_NAME
import org.calyxos.lupin.installer.R
import org.fdroid.download.HttpDownloader
import org.fdroid.download.HttpManager
import org.fdroid.index.v2.FileV2
import org.fdroid.index.v2.IndexV2
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

internal const val REPO_INDEX = "index-v1.jar"
internal const val REPO_PATH = "/product/fdroid/repo"
internal const val REPO_URL = "https://fdroid-repo.calyxinstitute.org/non/existent/repo"
internal const val CATEGORY_ALWAYS_INSTALL = "AlwaysInstall"
internal const val CATEGORY_DEFAULT = "Default"
internal const val CATEGORY_HIDDEN = "Hidden"
internal const val CATEGORY_ONLINE_ONLY = "OnlineOnly"

data class RepoResult(
    val index: IndexV2,
    val iconGetter: (String?) -> Any?,
    val apkGetter: suspend (String, DownloadListener) -> File,
)

@Singleton
class RepoManager @Inject constructor(@ApplicationContext private val context: Context) {

    private val httpManager = HttpManager("${context.getString(R.string.app_name)} $VERSION_NAME")

    suspend fun getLocalIndex(): RepoResult = withContext(Dispatchers.IO) {
        RepoResult(
            index = getIndex(File(REPO_PATH, REPO_INDEX), CERT),
            iconGetter = { icon -> if (icon == null) null else "$REPO_PATH/$icon" },
            apkGetter = { apk, _ -> File(REPO_PATH, apk) },
        )
    }

    suspend fun getOnlineIndex(): RepoResult = withContext(Dispatchers.IO) {
        val index = downloadIndex(context, REPO_URL, CERT_ONLINE, httpManager)
        RepoResult(
            index = index,
            iconGetter = { icon -> if (icon == null) null else "$REPO_URL/$icon" },
        ) { apk, downloadListener ->
            val apkRequest = FileV2(apk).getRequest(REPO_URL)
            val apkFile = File.createTempFile("dl-", "", context.cacheDir)
            HttpDownloader(httpManager, apkRequest, apkFile).apply {
                val coContext = currentCoroutineContext()
                setListener { bytesRead, _ ->
                    // this is a bit of a hack to work around the messy progress reporting
                    launch(coContext) {
                        downloadListener.bytesRead(bytesRead)
                    }
                }
                download()
            }
            apkFile
        }
    }
}

private const val CERT =
    "30820503308202eba00302010202046d902e92300d06092a864886f70d01010b050030323110300e06035504" +
        "0b1307462d44726f6964311e301c060355040313156c6f63616c686f73742e6c6f63616c646f6d61696e301e" +
        "170d3139303131303134303235355a170d3436303532383134303235355a30323110300e060355040b130746" +
        "2d44726f6964311e301c060355040313156c6f63616c686f73742e6c6f63616c646f6d61696e30820222300d" +
        "06092a864886f70d01010105000382020f003082020a028202010087fc14522eb8d57da8e05121574345edf6" +
        "9574973d64585df2292c23acf81bc42c98f1cbfdf9fe7a1976bc575d28f6b606dbf3228b110cfc7ddc823722" +
        "a279cd69b0f846ae5500ecd9884556209eacbbab30159a6ea1eaf2f64967849369a10adba65c8738b2c82b67" +
        "6e1367bb7b43a62dc1a5438a7c46ae2d971eafebca606a4960e0b7b1795a2a314e25759d06a20755b36b7bf0" +
        "d9a6868c08aae63e389dd68f450ee093b02b28e830018dbcbbfd48ac757d8cda87549f9c41836608595e9ebf" +
        "a09c128acc3c7dfd1d17a67eb6a5c99281fba69652ec27f4df3406b886e00881e6d6a4feb8fd1c5b84a5c773" +
        "a631b1b2d6eab5c5ebe503c599d40f15d3de313b0d0c96d3c63802ef4346036791b2b793b3874ca73a70565b" +
        "a7a768c3062679aab0e289e98b9ba16a77c8747b80820618863fe2028f02afc55914c8d6c4bcc13dd0dda618" +
        "34b728875b9682ee9724589bbe216cf2b0655f62976dbf07c91514e1c342e8e397ba4458eaa5aa9870351726" +
        "4ef47a0972458fee928d67e06c34ecdbf7307a157928567d799c34f2a657cee034bd3fbbb717387f12e70871" +
        "f2dc378687b7889bb727b92d69c8a9996257b8404e93e53eb187c807a154d95b5690eb053c249613cedad9ed" +
        "ea857b168d41864c892c33cfecbb969cc6199e7215e82dc5810a1785ebdad509e0254daa2b2acda1093bda4f" +
        "b389a8a2db5f526c5b23c10203010001a321301f301d0603551d0e04160414ba8990d4010764f81c86c769df" +
        "075400c198f15e300d06092a864886f70d01010b05000382020100489ce26311568b78e4fa07951f5fcf7732" +
        "2ff1f4e688594e2821a20e3986d8c433b092f360930fed95d9ff206cee9070120e3ddceca1ce2221c5493dc8" +
        "92f1df87ae3f9a45f3e3f29dd41f852daaec9cd7b9bbe754cab9c18b0a4d0f8687915befadb27cce7ca9fd4f" +
        "6061b43295568792eabd82a885ddd34ced64e9b3473b82069de6571f1bf8c292e5c599fbd37ce1103f8f95c0" +
        "f644b091ba227706c53a1952959a1685a410221d374924079144d8da9536a4bbab8e9af570468c81059a78a5" +
        "9d212b6a07883bc5f04adf019ab922e2ab1ee23ebb3cba0e4e2987e81538b385fa8dd28486a05d53f128dfc9" +
        "d18ab6bd1f2b7c92abf447eab70d3f4a73279c5fac6ec0e499cb07f4c03613836361f39cffdb75a09744b4bb" +
        "37bf8d54967d0bee745bb0f39a7397faab9a79cd7b81fc2ee089814a8c18198fbf3d5d7a0e7823305bfc5339" +
        "e4c61ccb64eee822acf9bc6a82e79fbce091ec91daac508970ef20e8bd4b3c2aa3dc3cd5af676d0fcfa2f433" +
        "9f68a52a4f81087a2807fc3aa701bbbf80f92e8e1a3e458fe558c99d34ae94de21b211f6402606daaa1791c3" +
        "be5f94730b3fa9d3e99ae34fc5682127c58fd4eb4d5b1e8f8b2848b3dbb0c1556d2c6043cceee5952e0a4f2b" +
        "83c2b21ed472fd596d0f3b74e56d640f65b7cf471959c1d90d46986e598b49cd799d0793d6397f8e295a9083" +
        "01728291a68a14df561735"

@Deprecated("Only used as long as the repos are signed with a different key")
private const val CERT_ONLINE =
    "30820503308202eba003020102020451af9e01300d06092a864886f70d01010b050030323110300e06" +
        "0355040b1307462d44726f6964311e301c060355040313156c6f63616c686f73742e6c6f63616c646f6d6169" +
        "6e301e170d3139303832393233303435335a170d3437303131343233303435335a30323110300e060355040b" +
        "1307462d44726f6964311e301c060355040313156c6f63616c686f73742e6c6f63616c646f6d61696e308202" +
        "22300d06092a864886f70d01010105000382020f003082020a0282020100da473e72cf0e202dd31ea6e229f8" +
        "957eb8c2491b3c99a654f9430da509117fce3a7a9aa75b0203375fd9aefd62243b8c05e0bc2c07d414b51c7f" +
        "626ea3fb9c17ccbba293f694117827f3f1b02c1bb81cbda4e352c2f0e126348ddda247b053a3c310ba6bad0f" +
        "15bfe27362a4f445ffc296e8e9f3e749729b0c22393cb3449fc33575a0d2798ba171abe6537b7dcce48be2b0" +
        "2d97ccbb918790b63443800e48762cc691dc5b68a532c2c83c14aecde41f56d2a4309651d6216c85d172057c" +
        "edabc5f5290850bb0160a346f860b4e472c3eefed9c5c5f66f8485bdcd5da19acabfe9389ece8ed6a485ad99" +
        "82de69a1d937ef394becf0815b42d8e88c948973500e5e35bb3760cb92f6516521e046faf8bc223ef625c457" +
        "5ca5edff58459879dd658291de027ecc387a11c4c1fa1982f44684c5c05b7dc3969625437be3cecc4a3406a2" +
        "9a0bedd6e51e569348c91e621dcbb415d0d3dba2fb07316c0070f8822362bcea7911e2105cc04a06e493ce3e" +
        "1560a9a00ec0d44b753c5c36dc95ff95976029aae140ab56304f7e5891d755099c3deb8f5b1ee662aaadc3fe" +
        "7fab74c0175a509ce28de7072268bf598329d389f27623a4677645394c9cc2a8391fa9bd09b3570fe0e258be" +
        "e1e8029d7916603e20c6972c6a7bfda2c26f691e84639874cd3aafb368ba51a4ce038ce84a945f6d604e6af3" +
        "ede5da58040972e053ad28c3d2890203010001a321301f301d0603551d0e041604142bf6de20fa9e51b8bce0" +
        "dd4b3497699fdd07af64300d06092a864886f70d01010b050003820201000ca2c66797928e49b17813dceeea" +
        "4fb64dbd675960756d85775b198754bef8bafc872f6f2ee594e4bef6b9c57ce701a95425a6eae7102e77ad4c" +
        "c251e9bfb56087a8ac14b175938560a9e8419587563c55c29e584fde5899b9c80aa3b8e9cbede188df19cd81" +
        "e642594185569c0966e022fe7a1f5d193f76df1bd21c304f19bff4e31c8aeeb986939b275bcf6d0ff7f69af6" +
        "230a7b5c96b6b1535e01eddc1d47b53d6aeac53c3dd7505b39bd8233c55bdadd42d64991e4ee941f435c88d1" +
        "93dbe91630bb1504836ffc9b7099ac782b1ddb9ec244363bd4cd7db34be2ad47b06dbb96ffcf316ce0f063b6" +
        "2bec781ad9d1059e8ef5913c9baa02db8f6b95932dc0d2e827e6e7f534069c6ae2e620b8149e4b6e9b6275f8" +
        "a9ffea2b61799bb08bf98c9d347aa0f0382b04a70f2a5b448531e39a2341c06388e9c449037903b1544e050a" +
        "382bd8694a245bfe64d25c9f7e119ddd2ecf7b641a11bcbd9f064a7fcfc77ac5635284ed058c95cfd401963b" +
        "2cbf55d1c7382c052b1772e87bff1906ea871fa6627e08797cce9b38abc97325934ab0c090571e6b069e18f5" +
        "c333333548ac83ef19e307e2433b6f02b3e7328cc3de0765df93ef3250b6e430d485da66694737888b8e0f19" +
        "a70b380dc37f6f7d8cad02ba514e611d734609b3166a584c06e5635b6367e5c992b1eb26221eb8f2a644ef07" +
        "52f1a2a38dfa310ac6ba4fe01bfb"
