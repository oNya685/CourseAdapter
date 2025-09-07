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
 * @param source 从 API 获取的原始 JSON 字符串。
 * @author PandZz
 * @date 2024/03/02
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
     * 内部数据类，用于临时存储从字符串中解析出的教师和周次信息。
     *
     * @property teacher 教师姓名。
     * @property startWeek 开始周。
     * @property endWeek 结束周。
     * @property type 周次类型 (0: 每周, 1: 单周, 2: 双周)。
     */
    private data class TeacherAndWeek(
        val teacher: String,
        val startWeek: Int,
        val endWeek: Int,
        val type: Int
    ) {
        companion object {
            const val TYPE_ALL = 0  // 每周
            const val TYPE_ODD = 1  // 单周
            const val TYPE_EVEN = 2 // 双周
        }
    }

    /**
     * 解析单个课程项目，将其转换为一个或多个 [Course] 对象。
     *
     * 一个 CourseItem 可能因为有多个老师或不同的上课周次（例如，"李四[1-8周] 王五[9-16周]"）
     * 而需要被解析成多个 Course 对象。
     *
     * @param courseItem 从 JSON 解析出的原始课程项。
     * @return 解析后的 [Course] 对象列表。
     */
    private fun parseCourseItem(courseItem: BUAACourseInfo.Datas.CourseItem): List<Course> {
        // "cellDetail[1].text" 字段通常包含教师和周次信息，格式如 "张三[1-16周(单)] 李四[1-16周(双)]"
        return courseItem.cellDetail[1].text.split(" ").mapNotNull { teacherAndWeeksString ->
            parseTeacherAndWeek(teacherAndWeeksString)?.let { teacherAndWeek ->
                Course(
                    name = courseItem.courseName,
                    day = courseItem.dayOfWeek,
                    room = courseItem.placeName,
                    teacher = teacherAndWeek.teacher,
                    startNode = courseItem.beginSection,
                    endNode = courseItem.endSection,
                    startWeek = teacherAndWeek.startWeek,
                    endWeek = teacherAndWeek.endWeek,
                    type = teacherAndWeek.type,
                    credit = courseItem.credit.toFloatOrNull() ?: 0f,
                    note = courseItem.titleDetail.getOrElse(8) { "" },
                    startTime = courseItem.startTime,
                    endTime = courseItem.endTime
                )
            }
        }
    }

    /**
     * 解析包含教师姓名、起止周和周次类型的字符串。
     *
     * 例如: "张三[1-16周(单)]" -> TeacherAndWeek("张三", 1, 16, 1)
     *
     * @param input 待解析的字符串。
     * @return 如果解析成功，返回 [TeacherAndWeek] 对象；否则返回 `null`。
     */
    private fun parseTeacherAndWeek(input: String): TeacherAndWeek? {
        return teacherAndWeekRegex.find(input)?.let { matchResult ->
            val (teacher, beginWeekStr, endWeekStr, typeStr) = matchResult.destructured
            val type = when (typeStr) {
                "单" -> TeacherAndWeek.TYPE_ODD
                "双" -> TeacherAndWeek.TYPE_EVEN
                else -> TeacherAndWeek.TYPE_ALL
            }
            TeacherAndWeek(
                teacher = teacher,
                startWeek = beginWeekStr.toInt(),
                endWeek = endWeekStr.toInt(),
                type = type
            )
        }
    }

    companion object {
        /**
         * 用于从字符串中提取教师姓名、开始周、结束周和单双周类型的正则表达式。
         * - Group 1: (.+) -> 教师姓名
         * - Group 2: (\d+) -> 开始周
         * - Group 3: (\d+) -> 结束周
         * - Group 4: ([单双]) -> 单双周标识 (可选)
         */
        private const val TEACHER_AND_WEEK_REGEX_PATTERN = """^(.+)\[(\d+)-(\d+)周(?:\(([单双])\))?]$"""
        private val teacherAndWeekRegex = Regex(TEACHER_AND_WEEK_REGEX_PATTERN)
    }
}