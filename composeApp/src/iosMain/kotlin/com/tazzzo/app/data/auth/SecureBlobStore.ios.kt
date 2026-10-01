@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.tazzzo.app.data.auth

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.posix.memcpy

actual fun createSecureBlobStore(): SecureBlobStore = KeychainBlobStore()

/**
 * One generic-password Keychain item. Accessibility is
 * `AfterFirstUnlockThisDeviceOnly`: readable by background launches after the
 * first unlock, never synced to iCloud Keychain and never migrated to another
 * device. The Keychain outlives an uninstall; [AuthInstallGuard] clears a
 * stale item on a fresh install.
 */
internal class KeychainBlobStore(
    private val service: String = "com.tazzzo.app.auth",
    private val account: String = "session.v1"
) : SecureBlobStore {

    override fun read(): String? = memScoped {
        val query = baseQuery()
        CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, result.ptr)
        CFRelease(query)
        when (status) {
            errSecSuccess -> {
                val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
                data.toByteArray().decodeToString()
            }
            errSecItemNotFound -> null
            else -> error("keychain read failed")
        }
    }

    override fun write(value: String) {
        delete()
        val query = baseQuery()
        val data = CFBridgingRetain(value.encodeToByteArray().toNSData())
        CFDictionaryAddValue(query, kSecValueData, data)
        CFDictionaryAddValue(query, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
        val status = SecItemAdd(query, null)
        CFRelease(data)
        CFRelease(query)
        check(status == errSecSuccess) { "keychain write failed" }
    }

    override fun delete() {
        val query = baseQuery()
        SecItemDelete(query)
        CFRelease(query)
    }

    private fun baseQuery(): CFMutableDictionaryRef? {
        val q = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        CFDictionaryAddValue(q, kSecClass, kSecClassGenericPassword)
        val s = CFBridgingRetain(service)
        val a = CFBridgingRetain(account)
        CFDictionaryAddValue(q, kSecAttrService, s)
        CFDictionaryAddValue(q, kSecAttrAccount, a)
        CFRelease(s)
        CFRelease(a)
        return q
    }
}

private fun ByteArray.toNSData(): NSData = memScoped {
    NSData.create(bytes = if (isEmpty()) null else allocArrayOf(this@toNSData), length = size.toULong())
}

private fun NSData.toByteArray(): ByteArray = ByteArray(length.toInt()).apply {
    if (isNotEmpty()) usePinned { memcpy(it.addressOf(0), bytes, length) }
}
