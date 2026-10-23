package com.everton.campusflow

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class DisciplinaFormActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_DISCIPLINA_ID = "disciplina_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_disciplina_form)

        val barsController = WindowCompat.getInsetsController(window, window.decorView)
        barsController.isAppearanceLightStatusBars = true
        barsController.isAppearanceLightNavigationBars = true

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val safeArea = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime() or
                    WindowInsetsCompat.Type.displayCutout(),
            )
            view.setPadding(safeArea.left, safeArea.top, safeArea.right, safeArea.bottom)
            insets
        }

        val edtNome = findViewById<TextInputEditText>(R.id.edtNomeDisciplina)
        val inputNome = findViewById<TextInputLayout>(R.id.inputNomeDisciplina)
        val edtProfessor = findViewById<TextInputEditText>(R.id.edtProfessorDisciplina)
        val btnSalvar = findViewById<Button>(R.id.btnSalvarDisciplina)
        val btnVoltar = findViewById<Button>(R.id.btnVoltarDisciplinas)
        val btnExcluir = findViewById<Button>(R.id.btnExcluirDisciplina)
        val txtErro = findViewById<TextView>(R.id.txtErroSalvar)
        val model = ViewModelProvider(this)[DisciplinaFormViewModel::class.java]
        findViewById<TextView>(R.id.txtTituloDisciplina).setText(
            if (model.editando) R.string.discipline_edit_title else R.string.discipline_form_title,
        )
        var preenchendoCampos = false
        edtNome.doAfterTextChanged {
            if (!preenchendoCampos) {
                model.atualizarRascunho(edtNome.text.toString(), edtProfessor.text.toString())
            }
        }
        edtProfessor.doAfterTextChanged {
            if (!preenchendoCampos) {
                model.atualizarRascunho(edtNome.text.toString(), edtProfessor.text.toString())
            }
        }

        btnSalvar.setOnClickListener {
            val nome = edtNome.text.toString().trim()
            inputNome.error = null

            if (nome.isEmpty()) {
                // O TextInputLayout mantém o erro abaixo do campo Material.
                inputNome.error = getString(R.string.error_empty_discipline_name)
                edtNome.requestFocus()
                return@setOnClickListener
            }

            model.salvar(nome, edtProfessor.text.toString())
        }

        btnVoltar.setOnClickListener {
            finish()
        }

        btnExcluir.visibility = if (model.editando) View.VISIBLE else View.GONE
        btnExcluir.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.discipline_delete_title)
                .setMessage(getString(R.string.discipline_delete_confirmation, model.nomeOriginal))
                .setNegativeButton(R.string.discipline_delete_cancel, null)
                .setPositiveButton(R.string.discipline_delete_confirm) { _, _ -> model.excluir() }
                .show()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.estado.collect { estado ->
                    val editavel = estado.pronto && !estado.salvando && !estado.salva && !estado.excluida
                    btnSalvar.isEnabled = editavel
                    btnExcluir.isEnabled = editavel
                    btnExcluir.setText(
                        if (estado.excluindo) R.string.discipline_deleting else R.string.discipline_delete,
                    )
                    btnVoltar.isEnabled = !estado.salvando
                    edtNome.isEnabled = editavel
                    edtProfessor.isEnabled = editavel
                    // Não reescreve o texto ao receber atualizações do próprio usuário.
                    preenchendoCampos = true
                    if (edtNome.text.toString() != estado.nome) edtNome.setText(estado.nome)
                    if (edtProfessor.text.toString() != estado.professor) {
                        edtProfessor.setText(estado.professor)
                    }
                    preenchendoCampos = false
                    btnSalvar.setText(
                        when {
                            estado.salvando -> R.string.discipline_saving
                            model.editando -> R.string.discipline_update
                            else -> R.string.discipline_save
                        },
                    )
                    txtErro.visibility = if (
                        estado.erro || estado.erroCarga || estado.inexistente || estado.carregando || estado.erroExclusao
                    ) View.VISIBLE else View.GONE
                    txtErro.setText(
                        when {
                            estado.inexistente -> R.string.discipline_not_found
                            estado.erroCarga -> R.string.discipline_load_error
                            estado.carregando -> R.string.discipline_loading
                            estado.erroExclusao -> R.string.discipline_delete_error
                            else -> R.string.discipline_save_error
                        },
                    )
                    if (estado.salva) {
                        Toast.makeText(
                            this@DisciplinaFormActivity,
                            if (model.editando) R.string.discipline_updated else R.string.discipline_saved,
                            Toast.LENGTH_SHORT,
                        ).show()
                        finish()
                    }
                    if (estado.excluida) {
                        Toast.makeText(
                            this@DisciplinaFormActivity, R.string.discipline_deleted, Toast.LENGTH_SHORT,
                        ).show()
                        finish()
                    }
                }
            }
        }
    }
}
