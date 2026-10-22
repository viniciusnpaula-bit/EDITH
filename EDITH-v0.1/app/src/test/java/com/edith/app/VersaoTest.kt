package com.edith.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersaoTest {

    @Test
    fun comparaVersoes() {
        assertTrue(Versao.maisNova("0.4.1", "0.4.0"))
        assertFalse(Versao.maisNova("0.4.0", "0.4.0"))
        assertFalse(Versao.maisNova("0.3.9", "0.4.0"))
        assertTrue(Versao.maisNova("1.0", "0.9.9"))
    }

    @Test
    fun aceitaTagEBuild() {
        assertTrue(Versao.maisNova("v0.4.1+b11", "0.4.1"))
        assertFalse(Versao.maisNova("v0.4.1+b11", "0.4.1"))
        assertTrue(Versao.maisNova("v0.5.0+b12", "0.4.1"))
    }

    @Test
    fun textoVazioNaoEhNovidade() {
        assertFalse(Versao.maisNova("", "0.4.1"))
        assertFalse(Versao.maisNova("v", "0.4.1"))
    }
}
