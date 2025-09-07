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
 * API 端点: `https://byxt.buaa.edu.cn/jwapp/sys/homeapp/api/home/student/getMyScheduleDetail.do` (POST)
 *
 * ---
 * ### 变更历史
 * - **v2.0.0 (2025-09-07 by oNya):**
 *   - 重构解析核心，将数据源从 `cellDetail` 迁移至 `titleDetail` 中的 "上课教师" 字段，以适应新版数据格式。
 *   - 实现了对复杂、非连续周次字符串（如 "[1-3周,5周(单)]"）的解析。
 *   - 增强了对多教师共同授课情况的处理能力。
 *
 * @param source 从 API 获取的原始 JSON 字符串。
 * @author PandZz (初版)
 * @author oNya (v2.0.0)
 * @date 2024/03/02
 * @version 2.0.0
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
        val response = Gson().fromJson(source, BUAACourseInfo::class.java)
        // 将每个 CourseItem 转换为一个或多个 Course 对象，并最终合并成一个列表
        return response.datas.arrangedList.flatMap { courseItem ->
            parseCourseItem(courseItem)
        }
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
     *
     * @property startWeek 开始周。
     * @property endWeek 结束周。
     * @property type 周次类型 (0: 每周, 1: 单周, 2: 双周)。
     */
    private data class WeekInfo(val startWeek: Int, val endWeek: Int, val type: Int)

    /**
     * 解析单个课程项目，将其转换为一个或多个 [Course] 对象。
     *
     * 新逻辑从 `titleDetail` 中查找 "上课教师：" 字段，并解析其后复杂的教师和周次安排字符串。
     * 例如："上课教师：张三/[6-17周]/6-7节 李四/[1-3周,5周,6-10周(双)]/6-7节"
     *
     * @param courseItem 从 JSON 解析出的原始课程项。
     * @return 解析后的 [Course] 对象列表。
     */
    private fun parseCourseItem(courseItem: BUAACourseInfo.Datas.CourseItem): List<Course> {
        // 1. 从 titleDetail 找到包含教师信息的字符串
        val teacherInfoSource = courseItem.titleDetail
            .find { it.startsWith("上课教师：") }
            ?.substringAfter("上课教师：")
            ?: return emptyList() // 如果找不到信息，则返回空列表

        // 2. 按空格分割，处理多个教师或时间段的情况
        return teacherInfoSource.split(" ").flatMap { teacherBlock ->
            val parts = teacherBlock.split('/')
            if (parts.size < 2) return@flatMap emptyList<Course>()

            val teacherName = parts[0]
            val weeksString = parts[1]

            // 3. 解析周次字符串，这可能会产生多个 WeekInfo 对象
            val weekInfos = parseWeeksString(weeksString)

            // 4. 为每个解析出的周次信息创建一个 Course 对象
            weekInfos.map { weekInfo ->
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
                    note = courseItem.titleDetail.getOrElse(8) { "" }, // 安全地获取备注
                    startTime = courseItem.startTime,
                    endTime = courseItem.endTime
                )
            }
        }
    }

    /**
     * 解析包含复杂周次信息的字符串。
     *
     * @param weeksString 格式如 "[1-3周(单),7-13周(单)]" 或 "[1-3周,5周]" 的字符串。
     * @return 一个包含所有解析出的周次规则的 [WeekInfo] 列表。
     */
    private fun parseWeeksString(weeksString: String): List<WeekInfo> {
        return weeksString.removeSurrounding("[", "]").split(',').mapNotNull { pattern ->
            parseWeekPattern(pattern.trim())
        }
    }

    /**
     * 解析单个周次模式字符串。
     *
     * @param pattern 格式如 "1-3周(单)", "7-13周", 或 "5周" 的字符串。
     * @return 解析成功则返回 [WeekInfo] 对象，否则返回 `null`。
     */
    private fun parseWeekPattern(pattern: String): WeekInfo? {
        return weekPatternRegex.find(pattern)?.let { matchResult ->
            // 使用解构声明获取正则捕获组
            val (startWeekStr, endWeekStr, typeStr) = matchResult.destructured
            val startWeek = startWeekStr.toInt()
            // 如果 endWeekStr 为空（例如 "5周"），则结束周等于开始周
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