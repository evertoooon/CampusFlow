package com.everton.campusflow

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.everton.campusflow.data.AtividadeComDisciplina
import com.everton.campusflow.data.PrioridadeAtividade
import com.everton.campusflow.data.TipoAtividade
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class AtividadesAdapter(
    private val onEdit: (AtividadeComDisciplina) -> Unit,
    private val onToggleConcluida: (AtividadeComDisciplina) -> Unit,
) : ListAdapter<AtividadeComDisciplina, AtividadesAdapter.ViewHolder>(AtividadeDiffCallback()) {

    var salvandoIds: Set<Long> = emptySet()
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

    var agoraEpochMillis: Long = System.currentTimeMillis()
        set(value) { if (field != value) { field = value; notifyDataSetChanged() } }
    var hojeEpochDay: Long = LocalDate.now().toEpochDay()
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_atividade, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val chkConcluida: CheckBox = itemView.findViewById(R.id.chkConcluida)
        private val txtTitulo: TextView = itemView.findViewById(R.id.txtTituloAtividade)
        private val txtDisciplina: TextView = itemView.findViewById(R.id.txtDisciplinaAtividade)
        private val txtTipoEPrioridade: TextView = itemView.findViewById(R.id.txtTipoEPrioridade)
        private val txtPrazo: TextView = itemView.findViewById(R.id.txtPrazoAtividade)
        private val txtStatusAtrasado: TextView = itemView.findViewById(R.id.txtStatusAtrasado)

        fun bind(atividade: AtividadeComDisciplina) {
            val context = itemView.context
            val emSalvamento = salvandoIds.contains(atividade.id)
            itemView.setOnClickListener { if (!emSalvamento) onEdit(atividade) }
            itemView.isEnabled = !emSalvamento

            // Evita disparos falsos de listener ao reciclar views
            chkConcluida.setOnCheckedChangeListener(null)
            chkConcluida.isChecked = atividade.concluida
            chkConcluida.isEnabled = !emSalvamento
            chkConcluida.contentDescription = context.getString(
                if (atividade.concluida) R.string.activity_reopen_accessibility
                else R.string.activity_complete_accessibility,
                atividade.titulo,
            )

            chkConcluida.setOnClickListener {
                // O estado visual confirmado vem do banco, inclusive se a gravação falhar.
                chkConcluida.isChecked = atividade.concluida
                onToggleConcluida(atividade)
            }

            txtTitulo.text = atividade.titulo

            txtDisciplina.text = if (atividade.disciplinaProfessor.isBlank()) {
                atividade.disciplinaNome
            } else {
                "${atividade.disciplinaNome} (${context.getString(R.string.discipline_teacher_format, atividade.disciplinaProfessor)})"
            }

            val tipoTexto = when (atividade.tipo) {
                TipoAtividade.PROVA -> context.getString(R.string.activity_type_prova)
                TipoAtividade.TRABALHO -> context.getString(R.string.activity_type_trabalho)
                TipoAtividade.TAREFA -> context.getString(R.string.activity_type_tarefa)
            }

            val prioridadeTexto = when (atividade.prioridade) {
                PrioridadeAtividade.BAIXA -> context.getString(R.string.activity_priority_baixa)
                PrioridadeAtividade.MEDIA -> context.getString(R.string.activity_priority_media)
                PrioridadeAtividade.ALTA -> context.getString(R.string.activity_priority_alta)
            }

            txtTipoEPrioridade.text = "${context.getString(R.string.activity_type_label, tipoTexto)} • ${context.getString(R.string.activity_priority_label, prioridadeTexto)}"

            val dataFormatada = LocalDate.ofEpochDay(atividade.prazoEpochDay)
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            txtPrazo.text = if (atividade.prazoExatoEpochMillis != null) context.getString(R.string.activity_imported_due,
                com.everton.campusflow.prazos.Prazos.formatar(atividade.prazoExatoEpochMillis))
            else context.getString(R.string.activity_due_label, dataFormatada)

            val hoje = LocalDate.ofEpochDay(hojeEpochDay)
            val atrasada = atividade.estaAtrasada(hoje, agoraEpochMillis)
            txtStatusAtrasado.visibility = if (atrasada) View.VISIBLE else View.GONE
        }
    }

    private class AtividadeDiffCallback : DiffUtil.ItemCallback<AtividadeComDisciplina>() {
        override fun areItemsTheSame(
            oldItem: AtividadeComDisciplina,
            newItem: AtividadeComDisciplina,
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: AtividadeComDisciplina,
            newItem: AtividadeComDisciplina,
        ): Boolean = oldItem == newItem
    }
}
