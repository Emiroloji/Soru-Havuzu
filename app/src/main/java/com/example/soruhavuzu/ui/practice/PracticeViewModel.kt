package com.example.soruhavuzu.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.soruhavuzu.SoruHavuzuApplication
import com.example.soruhavuzu.data.remote.ApiClient
import com.example.soruhavuzu.data.remote.ApiResult
import com.example.soruhavuzu.data.remote.AttemptRequest
import com.example.soruhavuzu.data.remote.AttemptResponse
import com.example.soruhavuzu.data.remote.BucketCountDto
import com.example.soruhavuzu.data.remote.CatalogExamDto
import com.example.soruhavuzu.data.remote.QuestionDto
import com.example.soruhavuzu.data.remote.ReportReason
import com.example.soruhavuzu.data.remote.ReportRequest
import com.example.soruhavuzu.data.remote.SessionType
import com.example.soruhavuzu.data.remote.UserStatsDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PracticeFilter(
    val examId: String? = null,
    val subjectId: String? = null,
    val topicId: String? = null,
    val minBucket: Int = 4,
    val maxBucket: Int = 4,
    val includeSolved: Boolean = false
)

data class HomeState(
    val catalog: List<CatalogExamDto> = emptyList(),
    val filter: PracticeFilter = PracticeFilter(),
    val bucketCounts: List<BucketCountDto> = emptyList(),
    val stats: UserStatsDto? = null,
    val isLoading: Boolean = true,
    val error: String? = null
) {
    val selectedExam: CatalogExamDto? get() = catalog.firstOrNull { it.id == filter.examId }

    /** Seçili aralıkta, filtreye göre (çözülmüşler dahil mi) hazır soru sayısı. */
    val matchingCount: Int
        get() = bucketCounts.filter { it.bucket in filter.minBucket..filter.maxBucket }
            .sumOf { if (filter.includeSolved) it.total else it.unsolved }
}

data class SessionState(
    val type: SessionType,
    val filter: PracticeFilter,
    val question: QuestionDto? = null,
    val selectedOption: Int? = null,
    val evaluation: AttemptResponse? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    /** Seçilen aralıkta soru kalmadığında sunucunun önerdiği daha geniş aralık. */
    val suggestedRange: IntRange? = null,
    val questionShownAtMs: Long = 0,
    val solved: Int = 0,
    val correct: Int = 0,
    val consecutiveCorrects: Int = 0,
    val consecutiveWrongs: Int = 0,
    val startLevel: Double? = null,
    val currentLevel: Double? = null,
    val isFinished: Boolean = false,
    /** Tekrar oturumunda sıradaki sorular (sunucuya sorulmaz, listeden gelir). */
    val reviewQueue: List<QuestionDto> = emptyList()
) {
    /** "Beni Zorla" hedefi; sunucudaki kuralın aynısı (3 doğru %50, 3 yanlış %75, yoksa %60). */
    val challengeTargetPercent: Int
        get() = when {
            consecutiveWrongs >= 3 -> 75
            consecutiveCorrects >= 3 -> 50
            else -> 60
        }
}

class PracticeViewModel(private val client: ApiClient) : ViewModel() {

    private val _home = MutableStateFlow(HomeState())
    val home: StateFlow<HomeState> = _home.asStateFlow()

    private val _session = MutableStateFlow<SessionState?>(null)
    val session: StateFlow<SessionState?> = _session.asStateFlow()

    /** Sınav seçimi kullanıcının hedef sınavıyla başlar; giriş sonrası bir kez çağrılır. */
    fun initialize(targetExamId: String?) {
        if (_home.value.catalog.isNotEmpty()) return
        _home.update { it.copy(filter = it.filter.copy(examId = targetExamId)) }
        refresh()
    }

