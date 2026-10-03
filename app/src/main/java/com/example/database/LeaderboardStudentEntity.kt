package com.example.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.api.LeaderboardUserInfo
import com.example.api.LeaderboardUserItem

@Entity(
    tableName = "leaderboard_students",
    indices = [
        Index(value = ["programId", "phaseId", "rank"]),
        Index(value = ["name"]),
        Index(value = ["phone"]),
        Index(value = ["college"])
    ]
)
data class LeaderboardStudentEntity(
    @PrimaryKey
    val id: String,
    val programId: String,
    val phaseId: String,
    val subjectId: String = "all",
    val rank: Int,
    val score: Int,
    val userId: String,
    val name: String,
    val phone: String? = null,
    val avatar: String? = null,
    val college: String? = null,
    val district: String? = null,
    val division: String? = null,
    val studyGroup: String? = null,
    val passingYear: String? = null,
    val roll: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toUserItem(): LeaderboardUserItem {
        val userInfo = LeaderboardUserInfo(
            id = userId.ifBlank { id },
            user_id = userId.ifBlank { id },
            name = name,
            full_name = name,
            avatar = avatar,
            college = college,
            school = college,
            phone = phone,
            mobile = phone,
            district = district,
            division = division,
            study_group = studyGroup,
            passing_year = passingYear,
            roll = roll
        )

        return LeaderboardUserItem(
            id = userId.ifBlank { id },
            user_id = userId.ifBlank { id },
            name = name,
            full_name = name,
            avatar = avatar,
            college = college,
            school = college,
            phone = phone,
            mobile = phone,
            district = district,
            division = division,
            study_group = studyGroup,
            passing_year = passingYear,
            roll_no = roll,
            rank = rank,
            score = score,
            marks = score,
            user = userInfo
        )
    }

    companion object {
        fun fromUserItem(
            item: LeaderboardUserItem,
            programId: String,
            phaseId: String,
            subjectId: String
        ): LeaderboardStudentEntity {
            val uId = item.effectiveUserId ?: ""
            val rk = item.rank ?: 0
            val nm = item.effectiveName
            val primaryKey = "${programId}_${phaseId}_${subjectId}_${if (uId.isNotBlank()) uId else "${rk}_${nm.hashCode()}"}"

            return LeaderboardStudentEntity(
                id = primaryKey,
                programId = programId,
                phaseId = phaseId,
                subjectId = if (subjectId.equals("ALL", ignoreCase = true)) "all" else subjectId,
                rank = rk,
                score = item.effectiveScore,
                userId = uId,
                name = nm,
                phone = item.effectivePhone,
                avatar = item.effectiveAvatar,
                college = item.effectiveCollege,
                district = item.user?.district ?: item.district,
                division = item.user?.division ?: item.division,
                studyGroup = item.user?.study_group ?: item.study_group ?: item.group,
                passingYear = item.user?.passing_year ?: item.passing_year ?: item.batch,
                roll = item.effectiveRoll,
                updatedAt = System.currentTimeMillis()
            )
        }
    }
}
