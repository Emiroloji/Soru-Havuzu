package com.example

import com.example.soruhavuzu.data.QuestionEntity
import com.example.soruhavuzu.data.QuestionStatus
import com.example.soruhavuzu.domain.CalibrationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun calibrationEngine_firstAttemptUpdatesThetaAndDifficulty_repeatDoesNot() {
        val initialLogit = CalibrationEngine.initialLogitFromUploaderRating(7)
        val q = QuestionEntity(
            id = "q_test",
            uploaderId = "u1",
            uploaderName = "Teacher",
            uploaderRole = "EDITOR",
            topicId = "top_fonksiyonlar",
            currentVersionId = "v1",
            status = QuestionStatus.ACTIVE.name,
            uploaderRating = 7,
            difficultyLogit = initialLogit,
            difficultyBucket = 7,
            attemptCount = 0,
            firstAttemptCount = 0,
            correctFirstCount = 0,
            keySuspect = false,
            keySuspectReason = null,
            weightedReportScore = 0,
            contentHash = "abc",
            imagePhash = null,
            optionSelectionCountsCsv = "0,0,0,0,0",
            createdAt = 0L
        )

        // First attempt: correct answer by student with theta = 0.0
        val stepFirst = CalibrationEngine.evaluateAttempt(
            userTheta = 0.0,
            question = q,
            selectedOptionIndex = 2,
            correctOptionIndex = 2,
            optionCount = 5,
            isFirstAttempt = true
        )
        assertTrue("Student theta should increase on correct first attempt", stepFirst.newUserTheta > 0.0)
        assertTrue("Question difficulty logit should decrease when solved correctly", stepFirst.newQuestionLogit < initialLogit)

        // Repeat attempt (isFirst = false): should NOT alter theta or question difficulty
        val stepRepeat = CalibrationEngine.evaluateAttempt(
            userTheta = stepFirst.newUserTheta,
            question = q.copy(difficultyLogit = stepFirst.newQuestionLogit),
            selectedOptionIndex = 0,
            correctOptionIndex = 2,
            optionCount = 5,
            isFirstAttempt = false
        )
        assertEquals(stepFirst.newUserTheta, stepRepeat.newUserTheta, 0.0001)
        assertEquals(stepFirst.newQuestionLogit, stepRepeat.newQuestionLogit, 0.0001)
    }

    @Test
    fun challengeMode_adaptsTargetProbabilityAfterThreeStreaks() {
        assertEquals(0.60, CalibrationEngine.computeChallengeTargetProbability(0, 0), 0.001)
        assertEquals(0.75, CalibrationEngine.computeChallengeTargetProbability(0, 3), 0.001)
        assertEquals(0.50, CalibrationEngine.computeChallengeTargetProbability(3, 0), 0.001)
    }

    @Test
    fun copyrightCheck_blocksOsymKeywords() {
        val blocked = CalibrationEngine.checkCopyrightAndQuality(
            bodyText = "2023 TYT ÖSYM çıkmış soru: f(x) = 3x + 1 ise f(2) kaçtır?",
            solutionText = "f(2) = 3(2) + 1 = 7 bulunur."
        )
        assertTrue(blocked.isBlockedCopyright)

        val valid = CalibrationEngine.checkCopyrightAndQuality(
            bodyText = "Gerçek sayılarda tanımlı f(x) = 3x + 1 fonksiyonu için f(2) değeri kaçtır?",
            solutionText = "x yerine 2 yazılırsa f(2) = 3(2) + 1 = 7 bulunur."
        )
        assertFalse(valid.isBlockedCopyright)
    }
}