    fun refresh() {
        _home.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            if (_home.value.catalog.isEmpty()) {
                when (val catalog = client.call { catalog() }) {
                    is ApiResult.Success -> _home.update { state ->
                        // Hedef sınav katalogda yoksa ilk sınavla başla
                        val examId = state.filter.examId?.takeIf { id -> catalog.value.any { it.id == id } }
                            ?: catalog.value.firstOrNull()?.id
                        state.copy(catalog = catalog.value, filter = state.filter.copy(examId = examId))
                    }
                    is ApiResult.Failure -> {
                        _home.update { it.copy(isLoading = false, error = catalog.message) }
                        return@launch
                    }
                }
            }
            loadCountsAndStats()
        }
    }

    fun selectExam(examId: String) = updateFilter { it.copy(examId = examId, subjectId = null, topicId = null) }
    fun selectSubject(subjectId: String?) = updateFilter { it.copy(subjectId = subjectId, topicId = null) }
    fun selectTopic(topicId: String?) = updateFilter { it.copy(topicId = topicId) }

    fun selectBucket(bucket: Int) {
        val b = bucket.coerceIn(1, 10)
        _home.update { it.copy(filter = it.filter.copy(minBucket = b, maxBucket = b)) }
    }

    fun selectRange(min: Int, max: Int) {
        val low = minOf(min, max).coerceIn(1, 10)
        val high = maxOf(min, max).coerceIn(1, 10)
        _home.update { it.copy(filter = it.filter.copy(minBucket = low, maxBucket = high)) }
    }

    fun setIncludeSolved(include: Boolean) = _home.update { it.copy(filter = it.filter.copy(includeSolved = include)) }

    fun startPractice(startLevel: Double?) = startSession(SessionType.PRACTICE, startLevel)

    /** Yanlışlarım veya favorilerdeki soruları sırayla tekrar çözdürür. */
    fun startReview(questions: List<QuestionDto>, startLevel: Double?) {
        if (questions.isEmpty()) return
        _session.value = SessionState(
            type = SessionType.REVIEW,
            filter = _home.value.filter,
            question = questions.first(),
            reviewQueue = questions.drop(1),
            questionShownAtMs = System.currentTimeMillis(),
            startLevel = startLevel,
            currentLevel = startLevel
        )
    }
    fun startChallenge(startLevel: Double?) = startSession(SessionType.CHALLENGE, startLevel)

    fun selectOption(index: Int) = _session.update { s ->
        if (s == null || s.evaluation != null) s else s.copy(selectedOption = index)
    }

    fun submitAnswer() {
        val s = _session.value ?: return
        val question = s.question ?: return
        val option = s.selectedOption ?: return
        if (s.evaluation != null || s.isLoading) return

        _session.update { it?.copy(isLoading = true, error = null) }
        val elapsed = (System.currentTimeMillis() - s.questionShownAtMs).coerceAtLeast(0)
        viewModelScope.launch {
            val result = client.call {
                submitAttempt(question.id, AttemptRequest(option, elapsed, s.type.name))
            }
            _session.update { current ->
                current ?: return@update null
                when (result) {
                    is ApiResult.Success -> {
                        val eval = result.value
                        // Tekrarda doğru çözülen soru öğrenilmiş sayılır ve yanlışlardan çıkar
                        if (current.type == SessionType.REVIEW && eval.isCorrect) {
                            viewModelScope.launch { client.call { clearWrongNote(question.id) } }
                        }
                        current.copy(
                            evaluation = eval,
                            isLoading = false,
                            solved = current.solved + 1,
                            correct = current.correct + if (eval.isCorrect) 1 else 0,
                            consecutiveCorrects = if (eval.isCorrect) current.consecutiveCorrects + 1 else 0,
                            consecutiveWrongs = if (eval.isCorrect) 0 else current.consecutiveWrongs + 1,
                            startLevel = current.startLevel ?: eval.oldLevel,
                            currentLevel = eval.newLevel
                        )
                    }
                    is ApiResult.Failure -> current.copy(isLoading = false, error = result.message)
                }
            }
        }
    }

    fun nextQuestion() {
        val s = _session.value ?: return
        loadQuestion(s.filter)
    }

    fun expandToRange(range: IntRange) {
        val s = _session.value ?: return
        val widened = s.filter.copy(minBucket = range.first, maxBucket = range.last)
        _home.update { it.copy(filter = it.filter.copy(minBucket = range.first, maxBucket = range.last)) }
        _session.update { it?.copy(filter = widened) }
        loadQuestion(widened)
    }

    fun toggleFavorite() {
        val question = _session.value?.question ?: return
        viewModelScope.launch {
            when (val result = client.call { toggleFavorite(question.id) }) {
                is ApiResult.Success -> _session.update { s ->
                    s?.copy(question = s.question?.copy(isFavorite = result.value.isFavorite))
                }
                is ApiResult.Failure -> _session.update { it?.copy(error = result.message) }
            }
        }
    }

    /** Sonucu banner olarak göstermek için mesajı döndürür. */
    fun report(reason: ReportReason, note: String, onResult: (String) -> Unit) {
        val question = _session.value?.question ?: return
        viewModelScope.launch {
            when (val result = client.call { report(question.id, ReportRequest(reason.name, note)) }) {
                is ApiResult.Success -> {
                    onResult(result.value.message)
                    // Havuzdan çekildiyse bu soruda kalmanın anlamı yok
                    if (result.value.flagged) nextQuestion()
                }
                is ApiResult.Failure -> onResult(result.message)
            }
        }
    }

    fun finishSession() = _session.update { it?.copy(isFinished = true) }

    fun closeSession() {
        _session.value = null
        loadCountsAndStats()
    }

    fun dismissSessionError() = _session.update { it?.copy(error = null) }

    private fun startSession(type: SessionType, startLevel: Double?) {
        val filter = _home.value.filter
        _session.value = SessionState(type = type, filter = filter, startLevel = startLevel, currentLevel = startLevel)
        loadQuestion(filter)
    }

    private fun loadQuestion(filter: PracticeFilter) {
        val type = _session.value?.type ?: return
        if (type == SessionType.REVIEW) {
            _session.update { s ->
                s ?: return@update null
                val next = s.reviewQueue.firstOrNull()
                if (next == null) s.copy(isFinished = true)
                else s.copy(
                    question = next,
                    reviewQueue = s.reviewQueue.drop(1),
                    selectedOption = null,
                    evaluation = null,
                    error = null,
                    questionShownAtMs = System.currentTimeMillis()
                )
            }
            return
        }
        _session.update { it?.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val practice = type == SessionType.PRACTICE
            val result = client.call {
                nextQuestion(
                    mode = type.name,
                    examId = filter.examId,
                    subjectId = filter.subjectId,
                    topicId = filter.topicId,
                    minBucket = filter.minBucket.takeIf { practice },
                    maxBucket = filter.maxBucket.takeIf { practice },
                    includeSolved = filter.includeSolved.takeIf { practice }
                )
            }
            _session.update { s ->
                s ?: return@update null
                when (result) {
                    is ApiResult.Success -> {
                        val next = result.value
                        s.copy(
                            question = next.question,
                            selectedOption = null,
                            evaluation = null,
                            isLoading = false,
                            suggestedRange = next.suggestedRange?.let { it.min..it.max },
                            questionShownAtMs = System.currentTimeMillis(),
                            // Ne soru ne öneri varsa havuz bitti: özet göster
                            isFinished = next.question == null && next.suggestedRange == null && s.solved > 0
                        )
                    }
                    is ApiResult.Failure -> s.copy(isLoading = false, error = result.message)
                }
            }
        }
    }

    private fun updateFilter(change: (PracticeFilter) -> PracticeFilter) {
        _home.update { it.copy(filter = change(it.filter)) }
        loadCountsAndStats()
    }

    private fun loadCountsAndStats() {
        val filter = _home.value.filter
        _home.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val counts = client.call { bucketCounts(filter.examId, filter.subjectId, filter.topicId) }
            val stats = client.call { stats() }
            _home.update { state ->
                state.copy(
                    bucketCounts = (counts as? ApiResult.Success)?.value ?: state.bucketCounts,
                    stats = (stats as? ApiResult.Success)?.value ?: state.stats,
                    isLoading = false,
                    error = (counts as? ApiResult.Failure)?.message ?: (stats as? ApiResult.Failure)?.message
                )
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                PracticeViewModel((this[APPLICATION_KEY] as SoruHavuzuApplication).apiClient)
            }
        }
    }
}
