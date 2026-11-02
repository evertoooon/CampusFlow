package com.everton.campusflow

import android.content.Intent
import android.view.WindowManager
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MoodleUiTest {
    @Test fun validaVaziosNaoRestauraCredenciaisEProtegeJanela() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch<MoodleActivity>(Intent(context, MoodleActivity::class.java)).use { scenario ->
            scenario.onActivity {
                assertTrue(it.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                it.findViewById<Button>(R.id.btnVerificarMoodle).performClick()
                assertNotNull(it.findViewById<TextInputLayout>(R.id.inputUsuarioMoodle).error)
                assertNotNull(it.findViewById<TextInputLayout>(R.id.inputSenhaMoodle).error)
                val usuario = it.findViewById<TextInputEditText>(R.id.edtUsuarioMoodle)
                val senha = it.findViewById<TextInputEditText>(R.id.edtSenhaMoodle)
                assertFalse(usuario.isSaveEnabled); assertFalse(senha.isSaveEnabled)
                usuario.setText("usuario-ficticio-sem-conta")
                senha.setText("senha-ficticia-sem-conta")
                // Não pressiona Verificar com dados preenchidos: nenhuma conexão externa é iniciada.
            }
            scenario.recreate()
            scenario.onActivity {
                assertTrue(it.findViewById<TextInputEditText>(R.id.edtUsuarioMoodle).text.isNullOrEmpty())
                assertTrue(it.findViewById<TextInputEditText>(R.id.edtSenhaMoodle).text.isNullOrEmpty())
            }
        }
    }
}
