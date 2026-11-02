package com.everton.campusflow.data

import androidx.room.TypeConverter

class AtividadeConverters {
    // Nomes estáveis no banco; os rótulos traduzidos pertencem à interface.
    @TypeConverter fun tipoParaTexto(tipo: TipoAtividade): String = tipo.name
    @TypeConverter fun textoParaTipo(valor: String): TipoAtividade = TipoAtividade.valueOf(valor)
    @TypeConverter fun prioridadeParaTexto(prioridade: PrioridadeAtividade): String = prioridade.name
    @TypeConverter fun textoParaPrioridade(valor: String): PrioridadeAtividade = PrioridadeAtividade.valueOf(valor)
}
