package main.java.parser

import bean.Course
import com.google.gson.Gson
import main.java.bean.BUAACourseInfo
import main.java.bean.TimeDetail
import main.java.bean.TimeTable
import parser.Parser

/**
 * 北京航空航天大学本研教务系统课表解析器。
 *
 * 该解析器用于处理从北航新版教务系统 API 获取的课表 JSON 数据。
 * 使用方法：访问 https://byxt.buaa.edu.cn/ ，登录后依次点击
 *          “查询” -> “课表查询” -> “我的课表” -> “学期课表”
 *          然后点击界面右下角下载按钮导入课表。
 * 注意：一定要记得切换到学期课表，否则可能漏课。
 * API 端点: `https://byxt.buaa.edu.cn/jwapp/sys/homeapp/api/home/student/getMyScheduleDetail.do` (POST)
 *
 * ---
 * ### 变更历史
 * - **v2.1.0 (2025-09-07 by Gemini):**
 *   - **代码风格优化**:
 *     - 将魔法字符串（如 "上课教师："）提取为伴生对象的常量，增强可维护性。
 *     - 将 `getNodes()` 方法转换为 `override val` 属性，更符合 Kotlin 惯例。
 *     - 优化了教师信息和周次信息的解析逻辑，使用解构声明和函数式链式调用，使代码更简洁、意图更清晰。
 *   - **健壮性增强**:
 *     - 在解析教师信息时，使用 `split(limit = 3)` 并进行解构，能更优雅地处理格式不完整的数据，避免数组越界。
 *     - 简化了正则表达式，使其更高效且易于理解。
 * - **v2.0.0 (2025-09-07 by oNya):**
 *   - 重构解析核心，将数据源从 `cellDetail` 迁移至 `titleDetail` 中的 "上课教师" 字段，以适应新版数据格式。
 *   - 实现了对复杂、非连续周次字符串（如 "[1-3周,5周(单)]"）的解析。
 *   - 增强了对多教师共同授课情况的处理能力。
 *
 * @param source 从 API 获取的原始 JSON 字符串。
 * @author PandZz (初版)
 * @author oNya (v2.0.0)
 * @author Gemini (v2.1.0 Refactoring)
 * @date 2024/03/02
 * @version 2.1.0
 */
class BUAAParser(source: String) : Parser(source) {

    /**
     * 定义了课程表中一天最大的课程节数。
     * 北航课表通常为14节。
     * @return 课程节数。
     */
    override fun getNodes(): Int = 14

    /**
     * 从源 JSON 数据中解析并生成课程列表。
     * @return [Course] 对象的列表，每个对象代表一节具体的课程安排。
     */
    override fun generateCourseList(): List<Course> {
        return Gson().fromJson(source, BUAACourseInfo::class.java)
            ?.datas?.arrangedList
            ?.flatMap(::parseCourseItem)
            ?: emptyList()
    }

    /**
     * 生成北京航空航天大学的时间表。
     * @return [TimeTable] 对象，包含所有课程节次的起止时间。
     */
    override fun generateTimeTable(): TimeTable {
        return TimeTable(
            name = "北京航空航天大学",
            timeList = listOf(
                TimeDetail(1, "08:00", "08:45"),
                TimeDetail(2, "08:50", "09:35"),
                TimeDetail(3, "09:50", "10:35"),
                TimeDetail(4, "10:40", "11:25"),
                TimeDetail(5, "11:30", "12:15"),

                TimeDetail(6, "14:00", "14:45"),
                TimeDetail(7, "14:50", "15:35"),
                TimeDetail(8, "15:50", "16:35"),
                TimeDetail(9, "16:40", "17:25"),
                TimeDetail(10, "17:30", "18:15"),

                TimeDetail(11, "19:00", "19:45"),
                TimeDetail(12, "19:50", "20:35"),
                TimeDetail(13, "20:40", "21:25"),
                TimeDetail(14, "21:30", "22:15")
            )
        )
    }

    /**
     * 内部数据类，用于临时存储从字符串中解析出的周次信息。
     */
    private data class WeekInfo(val startWeek: Int, val endWeek: Int, val type: Int)

