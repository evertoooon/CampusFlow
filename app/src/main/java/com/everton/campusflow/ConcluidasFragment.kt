package com.everton.campusflow

import android.os.Bundle
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class ConcluidasFragment : Fragment() {

    private lateinit var viewModel: AtividadesViewModel
    private lateinit var adapter: AtividadesAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_atividades_lista, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[AtividadesViewModel::class.java]

        val rv = view.findViewById<RecyclerView>(R.id.rvAtividades)
        val status = view.findViewById<TextView>(R.id.txtStatusAtividades)
        val estadoVazio = view.findViewById<View>(R.id.estadoVazioAtividades)
        val txtTituloVazio = view.findViewById<TextView>(R.id.txtTituloVazioAtividades)
        val txtDescricaoVazio = view.findViewById<TextView>(R.id.txtDescricaoVazioAtividades)
        val btnTentar = view.findViewById<MaterialButton>(R.id.btnTentarNovamenteAtividades)

        txtTituloVazio.setText(R.string.activities_completed_empty_title)
        txtDescricaoVazio.setText(R.string.activities_completed_empty_desc)

        adapter = AtividadesAdapter(onEdit = { atividade ->
            startActivity(Intent(requireContext(), AtividadeFormActivity::class.java)
                .putExtra(AtividadeFormActivity.EXTRA_ATIVIDADE_ID, atividade.id))
        }) { atividade ->
            viewModel.alternarConcluida(atividade)
        }
        rv.adapter = adapter

        btnTentar.setOnClickListener {
            viewModel.carregarAtividades()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.estado.collect { estado ->
                    adapter.salvandoIds = estado.salvandoIds
                    adapter.hojeEpochDay = estado.hojeEpochDay
                    adapter.agoraEpochMillis = estado.agoraEpochMillis
                    adapter.submitList(estado.concluidas)

                    when {
                        estado.carregando -> {
                            status.setText(R.string.activities_loading)
                            status.visibility = View.VISIBLE
                            estadoVazio.visibility = View.GONE
                            rv.visibility = View.GONE
                            btnTentar.visibility = View.GONE
                        }
                        estado.erro -> {
                            status.setText(R.string.activities_load_error)
                            status.visibility = View.VISIBLE
                            estadoVazio.visibility = View.GONE
                            rv.visibility = View.GONE
                            btnTentar.visibility = View.VISIBLE
                        }
                        estado.concluidas.isEmpty() -> {
                            status.visibility = View.GONE
                            estadoVazio.visibility = View.VISIBLE
                            rv.visibility = View.GONE
                            btnTentar.visibility = View.GONE
                        }
                        else -> {
                            status.visibility = View.GONE
                            estadoVazio.visibility = View.GONE
                            rv.visibility = View.VISIBLE
                            btnTentar.visibility = View.GONE
                        }
                    }

                }
            }
        }
    }

    override fun onDestroyView() {
        view?.findViewById<RecyclerView>(R.id.rvAtividades)?.adapter = null
        super.onDestroyView()
    }
}
