package main.java.bean

/**
 * GSON 数据类，用于映射北航教务系统课表 API 的 JSON 响应。
 * 顶层结构。
 *
 * @property code 状态码，通常成功时为 0。
 * @property msg 响应信息。
 * @property datas 核心数据负载。
 */
data class BUAACourseInfo(
    val code: Int,
    val msg: String,
    val datas: Datas
) {
    /**
     * 包含所有课表列表的核心数据模型。
     *
     * @property arrangedList 已安排（有具体时间地点）的课程列表。
     * @property notArrangeList 未安排的课程列表。
     * @property practiceList 实践类课程列表。
     * @property code 未知用途的编码。
     * @property name 未知用途的名称。
     */
    data class Datas(
        val arrangedList: List<CourseItem>,
        val notArrangeList: List<CourseItem>,
        val practiceList: List<CourseItem>,
        val code: String,
        val name: String
    ) {
        /**
         * 代表一个具体的课程项目信息。
         *
         * @property week 星期（例如："星期一"），但通常使用 dayOfWeek。
         * @property courseCode 课程代码。
         * @property credit 学分。
         * @property courseName 课程名称。
         * @property byCode 未知用途编码。
         * @property beginSection 开始节次。
         * @property endSection 结束节次。
         * @property titleDetail 标题详情列表，包含课程的各种详细信息。
         * @property multiCourse 是否为多课程。
         * @property teachClassName 教学班名称。
         * @property placeName 上课地点。
         * @property teachingTarget 教学对象。
         * @property weeksAndTeachers 包含周次和教师的聚合字符串。
         * @property teachClassId 教学班ID。
         * @property cellDetail UI单元格的详细信息，通常包含教师和周次。
         * @property tags 课程标签。
         * @property courseSerialNo 课程序号。
         * @property startTime 课程开始时间 (格式 "HH:mm")。
         * @property endTime 课程结束时间 (格式 "HH:mm")。
         * @property color 用于UI显示的颜色代码。
         * @property dayOfWeek 星期几，一个整数 (例如，1代表周一)。
         */
        data class CourseItem(
            val week: String = "",
            val courseCode: String = "",
            val credit: String = "",
            val courseName: String = "",
            val byCode: String = "",
            val beginSection: Int = 0,
            val endSection: Int = 0,
            val titleDetail: List<String> = emptyList(),
            val multiCourse: String = "",
            val teachClassName: String = "",
            val placeName: String = "",
            val teachingTarget: String = "",
            val weeksAndTeachers: String = "",
            val teachClassId: String = "",
            val cellDetail: List<CellDetail> = emptyList(),
            val tags: List<String> = emptyList(),
            val courseSerialNo: String = "",
            val startTime: String = "",
            val endTime: String = "",
            val color: String = "",
            val dayOfWeek: Int = 0
        ) {
            /**
             * 课表UI单元格的显示细节。
             *
             * @property color 文本颜色。
             * @property text 显示的文本内容。
             */
            data class CellDetail(
                val color: String = "",
                val text: String = ""
            )
        }
    }
}