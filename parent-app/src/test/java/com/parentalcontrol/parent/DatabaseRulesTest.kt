package com.parentalcontrol.parent

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Penjaga agar aturan Realtime Database di repo tidak pernah terbuka atau bocor antar keluarga.
 * Berkas ini tidak otomatis diterapkan; pemilik proyek menempelkannya manual di Firebase Console.
 */
class DatabaseRulesTest {

    private val rules: JsonObject by lazy {
        // Unit test Gradle berjalan dari folder modul, berkas aturan ada di root repo
        JsonParser.parseString(File("../database.rules.json").readText()).asJsonObject.getAsJsonObject("rules")
    }

    @Test
    fun `root is closed by default`() {
        assertEquals("false", rules.get(".read").toString())
        assertEquals("false", rules.get(".write").toString())
    }

    @Test
    fun `only devices uid is exposed`() {
        assertEquals(setOf(".read", ".write", "devices"), rules.keySet())
        assertEquals(setOf("\$uid"), rules.getAsJsonObject("devices").keySet())
    }

    @Test
    fun `each family can only reach its own node`() {
        val family = rules.getAsJsonObject("devices").getAsJsonObject("\$uid")
        for (key in listOf(".read", ".write")) {
            val rule = family.get(key).asString
            assertTrue(rule, rule.contains("auth != null"))
            assertTrue(rule, rule.contains("auth.uid === \$uid"))
        }
    }

    @Test
    fun `no rule anywhere is literally true`() {
        fun walk(name: String, element: JsonElement) {
            if (element.isJsonObject) {
                element.asJsonObject.entrySet().forEach { (k, v) -> walk(k, v) }
            } else if (name == ".read" || name == ".write") {
                assertFalse("$name must not be true", element.toString().trim('"') == "true")
            }
        }
        walk("rules", rules)
    }
}
