package com.everton.campusflow

import android.content.Intent
import android.view.View
import android.widget.Button
import android.widget.DatePicker
import android.widget.TimePicker
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.ZonedDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.hamcrest.Matcher
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LembreteHorarioUiTest {
    @Test fun selecionaHorarioProximoNoFusoDoAparelhoEMantemRascunho() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch<AtividadeFormActivity>(Intent(context, AtividadeFormActivity::class.java)).use { scenario ->
            scenario.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            withTimeout(8_000) {
                while (true) {
                    var pronto = false
                    scenario.onActivity { pronto = ViewModelProvider(it)[AtividadeFormViewModel::class.java].estado.value.pronto }
                    if (pronto) break
                    delay(25)
                }
            }
            scenario.onActivity {
                ViewModelProvider(it)[AtividadeFormViewModel::class.java].rascunho(lembrar = true, automatico = false)
            }
            fun selecionar(alvo: ZonedDateTime) {
                scenario.onActivity { it.findViewById<Button>(R.id.btnHorarioLembrete).performClick() }
                onView(isAssignableFrom(DatePicker::class.java)).inRoot(isDialog()).perform(object : ViewAction {
                    override fun getDescription() = "Escolher a data do relógio do aparelho"
                    override fun getConstraints(): Matcher<View> = isAssignableFrom(DatePicker::class.java)
                    override fun perform(controller: UiController, view: View) {
                        (view as DatePicker).updateDate(alvo.year, alvo.monthValue - 1, alvo.dayOfMonth)
                    }
                })
                onView(withText(android.R.string.ok)).inRoot(isDialog()).perform(click())
                onView(isAssignableFrom(TimePicker::class.java)).inRoot(isDialog()).perform(object : ViewAction {
                    override fun getDescription() = "Selecionar hora e minuto em 24 horas"
                    override fun getConstraints(): Matcher<View> = isAssignableFrom(TimePicker::class.java)
                    override fun perform(controller: UiController, view: View) {
                        val picker = view as TimePicker
                        assertTrue(picker.is24HourView)
                        picker.hour = alvo.hour
                        picker.minute = alvo.minute
                    }
                })
                onView(withText(android.R.string.ok)).inRoot(isDialog()).perform(click())
            }
            selecionar(ZonedDateTime.now().withSecond(0).withNano(0))
            scenario.onActivity {
                assertNull(ViewModelProvider(it)[AtividadeFormViewModel::class.java].estado.value.lembrete)
            }
            val futuro = ZonedDateTime.now().plusMinutes(2).withSecond(0).withNano(0)
            selecionar(futuro)
            withTimeout(8_000) {
                while (true) {
                    var ok = false
                    scenario.onActivity { ok = ViewModelProvider(it)[AtividadeFormViewModel::class.java].estado.value.lembrete == futuro.toInstant().toEpochMilli() }
                    if (ok) break
                    delay(25)
                }
            }
            scenario.recreate()
            scenario.onActivity {
                assertEquals(futuro.toInstant().toEpochMilli(),
                    ViewModelProvider(it)[AtividadeFormViewModel::class.java].estado.value.lembrete)
                assertTrue(it.findViewById<TextView>(R.id.txtRelogioLembrete).text.toString().contains(futuro.zone.id))
            }
            // Não salva, não publica notificações e não altera dados do usuário.
        }
    }
}
