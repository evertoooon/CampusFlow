package com.everton.campusflow

import android.content.Intent
import android.graphics.Rect
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Executar com fonte do Android em 200%; não muda configurações do usuário. */
@RunWith(AndroidJUnit4::class)
class FonteAmpliadaTest {
    private val ins=InstrumentationRegistry.getInstrumentation()
    private fun exigirFonte() = assumeTrue("Teste dirigido com fonte em 200%",ins.targetContext.resources.configuration.fontScale>=1.9f)
    private fun conferirTexto(v:TextView) {
        val layout=v.layout ?: error("Texto ainda não medido")
        for(i in 0 until layout.lineCount) assertEquals("Texto truncado: ${v.id}",0,layout.getEllipsisCount(i))
        assertTrue("Texto cortado verticalmente: ${v.id}",layout.getLineBottom(layout.lineCount-1)<=v.height-v.compoundPaddingTop-v.compoundPaddingBottom)
    }
    @Test fun formularioMantemSalvarEVoltarAcessiveisComRolagem() = runBlocking<Unit> {
        exigirFonte()
        ActivityScenario.launch<AtividadeFormActivity>(Intent(ins.targetContext,AtividadeFormActivity::class.java)).use { s ->
            ins.waitForIdleSync()
            withTimeout(10000) { while(true) {
                var pronto=false
                s.onActivity { pronto=ViewModelProvider(it)[AtividadeFormViewModel::class.java].estado.value.disciplinasCarregadas }
                if(pronto) break
                delay(30)
            } }
            ins.waitForIdleSync()
            s.onActivity { val scroll=it.findViewById<ScrollView>(R.id.main); scroll.scrollTo(0,scroll.getChildAt(0).height) }
            ins.waitForIdleSync()
            s.onActivity { a ->
                for(id in listOf(R.id.btnSalvarAtividade,R.id.btnVoltarAtividades)) {
                    val v=a.findViewById<TextView>(id)
                    val rect=Rect()
                    assertTrue("Ação precisa ser alcançável pela rolagem",v.getGlobalVisibleRect(rect))
                    assertTrue("Ação cortada pela janela",rect.height()>=v.height-2)
                    assertTrue("Área de toque mínima",v.height>=48*a.resources.displayMetrics.density)
                    conferirTexto(v)
                }
                assertTrue(a.findViewById<ScrollView>(R.id.main).scrollY>0)
            }
        }
    }
    @Test fun listaReservaEspacoParaAsAtividadesComFonteAmpliada() {
        exigirFonte()
        ActivityScenario.launch<AtividadesActivity>(Intent(ins.targetContext,AtividadesActivity::class.java)).use { s ->
            ins.waitForIdleSync()
            s.onActivity { a ->
                val pager=a.findViewById<View>(R.id.viewPagerAtividades)
                val rect=Rect()
                assertTrue("A lista precisa continuar visível",pager.getGlobalVisibleRect(rect))
                assertTrue("É preciso espaço para navegar nas atividades",rect.height()>=96*a.resources.displayMetrics.density)
                for(id in listOf(R.id.btnNovaAtividade,R.id.btnVoltarResumoAtividades))
                    conferirTexto(a.findViewById(id))
            }
        }
    }
}
