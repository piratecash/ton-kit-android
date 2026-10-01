package com.tonapps.security

import io.horizontalsystems.tonkit.TonV2Fixture
import io.horizontalsystems.tonkit.tweetnacl.TweetNaclFast.Box
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

// Vectors from NaCl/libsodium test/default/box.c, box2.c and box.exp.
class CryptoBoxTest {

    @Test
    fun box_naclVector_matchesKnownCiphertext() {
        val box = Box(BOB_PK, ALICE_SK).box(MESSAGE, NONCE)

        assertArrayEquals(CIPHER, box)
    }

    @Test
    fun open_naclVector_recoversMessage() {
        val message = Box(ALICE_PK, BOB_SK).open(CIPHER, NONCE)

        assertArrayEquals(MESSAGE, message)
    }

    @Test
    fun keyPairFromSecretKey_naclSecretKeys_deriveKnownPublicKeys() {
        assertArrayEquals(ALICE_PK, Box.keyPair_fromSecretKey(ALICE_SK).publicKey)
        assertArrayEquals(BOB_PK, Box.keyPair_fromSecretKey(BOB_SK).publicKey)
    }

    @Test
    fun keyPairFromSecretKey_tonConnectFixtureKeyPairs_deriveStoredPublicKeys() {
        listOf(TonV2Fixture.aliceKeyPair, TonV2Fixture.bobKeyPair).forEach { stored ->
            assertArrayEquals(stored.publicKey, Box.keyPair_fromSecretKey(stored.privateKey).publicKey)
        }
    }

    @Test
    fun decrypt_dAppEncryptedNaclVector_recoversMessage() {
        val message = CryptoBox.decrypt(NONCE + CIPHER, remotePublicKey = ALICE_PK, localPrivateKey = BOB_SK)

        assertArrayEquals(MESSAGE, message)
    }

    @Test
    fun encrypt_naclKeys_counterpartyOpensNonceAndBox() {
        val body = CryptoBox.encrypt(MESSAGE, remotePublicKey = BOB_PK, localPrivateKey = ALICE_SK)

        assertEquals(Box.nonceLength + Box.overheadLength + MESSAGE.size, body.size)
        val nonce = body.sliceArray(0 until Box.nonceLength)
        val box = body.sliceArray(Box.nonceLength until body.size)
        assertArrayEquals(MESSAGE, Box(ALICE_PK, BOB_SK).open(box, nonce))
    }

    @Test
    fun encrypt_sameInputsTwice_usesFreshNonces() {
        val first = CryptoBox.encrypt(MESSAGE, BOB_PK, ALICE_SK)
        val second = CryptoBox.encrypt(MESSAGE, BOB_PK, ALICE_SK)

        assertFalse(first.sliceArray(0 until Box.nonceLength).contentEquals(second.sliceArray(0 until Box.nonceLength)))
    }

    @Test
    fun decrypt_freshKeyPairs_roundTrips() {
        val wallet = CryptoBox.keyPair()
        val dApp = CryptoBox.keyPair()

        val body = CryptoBox.encrypt(MESSAGE, dApp.publicKey, wallet.privateKey)

        assertArrayEquals(MESSAGE, CryptoBox.decrypt(body, wallet.publicKey, dApp.privateKey))
    }

    @Test
    fun keyPair_generated_publicKeyMatchesSecretKey() {
        val keyPair = CryptoBox.keyPair()

        assertArrayEquals(keyPair.publicKey, Box.keyPair_fromSecretKey(keyPair.privateKey).publicKey)
    }

    @Test
    fun decrypt_tamperedCipher_returnsZeroedMessageLikeLibsodiumBinding() {
        val tampered = NONCE + CIPHER
        tampered[tampered.size - 1] = (tampered[tampered.size - 1].toInt() xor 1).toByte()

        val message = CryptoBox.decrypt(tampered, ALICE_PK, BOB_SK)

        assertArrayEquals(ByteArray(MESSAGE.size), message)
    }

    @Test
    fun encrypt_lowOrderRemoteKey_throws() {
        LOW_ORDER_KEYS.forEach { key ->
            assertThrows(IllegalArgumentException::class.java) { CryptoBox.encrypt(MESSAGE, key, ALICE_SK) }
        }
    }

    @Test
    fun decrypt_lowOrderRemoteKey_returnsZeroedBuffer() {
        LOW_ORDER_KEYS.forEach { key ->
            // Any secret key yields the same all-zero shared point, so an attacker can forge this box.
            val forged = NONCE + Box(key, ALICE_SK).box(MESSAGE, NONCE)

            assertArrayEquals(ByteArray(MESSAGE.size), CryptoBox.decrypt(forged, key, BOB_SK))
        }
    }

    @Test
    fun dAppEntity_bridgeRoundTrip_interoperatesWithDAppKeys() {
        // The wallet holds bob's key pair; the dApp (clientId) is alice.
        val dApp = TonV2Fixture.dApps()[1].copy(clientId = hex(ALICE_PK))

        assertArrayEquals(MESSAGE, dApp.decrypt(NONCE + CIPHER))

        val body = dApp.encrypt(MESSAGE)
        val reply = CryptoBox.decrypt(body, remotePublicKey = BOB_PK, localPrivateKey = ALICE_SK)
        assertArrayEquals(MESSAGE, reply)
    }

    private companion object {
        val ALICE_SK = "77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a".hex()
        val ALICE_PK = "8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a".hex()
        val BOB_SK = "5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb".hex()
        val BOB_PK = "de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f".hex()
        val NONCE = "69696ee955b62b73cd62bda875fc73d68219e0036b7a0b37".hex()
        val LOW_ORDER_KEYS = listOf(
            ByteArray(32),
            ByteArray(32).apply { this[0] = 1 },
            "e0eb7a7c3b41b8ae1656e3faf19fc46ada098deb9c32b1fd866205165f49b800".hex(),
        )
        val MESSAGE = (
            "be075fc53c81f2d5cf141316ebeb0c7b5228c52a4c62cbd44b66849b64244ffce5ecbaaf33bd751a1ac728d45e6c6129" +
                "6cdc3c01233561f41db66cce314adb310e3be8250c46f06dceea3a7fa1348057e2f6556ad6b1318a024a838f21af1fde" +
                "048977eb48f59ffd4924ca1c60902e52f0a089bc76897040e082f937763848645e0705"
            ).hex()
        // MAC ‖ ciphertext, the crypto_box_easy layout.
        val CIPHER = (
            "f3ffc7703f9400e52a7dfb4b3d3305d98e993b9f48681273c29650ba32fc76ce48332ea7164d96a4476fb8c531a1186a" +
                "c0dfc17c98dce87b4da7f011ec48c97271d2c20f9b928fe2270d6fb863d51738b48eeee314a7cc8ab932164548e526ae" +
                "90224368517acfeabd6bb3732bc0e9da99832b61ca01b6de56244a9e88d5f9b37973f622a43d14a6599b1f654cb45a74" +
                "e355a5"
            ).hex()
    }
}
