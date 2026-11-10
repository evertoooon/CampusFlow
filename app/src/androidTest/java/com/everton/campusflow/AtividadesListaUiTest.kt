package com.everton.campusflow

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.recyclerview.widget.RecyclerView
import android.graphics.Rect
import com.everton.campusflow.data.*
import com.google.android.material.tabs.TabLayout
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AtividadesListaUiTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = CampusFlowDatabase.getInstance(context)

    private fun descendentes(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendentes(view.getChildAt(i)))
    }

    private fun cartao(activity: AtividadesActivity, titulo: String): View? =
        descendentes(activity.window.decorView).firstOrNull {
            it.id == R.id.cardAtividade && it.isShown && it.getGlobalVisibleRect(Rect()) &&
                it.findViewById<TextView>(R.id.txtTituloAtividade).text.toString() == titulo
        }

    private suspend fun aguardar(
        scenario: ActivityScenario<AtividadesActivity>, condicao: (AtividadesActivity) -> Boolean,
    ) = withTimeout(10_000) {
        while (true) {
            var pronto = false
            scenario.onActivity { pronto = condicao(it) }
            if (pronto) return@withTimeout
            delay(30)
        }
    }

    private suspend fun aguardarCartao(scenario: ActivityScenario<AtividadesActivity>, titulo: String) =
        aguardar(scenario) { activity ->
            if (cartao(activity, titulo) != null) true else {
                // RecyclerView não mantém cartões fora da tela anexados. Rola até a fixture,
                // sem depender da quantidade/data das atividades reais já cadastradas.
                descendentes(activity.window.decorView).filterIsInstance<RecyclerView>()
                    .filter { it.id == R.id.rvAtividades && it.getGlobalVisibleRect(Rect()) }
                    .forEach { lista ->
                        val adapter = lista.adapter as? AtividadesAdapter
                        val posicao = adapter?.currentList?.indexOfFirst { it.titulo == titulo } ?: -1
                        if (posicao >= 0) lista.scrollToPosition(posicao)
                    }
                false
            }
        }

    @Test
    fun concluirMoveParaOutraAbaRecriarPreservaEReabrirRetorna() = runBlocking<Unit> {
        val tag = "teste-lista-${UUID.randomUUID()}"
        val disciplinaId = db.disciplinaDao().inserir(Disciplina(nome = tag, professor = "Professora teste"))
        val dao = db.atividadeDao()
        val original = Atividade(
            titulo = tag, disciplinaId = disciplinaId, tipo = TipoAtividade.PROVA,
            prazoEpochDay = LocalDate.now().minusDays(1).toEpochDay(),
            prioridade = PrioridadeAtividade.ALTA, descricao = "Preservar este texto",
        )
        val id = dao.inserir(original)
        try {
            ActivityScenario.launch<AtividadesActivity>(Intent(context, AtividadesActivity::class.java)).use { scenario ->
                aguardarCartao(scenario, tag)
                scenario.onActivity {
                    val card = cartao(it, tag)!!
                    assertTrue(card.findViewById<TextView>(R.id.txtStatusAtrasado).isShown)
                    val check = card.findViewById<CheckBox>(R.id.chkConcluida)
                    assertFalse(check.isChecked)
                    assertTrue(check.contentDescription.toString().contains(tag))
                    check.performClick()
                }
                withTimeout(5_000) { while (dao.buscarPorId(id)?.concluida != true) delay(25) }
                assertEquals(original.copy(id = id, concluida = true), dao.buscarPorId(id))
                aguardar(scenario) { cartao(it, tag) == null }
                scenario.onActivity { it.findViewById<TabLayout>(R.id.tabLayoutAtividades).getTabAt(1)!!.select() }
                aguardarCartao(scenario, tag)
                scenario.recreate()
                aguardarCartao(scenario, tag)
                scenario.onActivity {
                    assertEquals(1, it.findViewById<TabLayout>(R.id.tabLayoutAtividades).selectedTabPosition)
                    val card = cartao(it, tag)!!
                    assertFalse(card.findViewById<TextView>(R.id.txtStatusAtrasado).isShown)
                    val check = card.findViewById<CheckBox>(R.id.chkConcluida)
                    assertTrue(check.isChecked)
                    check.performClick()
                }
                withTimeout(5_000) { while (dao.buscarPorId(id)?.concluida != false) delay(25) }
                scenario.onActivity { it.findViewById<TabLayout>(R.id.tabLayoutAtividades).getTabAt(0)!!.select() }
                aguardarCartao(scenario, tag)
                assertEquals(original.copy(id = id), dao.buscarPorId(id))
            }
        } finally {
            // Remove exclusivamente os IDs criados por este teste; nunca limpa o banco do usuário.
            dao.excluirPorId(id)
            db.disciplinaDao().excluirPorId(disciplinaId)
        }
    }
}
