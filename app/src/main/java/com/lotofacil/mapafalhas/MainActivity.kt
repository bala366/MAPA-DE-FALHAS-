package com.lotofacil.mapafalhas

import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.lotofacil.mapafalhas.databinding.ActivityMainBinding
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.max

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private var concursos: List<Concurso> = emptyList()

    data class Concurso(val numero: Int, val dezenas: Set<Int>)
    data class Score(val dezena: Int, val valor: Double)

    private val escolherArquivo = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) { }

        try {
            concursos = lerArquivo(uri)
            if (concursos.isEmpty()) {
                b.txtStatus.text = "Não consegui reconhecer concursos nesse arquivo."
                return@registerForActivityResult
            }
            b.txtArquivo.text = uri.lastPathSegment ?: "Arquivo selecionado"
            b.edtInicio.setText(concursos.first().numero.toString())
            b.edtFim.setText(concursos.last().numero.toString())
            b.txtStatus.text = "${concursos.size} concursos encontrados. Escolha o intervalo."
        } catch (e: Exception) {
            b.txtStatus.text = "Erro ao ler arquivo: ${e.message}"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnArquivo.setOnClickListener {
            escolherArquivo.launch(arrayOf("text/plain", "text/csv", "text/*", "*/*"))
        }

        b.btnAnalisar.setOnClickListener { analisar() }
    }

    private fun lerArquivo(uri: Uri): List<Concurso> {
        val mapa = linkedMapOf<Int, Set<Int>>()
        contentResolver.openInputStream(uri).use { input ->
            BufferedReader(InputStreamReader(input)).useLines { lines ->
                lines.forEach { linha ->
                    val nums = Regex("\\d+").findAll(linha).map { it.value.toInt() }.toList()
                    if (nums.size >= 16) {
                        val concurso = nums.first()
                        val dezenas = nums.drop(1).filter { it in 1..25 }.takeLast(15)
                        if (dezenas.size == 15 && dezenas.toSet().size == 15) {
                            mapa[concurso] = dezenas.toSet()
                        }
                    }
                }
            }
        }
        return mapa.entries.sortedBy { it.key }.map { Concurso(it.key, it.value) }
    }

    private fun analisar() {
        if (concursos.isEmpty()) {
            b.txtStatus.text = "Escolha primeiro o arquivo de resultados."
            return
        }

        val inicio = b.edtInicio.text.toString().toIntOrNull()
        val fim = b.edtFim.text.toString().toIntOrNull()
        if (inicio == null || fim == null || inicio > fim) {
            b.txtStatus.text = "Informe um intervalo válido."
            return
        }

        val trecho = concursos.filter { it.numero in inicio..fim }
        if (trecho.isEmpty() || trecho.first().numero != inicio || trecho.last().numero != fim) {
            b.txtStatus.text = "O intervalo precisa existir completo dentro do arquivo."
            return
        }

        val resultados = trecho.map { it.dezenas }
        b.txtMapa.text = montarMapa(trecho)

        val ranking = (1..25).map { d -> Score(d, calcularScore(resultados, d)) }
            .sortedByDescending { it.valor }
        val candidatas = ranking.take(15).map { it.dezena }
        val scores = ranking.associate { it.dezena to it.valor }

        var melhor: List<Int> = emptyList()
        var melhorScore = Double.NEGATIVE_INFINITY
        combinar(candidatas, 10).forEach { comb ->
            val s = comb.sumOf { scores[it] ?: 0.0 } + pontuarConjunto(resultados, comb)
            if (s > melhorScore) {
                melhorScore = s
                melhor = comb.sorted()
            }
        }

        val jogo = (1..25).filter { it !in melhor }
        b.txtFalhas.text = melhor.joinToString("  ") { "%02d".format(it) }
        b.txtJogo.text = jogo.joinToString("  ") { "%02d".format(it) }
        b.txtStatus.text = "Intervalo $inicio a $fim analisado (${trecho.size} concursos). Palpite projetado para o próximo concurso."
    }

    private fun montarMapa(trecho: List<Concurso>): String {
        val sb = StringBuilder("CONC  ")
        (1..25).forEach { sb.append("%02d ".format(it)) }
        sb.append('\n')
        trecho.forEach { c ->
            sb.append("%04d  ".format(c.numero))
            (1..25).forEach { d -> sb.append(if (d in c.dezenas) "██ " else ".. ") }
            sb.append('\n')
        }
        return sb.toString()
    }

    private fun calcularScore(r: List<Set<Int>>, d: Int): Double {
        val total = freq(r, d, r.size)
        val f30 = freq(r, d, 30)
        val f15 = freq(r, d, 15)
        val f10 = freq(r, d, 10)
        val f5 = freq(r, d, 5)
        val seq = sequenciaAtual(r, d)
        val maior = maiorSequencia(r, d)
        val cont = continuidade(r, d)
        val entrada = entradaFalha(r, d)
        val recente = tendenciaRecente(r, d)
        val ultimoFalhou = d !in r.last()

        var s = total * 15 + f30 * 12 + f15 * 14 + f10 * 17 + f5 * 20 + recente * 20
        s += if (ultimoFalhou) cont * 22 else entrada * 22
        s += when (seq) { 1 -> 3.0; 2 -> 5.0; 3 -> 3.0; else -> if (seq >= 4) -((seq - 3) * 2.0) else 0.0 }
        if (maior > 0 && seq >= maior) s -= 3.0
        return s
    }

    private fun freq(r: List<Set<Int>>, d: Int, janela: Int): Double {
        val sub = r.takeLast(minOf(janela, r.size))
        if (sub.isEmpty()) return 0.0
        return sub.count { d !in it }.toDouble() / sub.size
    }

    private fun sequenciaAtual(r: List<Set<Int>>, d: Int): Int {
        var n = 0
        for (x in r.asReversed()) if (d !in x) n++ else break
        return n
    }

    private fun maiorSequencia(r: List<Set<Int>>, d: Int): Int {
        var maior = 0; var atual = 0
        r.forEach { x ->
            if (d !in x) { atual++; maior = max(maior, atual) } else atual = 0
        }
        return maior
    }

    private fun continuidade(r: List<Set<Int>>, d: Int): Double {
        var op = 0; var sim = 0
        for (i in 0 until r.size - 1) if (d !in r[i]) { op++; if (d !in r[i + 1]) sim++ }
        return if (op == 0) 0.0 else sim.toDouble() / op
    }

    private fun entradaFalha(r: List<Set<Int>>, d: Int): Double {
        var op = 0; var sim = 0
        for (i in 0 until r.size - 1) if (d in r[i]) { op++; if (d !in r[i + 1]) sim++ }
        return if (op == 0) 0.0 else sim.toDouble() / op
    }

    private fun tendenciaRecente(r: List<Set<Int>>, d: Int): Double {
        if (r.isEmpty()) return 0.0
        var soma = 0.0; var pesos = 0.0
        r.forEachIndexed { i, x ->
            val p = (i + 1).toDouble(); pesos += p; if (d !in x) soma += p
        }
        return soma / pesos
    }

    private fun pontuarConjunto(r: List<Set<Int>>, comb: List<Int>): Double {
        val conjunto = comb.toSet()
        val mapas = r.map { (1..25).filter { d -> d !in it }.toSet() }
        val ultimo = mapas.last()
        val repetidas = conjunto.intersect(ultimo).size
        var s = when (repetidas) { in 3..6 -> 20.0; 2, 7 -> 8.0; else -> -8.0 }

        val ultimos = mapas.takeLast(15)
        ultimos.forEachIndexed { i, m ->
            val inter = conjunto.intersect(m).size
            val peso = (i + 1).toDouble() / ultimos.size
            s += when (inter) { 5 -> 5 * peso; 4, 6 -> 3 * peso; 3, 7 -> 1 * peso; else -> 0.0 }
        }

        val linhas = listOf((1..5).toSet(), (6..10).toSet(), (11..15).toSet(), (16..20).toSet(), (21..25).toSet())
        val colunas = listOf(setOf(1,6,11,16,21), setOf(2,7,12,17,22), setOf(3,8,13,18,23), setOf(4,9,14,19,24), setOf(5,10,15,20,25))
        (linhas + colunas).forEach { grupo ->
            val q = conjunto.intersect(grupo).size
            if (q >= 4) s -= 5 else if (q == 3) s -= 1
        }
        return s
    }

    private fun <T> combinar(lista: List<T>, k: Int): Sequence<List<T>> = sequence {
        suspend fun SequenceScope<List<T>>.rec(inicio: Int, atual: MutableList<T>) {
            if (atual.size == k) { yield(atual.toList()); return }
            for (i in inicio..lista.size - (k - atual.size)) {
                atual.add(lista[i]); rec(i + 1, atual); atual.removeAt(atual.lastIndex)
            }
        }
        rec(0, mutableListOf())
    }
}
