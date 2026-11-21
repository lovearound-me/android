package me.lovearound.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A box sealed by the site's own code (site/src/signin/index.html, `seal`,
 * run in Node) to a key made for this test: the app opens it, and nothing
 * else does.
 */
class SiteSignInTest {
    private val state = "st4te-XYZ"
    private val pkcs8 = "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgooOVGq-EQPKyfs_wIOVb7K5YsKuZdecTB_5Gb5igJaahRANCAAR8z5ElcITTPtIguHGDZXoB-4qQ5oObPwCL78i0AIrNMWNDGDQXvWk46QBprRNi5ODeRZUIwuotS9RJUkGuxHW9"
    private val epk = "BNzUxVf1jufW4GrE3_TJycCTa0HhllJjrkr6ZJhemAinaolFFjJnpvrS0N6xxTWsbIAuuhiUvpLtRn-vpoozwP8"
    private val box = "jFsRcJMmZ28ZBKkd18o6dT9fnQ4vnLgfooTmCMdP9ETv5GD-gmHqVMU0P8BaMR45tdGY0QAzgSGCh6xA0LfTb8lGkHrKpQ27HoH8ED3OD0bXU-C21XcUqF5W"
    private val key = SiteSignIn.unb64(pkcs8)

    @Test fun opensWhatTheSiteSealed() {
        val opened = SiteSignIn.open(key, state, epk, box)!!
        assertEquals("u123", opened.uid)
        assertEquals("anna@example.com", opened.email)
        assertEquals("refresh-token", opened.refreshToken)
    }

    @Test fun anotherStateOpensNothing() {
        assertNull(SiteSignIn.open(key, "st4te-XYZ2", epk, box))
    }

    @Test fun aTouchedBoxOpensNothing() {
        val touched = box.dropLast(2) + (if (box.endsWith("AA")) "AB" else "AA")
        assertNull(SiteSignIn.open(key, state, epk, touched))
        assertNull(SiteSignIn.open(key, state, epk.dropLast(3) + "AAA", box))
        assertNull(SiteSignIn.open(key, state, "BA", box))
    }
}
