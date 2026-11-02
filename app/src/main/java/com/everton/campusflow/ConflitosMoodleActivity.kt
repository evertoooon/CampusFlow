package com.everton.campusflow

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.*
import androidx.lifecycle.*
import com.everton.campusflow.importacao.PrazosIcs
import com.everton.campusflow.moodle.CampoMoodle
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ConflitosMoodleActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge(); setContentView(R.layout.activity_conflitos_moodle)
        WindowCompat.getInsetsController(window,window.decorView).apply { isAppearanceLightStatusBars=true; isAppearanceLightNavigationBars=true }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v,i ->
            val a=i.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(a.left,a.top,a.right,a.bottom); i
        }
        findViewById<Button>(R.id.btnVoltarConflitos).setOnClickListener { finish() }
        val model=ViewModelProvider(this)[ConflitosMoodleViewModel::class.java]
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { model.estado.collect { s ->
            findViewById<TextView>(R.id.txtEstadoConflitos).text=when {
                s.ocupado -> getString(R.string.moodle_conflict_saving)
                s.erro -> getString(R.string.moodle_conflict_error)
                s.mensagem!=null -> getString(s.mensagem)
                s.itens.isEmpty() -> getString(R.string.moodle_conflict_empty)
                else -> getString(R.string.moodle_conflict_count,s.itens.size)
            }
            val lista=findViewById<LinearLayout>(R.id.listaConflitosMoodle); lista.removeAllViews()
            fun texto(valor:String,negrito:Boolean=false) { lista.addView(TextView(this@ConflitosMoodleActivity).apply {
                text=valor; textSize=16f; setTextColor(getColor(R.color.cf_text_primary))
                setPadding(0,16,0,8); if(negrito) setTypeface(null,android.graphics.Typeface.BOLD)
            }) }
            for(item in s.itens) {
                val c=item.controle; val a=item.atividade
                texto(a.titulo,true); texto(item.disciplina)
                fun campo(tipo:CampoMoodle,nome:Int,local:String,remoto:String,aplicavel:Boolean=true) {
                    texto(getString(nome),true)
                    texto(getString(R.string.moodle_conflict_local,local)); texto(getString(R.string.moodle_conflict_remote,remoto))
                    lista.addView(MaterialButton(this@ConflitosMoodleActivity).apply {
                        text=getString(R.string.moodle_conflict_keep); isAllCaps=false; minHeight=(56*resources.displayMetrics.density).toInt()
                        contentDescription=getString(R.string.moodle_conflict_action,text,getString(nome),a.titulo)
                        isEnabled=!s.ocupado; setOnClickListener { model.resolver(item,tipo,false) }
                    })
                    if(aplicavel) lista.addView(MaterialButton(this@ConflitosMoodleActivity).apply {
                        text=getString(if(tipo==CampoMoodle.CONCLUSAO && c.conclusaoRemota==false) R.string.moodle_conflict_reopen else R.string.moodle_conflict_use)
                        isAllCaps=false; minHeight=(56*resources.displayMetrics.density).toInt()
                        contentDescription=getString(R.string.moodle_conflict_action,text,getString(nome),a.titulo)
                        isEnabled=!s.ocupado; setOnClickListener { model.resolver(item,tipo,true) }
                    })
                }
                if(c.tituloPendente) campo(CampoMoodle.TITULO,R.string.moodle_conflict_field_title,a.titulo,c.tituloRemoto)
                if(c.prazoPendente) campo(CampoMoodle.PRAZO,R.string.moodle_conflict_field_due,
                    a.prazoExatoEpochMillis?.let(PrazosIcs::formatar)?:LocalDate.ofEpochDay(a.prazoEpochDay).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    c.prazoRemoto?.let(PrazosIcs::formatar)?:getString(R.string.moodle_conflict_due_required),c.prazoRemoto!=null)
                if(c.conclusaoPendente) campo(CampoMoodle.CONCLUSAO,R.string.moodle_conflict_field_completion,
                    getString(if(a.concluida) R.string.moodle_conflict_completed else R.string.moodle_conflict_pending),
                    getString(if(c.conclusaoRemota==true) R.string.moodle_conflict_completed else R.string.moodle_conflict_pending))
            }
        } } }
    }
}
