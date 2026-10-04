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

    @Test fun aTokenThatCannotBeUnsealedReadsAsSignedOut() = runBlocking {
        val file = tmp.newFile("account.preferences_pb").also { it.delete() }
        val data = PreferenceDataStoreFactory.create { file }
        AccountStore(data, testCipher()).save(account)
        val otherKey = AesGcmCipher { SecretKeySpec(ByteArray(32) { 7 }, "AES") }
        assertNull(AccountStore(data, otherKey).current())
    }
}
