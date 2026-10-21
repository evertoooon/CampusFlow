package com.everton.campusflow

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        val mainView = findViewById<android.view.View>(R.id.main)
        ViewCompat.setOnApplyWindowInsetsListener(mainView) { view, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars(),
            )

            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom,
            )

            insets
        }

        val edtEmail = findViewById<EditText>(R.id.edtEmail)
        val edtSenha = findViewById<EditText>(R.id.edtSenha)
        val btnEntrar = findViewById<Button>(R.id.btnEntrar)

        btnEntrar.setOnClickListener {

            val email = edtEmail.text.toString().trim()
            val senha = edtSenha.text.toString()

            when {
                email.isEmpty() -> {
                    edtEmail.error = getString(R.string.error_empty_email)
                    edtEmail.requestFocus()
                }

                senha.isEmpty() -> {
                    edtSenha.error = getString(R.string.error_empty_password)
                    edtSenha.requestFocus()
                }

                ((email == "teste@campusflow.com") && (senha == "123456")) -> {

                    Toast.makeText(
                        this,
                        getString(R.string.toast_login_success),
                        Toast.LENGTH_SHORT,
                    ).show()

                    val intent = Intent(
                        this,
                        MainActivity::class.java,
                    )

                    startActivity(intent)
                    finish()
                }

                else -> {
                    Toast.makeText(
                        this,
                        getString(R.string.toast_login_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }
}
