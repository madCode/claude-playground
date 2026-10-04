package com.app.jekyllposter.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.jekyllposter.testutil.testCipher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import javax.crypto.spec.SecretKeySpec

@RunWith(AndroidJUnit4::class)
class AccountStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val account = Account("writer", "github_pat_secret", "writer", "blog", "main", "https://writer.github.io/blog/")

    @Test fun theTokenIsSealedOnDiskAndReadBack() = runBlocking {
        val file = tmp.newFile("account.preferences_pb").also { it.delete() }
        val store = AccountStore(PreferenceDataStoreFactory.create { file }, testCipher())
        store.save(account)
        assertEquals(account, store.current())
        assertFalse(String(file.readBytes(), Charsets.ISO_8859_1).contains("github_pat_secret"))
        store.signOut()
        assertNull(store.current())
    }

    @Test fun anExpiringTokenIsRenewedBeforeUse() = runBlocking {
        val file = tmp.newFile("account.preferences_pb").also { it.delete() }
        var now = 1_000_000L
        val renewals = mutableListOf<String>()
        val store = AccountStore(PreferenceDataStoreFactory.create { file }, testCipher(), { refresh ->
            renewals += refresh
            com.app.jekyllposter.core.github.DeviceFlow.Tokens("ghu_new", 28_800, "ghr_new")
        }) { now }
        store.save(account.copy(token = "ghu_old", refreshToken = "ghr_old", expiresAt = now + 60 * 60_000))
        assertEquals("ghu_old", store.current()!!.token)
        now += 58 * 60_000
        val renewed = store.current()!!
        assertEquals("ghu_new", renewed.token)
        assertEquals(listOf("ghr_old"), renewals)
        assertEquals(now + 28_800_000, renewed.expiresAt)
        // Kept, sealed like the token.
        assertEquals("ghr_new", store.current()!!.refreshToken)
        assertFalse(String(file.readBytes(), Charsets.ISO_8859_1).contains("ghr_new"))
    }

    @Test fun aTokenThatCannotBeUnsealedReadsAsSignedOut() = runBlocking {
        val file = tmp.newFile("account.preferences_pb").also { it.delete() }
        val data = PreferenceDataStoreFactory.create { file }
        AccountStore(data, testCipher()).save(account)
        val otherKey = AesGcmCipher { SecretKeySpec(ByteArray(32) { 7 }, "AES") }
        assertNull(AccountStore(data, otherKey).current())
    }
}
