package app.soruhavuzu

import app.soruhavuzu.domain.CalibrationEngine
import app.soruhavuzu.domain.CalibrationEngine.AttemptInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalibrationEngineTest {

    @Test
    fun `dogru cevap theta'yi artirir ve soruyu kolaylastirir, yanlis tersini yapar`() {
        val logit = CalibrationEngine.initialLogit(7, 0.0)

        val correct = CalibrationEngine.evaluateFirstAttempt(AttemptInput(0.0, logit, 0, 0, isCorrect = true))
        assertTrue(correct.newTheta > 0.0)
        assertTrue(correct.newQuestionLogit < logit)

        val wrong = CalibrationEngine.evaluateFirstAttempt(AttemptInput(0.0, logit, 0, 0, isCorrect = false))
        assertTrue(wrong.newTheta < 0.0)
        assertTrue(wrong.newQuestionLogit > logit)
    }

    @Test
    fun `cok cozulmus sorunun zorlugu daha az oynar`() {
        val fresh = CalibrationEngine.evaluateFirstAttempt(AttemptInput(0.0, 0.0, 0, 0, isCorrect = false))
        val mature = CalibrationEngine.evaluateFirstAttempt(AttemptInput(0.0, 0.0, 45, 20, isCorrect = false))

        assertTrue(mature.newQuestionLogit - 0.0 < (fresh.newQuestionLogit - 0.0) / 5)
    }

    @Test
    fun `kova sinirlari 1 ile 10 arasinda kalir ve logit araligiyla tutarlidir`() {
        assertEquals(1, CalibrationEngine.logitToBucket(-99.0))
        assertEquals(10, CalibrationEngine.logitToBucket(99.0))
        for (bucket in 1..10) {
            val range = CalibrationEngine.bucketToLogitRange(bucket)
            assertEquals(bucket, CalibrationEngine.logitToBucket((range.start + range.endInclusive) / 2))
        }
    }

    @Test
    fun `abartili puan veren yukleyenin baslangic tahmini asagi cekilir`() {
        assertTrue(CalibrationEngine.initialLogit(8, uploaderBias = 1.0) < CalibrationEngine.initialLogit(8, 0.0))
    }

    @Test
    fun `soru puanindan kolay cikarsa yanlilik pozitife gider`() {
        // 9 puan verilmiş (beklenen +2.1) ama gerçekte orta zorlukta (0.0) çıkmış
        val bias = CalibrationEngine.updatedUploaderBias(currentBias = 0.0, uploaderRating = 9, calibratedLogit = 0.0)
        assertTrue(bias > 0.0)
        assertTrue(CalibrationEngine.shouldUpdateUploaderBias(10))
        assertTrue(!CalibrationEngine.shouldUpdateUploaderBias(9))
    }

    @Test
    fun `beni zorla hedefi seriye gore degisir ve hedef logit dogru hesaplanir`() {
        assertEquals(0.60, CalibrationEngine.challengeTargetProbability(0, 0), 0.001)
        assertEquals(0.75, CalibrationEngine.challengeTargetProbability(0, 3), 0.001)
        assertEquals(0.50, CalibrationEngine.challengeTargetProbability(3, 0), 0.001)

        val target = CalibrationEngine.targetLogitForProbability(theta = 0.5, targetProbability = 0.6)
        assertEquals(0.6, CalibrationEngine.expectedSuccessProbability(0.5, target), 0.0001)
    }

    @Test
    fun `cogunluk celdiriciyi secerse anahtar suphesi olusur`() {
        val reason = assertNotNull(CalibrationEngine.detectKeySuspect(listOf(3, 16, 1, 0, 0), correctOptionIndex = 0))
        assertTrue(reason.contains("B şıkkını"))

        assertNull(CalibrationEngine.detectKeySuspect(listOf(1, 2, 0, 0, 0), correctOptionIndex = 0), "az veride karar verilmez")
        assertNull(CalibrationEngine.detectKeySuspect(listOf(12, 3, 2, 1, 0), correctOptionIndex = 0))
    }
}
