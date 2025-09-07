package main.java.test

import main.java.parser.BUAAParser
import java.io.File

fun main() {
    val source = File("getMyScheduleDetail.do.json")
        .readText()
    BUAAParser(source).apply {
        generateCourseList()
        saveCourse()
        println(generateTimeTable())
    }
}