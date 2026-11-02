package com.everton.campusflow

import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.widget.ScrollView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AtividadeMouseScrollTest {
    @Test fun formularioAceitaRodaDoMouseNosDoisSentidos() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val intent = Intent(instrumentation.targetContext, AtividadeFormActivity::class.java)
        ActivityScenario.launch<AtividadeFormActivity>(intent).use { scenario ->
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val scroll = activity.findViewById<ScrollView>(R.id.main)
                scroll.scrollTo(0, 0)
                assertTrue("O formulário deve ter conteúdo abaixo da área visível", scroll.canScrollVertically(1))
                fun roda(valor: Float) {
                    val propriedades = MotionEvent.PointerProperties().apply {
                        id = 0
                        toolType = MotionEvent.TOOL_TYPE_MOUSE
                    }
                    val coordenadas = MotionEvent.PointerCoords().apply {
                        x = scroll.width / 2f
                        y = scroll.height / 2f
                        setAxisValue(MotionEvent.AXIS_VSCROLL, valor)
                    }
                    val agora = SystemClock.uptimeMillis()
                    val evento = MotionEvent.obtain(agora, agora, MotionEvent.ACTION_SCROLL,
                        1, arrayOf(propriedades), arrayOf(coordenadas), 0, 0, 1f, 1f,
                        0, 0, InputDevice.SOURCE_MOUSE, 0)
                    try { activity.dispatchGenericMotionEvent(evento) } finally { evento.recycle() }
                }
                roda(-2f)
                val depois = scroll.scrollY
                assertTrue("A roda para baixo deve mover o formulário", depois > 0)
                roda(2f)
                assertTrue("A roda para cima deve retornar", scroll.scrollY < depois)
            }
        }
    }
}
