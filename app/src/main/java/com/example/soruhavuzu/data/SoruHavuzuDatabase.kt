package com.example.soruhavuzu.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SoruHavuzuDao {

    @Query("SELECT * FROM exams ORDER BY id ASC")
    fun observeExams(): Flow<List<ExamEntity>>

    @Query("SELECT * FROM topics ORDER BY orderIndex ASC")
    fun observeTopics(): Flow<List<TopicEntity>>

    @Query("SELECT * FROM topics ORDER BY orderIndex ASC")
    suspend fun getAllTopics(): List<TopicEntity>

    @Query("SELECT * FROM questions ORDER BY createdAt DESC")
    fun observeAllQuestions(): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM questions WHERE id = :questionId LIMIT 1")
    suspend fun getQuestionById(questionId: String): QuestionEntity?

    @Query("SELECT * FROM questions WHERE contentHash = :hash AND status != 'REMOVED' LIMIT 1")
    suspend fun findDuplicateByContentHash(hash: String): QuestionEntity?

    @Query("SELECT * FROM question_versions")
    fun observeAllVersions(): Flow<List<QuestionVersionEntity>>

    @Query("SELECT * FROM question_versions WHERE id = :versionId LIMIT 1")
    suspend fun getVersionById(versionId: String): QuestionVersionEntity?

    @Query("SELECT * FROM question_versions WHERE questionId = :questionId ORDER BY versionNumber DESC")
    suspend fun getVersionsForQuestion(questionId: String): List<QuestionVersionEntity>

    @Query("SELECT * FROM attempts WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeUserAttempts(userId: String): Flow<List<AttemptEntity>>

    @Query("SELECT COUNT(*) FROM attempts WHERE userId = :userId AND questionId = :questionId")
    suspend fun countUserAttemptsForQuestion(userId: String, questionId: String): Int

    @Query("SELECT * FROM user_profile WHERE id = 'active_user' LIMIT 1")
    fun observeUserProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 'active_user' LIMIT 1")
    suspend fun getUserProfile(): UserProfileEntity?

    @Query("SELECT * FROM topic_mastery WHERE userId = :userId")
    fun observeTopicMastery(userId: String): Flow<List<TopicMasteryEntity>>

    @Query("SELECT * FROM topic_mastery WHERE userId = :userId AND topicId = :topicId LIMIT 1")
    suspend fun getTopicMastery(userId: String, topicId: String): TopicMasteryEntity?

    @Query("SELECT * FROM bookmarks WHERE userId = :userId")
    fun observeBookmarks(userId: String): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE userId = :userId AND questionId = :questionId LIMIT 1")
    suspend fun getBookmark(userId: String, questionId: String): BookmarkEntity?

    @Query("SELECT * FROM reputation_events WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeReputationEvents(userId: String): Flow<List<ReputationEventEntity>>

    @Query("SELECT * FROM reports ORDER BY createdAt DESC")
    fun observeReports(): Flow<List<ReportEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExams(exams: List<ExamEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopics(topics: List<TopicEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestion(question: QuestionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<QuestionEntity>)

    @Update
    suspend fun updateQuestion(question: QuestionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVersion(version: QuestionVersionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVersions(versions: List<QuestionVersionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttempt(attempt: AttemptEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(profile: UserProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopicMastery(mastery: TopicMasteryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReputationEvent(event: ReputationEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: ReportEntity)

    @Update
    suspend fun updateReport(report: ReportEntity)

    @Query("DELETE FROM attempts WHERE userId = :userId")
    suspend fun clearUserAttempts(userId: String)

    @Query("DELETE FROM bookmarks WHERE userId = :userId")
    suspend fun clearUserBookmarks(userId: String)

    @Query("DELETE FROM topic_mastery WHERE userId = :userId")
    suspend fun clearUserTopicMastery(userId: String)

    @Query("DELETE FROM reputation_events WHERE userId = :userId")
    suspend fun clearUserReputationEvents(userId: String)
}

@Database(
    entities = [
        ExamEntity::class,
        TopicEntity::class,
        QuestionEntity::class,
        QuestionVersionEntity::class,
        AttemptEntity::class,
        UserProfileEntity::class,
        TopicMasteryEntity::class,
        BookmarkEntity::class,
        ReputationEventEntity::class,
        ReportEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SoruHavuzuDatabase : RoomDatabase() {
    abstract fun dao(): SoruHavuzuDao
}