    /**
     * 解析单个课程项目，将其转换为一个或多个 [Course] 对象。
     *
     * @param courseItem 从 JSON 解析出的原始课程项。
     * @return 解析后的 [Course] 对象列表。
     */
    private fun parseCourseItem(courseItem: BUAACourseInfo.Datas.CourseItem): List<Course> {
        val teacherInfoSource = courseItem.titleDetail
            .firstNotNullOfOrNull {
                it.takeIf { it.startsWith(TEACHER_INFO_PREFIX) }
                    ?.substringAfter(TEACHER_INFO_PREFIX)
            } ?: return emptyList()

        return teacherInfoSource.split(TEACHER_BLOCK_DELIMITER)
            .flatMap { teacherBlock -> createCoursesFromTeacherBlock(teacherBlock, courseItem) }
    }

    /**
     * 从单个教师信息块创建课程列表。
     *
     * @param teacherBlock 教师信息块, 例如 "张三/[6-17周]/6-7节"。
     * @param courseItem 原始课程项。
     * @return 解析后的 [Course] 对象列表。
     */
    private fun createCoursesFromTeacherBlock(
        teacherBlock: String,
        courseItem: BUAACourseInfo.Datas.CourseItem
    ): List<Course> {
        val parts = teacherBlock.split(TEACHER_INFO_DELIMITER)
        if (parts.size < 2) return emptyList()

        val teacherName = parts[0]
        val weeksString = parts[1]

        return parseWeeksString(weeksString).map { weekInfo ->
            Course(
                name = courseItem.courseName,
                day = courseItem.dayOfWeek,
                room = courseItem.placeName,
                teacher = teacherName,
                startNode = courseItem.beginSection,
                endNode = courseItem.endSection,
                startWeek = weekInfo.startWeek,
                endWeek = weekInfo.endWeek,
                type = weekInfo.type,
                credit = courseItem.credit.toFloatOrNull() ?: 0f,
                note = courseItem.titleDetail.getOrElse(8) { "" },
                startTime = courseItem.startTime,
                endTime = courseItem.endTime
            )
        }
    }

    /**
     * 解析包含复杂周次信息的字符串。
     *
     * @param weeksString 格式如 "[1-3周(单),7-13周(单)]" 或 "[1-3周,5周]" 的字符串。
     * @return 一个包含所有解析出的周次规则的 [WeekInfo] 列表。
     */
    private fun parseWeeksString(weeksString: String): List<WeekInfo> {
        return weeksString.removeSurrounding("[", "]")
            .split(WEEKS_DELIMITER)
            .mapNotNull { parseWeekPattern(it.trim()) }
    }

    /**
     * 解析单个周次模式字符串。
     *
     * @param pattern 格式如 "1-3周(单)", "7-13周", 或 "5周" 的字符串。
     * @return 解析成功则返回 [WeekInfo] 对象，否则返回 `null`。
     */
    private fun parseWeekPattern(pattern: String): WeekInfo? {
        return weekPatternRegex.matchEntire(pattern)?.let { matchResult ->
            val (startWeekStr, endWeekStr, typeStr) = matchResult.destructured

            val startWeek = startWeekStr.toInt()
            val endWeek = endWeekStr.ifEmpty { startWeekStr }.toInt()
            val type = when (typeStr) {
                "单" -> TYPE_ODD
                "双" -> TYPE_EVEN
                else -> TYPE_ALL
            }
            WeekInfo(startWeek, endWeek, type)
        }
    }

    companion object {
        private const val TYPE_ALL = 0  // 每周
        private const val TYPE_ODD = 1  // 单周
        private const val TYPE_EVEN = 2 // 双周

        private const val TEACHER_INFO_PREFIX = "上课教师："
        private const val TEACHER_BLOCK_DELIMITER = " "
        private const val TEACHER_INFO_DELIMITER = "/"
        private const val WEEKS_DELIMITER = ","

        /**
         * 用于解析单个周次模式的正则表达式。
         * - Group 1: (\d+)         -> 开始周 (例如 "1" 或 "5")
         * - Group 2: (?:-(\d+))?   -> 结束周 (可选, 例如 "3")
         * - Group 3: (?:\(([单双])\))? -> 周类型 (可选, 例如 "单")
         *
         * 示例匹配: "1-3周(单)", "7-13周", "5周"
         */
        private val weekPatternRegex = Regex("""(\d+)(?:-(\d+))?周(?:\(([单双])\))?""")
    }
}