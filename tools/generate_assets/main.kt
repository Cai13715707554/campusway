package com.campusway.tools

import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate

// ---------------------------------------------------------------------------
// 零依赖的离线数据生成器
//
// 用途：生成 app/src/main/assets/data/ 下的示例学校数据 JSON。
//
// 为什么单独写一份而不是复用 app 里的 SampleData：
//   本文件只用 Kotlin 标准库，可以直接用 Android Studio 自带的 kotlinc 编译执行，
//   不需要 Android SDK、不需要联网、不需要 Gradle。
//
// 用法（在工程根目录执行）：
//   kotlinc tools/generate_assets/main.kt -include-runtime -d build/assets-gen.jar
//   java -jar build/assets-gen.jar .
//
// 重要：应用在缺少这些 JSON 时，会自动回退到代码内置的 SampleData，
// 功能完全一致。因此它们是"可选的随包数据"，主要作用是给学校一个
// 可以直接照着改的数据格式样例。
// ---------------------------------------------------------------------------

private const val B1 = "b1"
private const val B2 = "b2"

private data class Pt(val x: Float, val y: Float)
private data class Rect(val l: Float, val t: Float, val r: Float, val b: Float)

private class RoomRec(
    val id: String, val name: String, val short: String, val floorId: String,
    val buildingId: String, val rect: Rect, val kind: String
)

private class NodeRec(val id: String, val p: Pt)
private class EdgeRec(val from: String, val to: String)

private class FloorRec(
    val buildingId: String, val floorId: String, val level: Int, val name: String,
    val aspectRatio: Float, val rooms: List<RoomRec>, val nodes: List<NodeRec>, val edges: List<EdgeRec>
)

private class BuildingRec(
    val id: String, val name: String, val short: String, val rect: Rect, val floorCount: Int,
    val lat: Double, val lon: Double, val floors: List<FloorRec>
)

private class CourseRec(
    val id: String, val name: String, val teacher: String,
    val roomId: String, val colorIndex: Int, val note: String = ""
)

private class EntryRec(
    val courseId: String, val day: Int, val period: Int, val roomOverride: String = ""
)

// ------------------------------------------------------------------ JSON 工具

private fun str(s: String): String = buildString {
    append('"')
    s.forEach { c ->
        when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(c)
        }
    }
    append('"')
}

private fun f(v: Float): String = v.toString()

private fun rectJson(r: Rect, indent: String): String =
    "{\n$indent  \"left\": ${f(r.l)},\n$indent  \"top\": ${f(r.t)},\n" +
        "$indent  \"right\": ${f(r.r)},\n$indent  \"bottom\": ${f(r.b)}\n$indent}"

private fun pointJson(p: Pt): String =
    "{\n          \"x\": ${f(p.x)},\n          \"y\": ${f(p.y)}\n        }"

private fun roomJson(room: RoomRec): String = buildString {
    append("      {\n")
    append("        \"id\": ${str(room.id)},\n")
    append("        \"name\": ${str(room.name)},\n")
    append("        \"shortName\": ${str(room.short)},\n")
    append("        \"floorId\": ${str(room.floorId)},\n")
    append("        \"buildingId\": ${str(room.buildingId)},\n")
    append("        \"rect\": ${rectJson(room.rect, "        ")},\n")
    append("        \"polygon\": [],\n")
    append("        \"kind\": ${str(room.kind)},\n")
    append("        \"nearestExitId\": \"\",\n")
    append("        \"note\": \"\"\n")
    append("      }")
}

private fun floorJson(floor: FloorRec): String = buildString {
    append("    {\n")
    append("      \"buildingId\": ${str(floor.buildingId)},\n")
    append("      \"floorId\": ${str(floor.floorId)},\n")
    append("      \"level\": ${floor.level},\n")
    append("      \"name\": ${str(floor.name)},\n")
    append("      \"aspectRatio\": ${f(floor.aspectRatio)},\n")
    append("      \"baseImage\": \"\",\n")
    append("      \"rooms\": [\n")
    append(floor.rooms.joinToString(",\n") { roomJson(it) })
    append("\n      ],\n")
    append("      \"nodes\": [\n")
    append(
        floor.nodes.joinToString(",\n") {
            "        {\n          \"id\": ${str(it.id)},\n          \"point\": ${pointJson(it.p)}\n        }"
        }
    )
    append("\n      ],\n")
    append("      \"edges\": [\n")
    append(
        floor.edges.joinToString(",\n") {
            "        {\n          \"fromId\": ${str(it.from)},\n          \"toId\": ${str(it.to)}\n        }"
        }
    )
    append("\n      ]\n")
    append("    }")
}

private fun buildingJson(b: BuildingRec): String = buildString {
    append("    {\n")
    append("      \"id\": ${str(b.id)},\n")
    append("      \"name\": ${str(b.name)},\n")
    append("      \"rect\": ${rectJson(b.rect, "      ")},\n")
    append("      \"shortName\": ${str(b.short)},\n")
    append("      \"floorCount\": ${b.floorCount},\n")
    append("      \"latitude\": ${b.lat},\n")
    append("      \"longitude\": ${b.lon},\n")
    append("      \"model3dPath\": \"\",\n")
    append("      \"floors\": [\n")
    append(b.floors.joinToString(",\n") { floorJson(it) })
    append("\n    ]\n")
    append("    }")
}

// ------------------------------------------------------------------ 数据定义

private fun room(
    id: String, name: String, short: String, floorId: String,
    buildingId: String, rect: Rect, kind: String = "CLASSROOM"
) = RoomRec(id, name, short, floorId, buildingId, rect, kind)

private fun corridorNodes(prefix: String, y: Float, xs: List<Float>): List<NodeRec> =
    xs.mapIndexed { i, x -> NodeRec("$prefix-n$i", Pt(x, y)) }

private fun chainEdges(prefix: String, count: Int): List<EdgeRec> =
    (0 until count - 1).map { EdgeRec("$prefix-n$it", "$prefix-n${it + 1}") }

private fun building1(): BuildingRec {
    val floors = listOf(1, 2, 3).map { level ->
        val floorId = "b1-f$level"
        val prefix = "b1f$level"
        fun rid(no: String) = "$floorId-$no"
        val rooms = mutableListOf(
            room(rid("r101"), "10${level}教室", "10$level", floorId, B1, Rect(0.06f, 0.10f, 0.30f, 0.30f)),
            room(rid("r102"), "10${level + 1}教室", "10${level + 1}", floorId, B1, Rect(0.06f, 0.36f, 0.30f, 0.56f)),
            room(rid("r103"), "10${level + 2}教室", "10${level + 2}", floorId, B1, Rect(0.06f, 0.62f, 0.30f, 0.82f)),
            room(rid("r201"), "20${level}教室", "20$level", floorId, B1, Rect(0.70f, 0.10f, 0.94f, 0.30f)),
            room(rid("r202"), "20${level + 1}教室", "20${level + 1}", floorId, B1, Rect(0.70f, 0.36f, 0.94f, 0.56f)),
            room(rid("r302"), "30${level}专业教室", "30$level", floorId, B1, Rect(0.36f, 0.10f, 0.64f, 0.30f), "LAB"),
            room(rid("r401"), "40${level}办公室", "40$level", floorId, B1, Rect(0.34f, 0.56f, 0.66f, 0.80f), "OFFICE"),
            room(rid("rps"), "卫生间", "卫", floorId, B1, Rect(0.34f, 0.84f, 0.46f, 0.96f), "RESTROOM"),
            room(rid("rs1"), "东楼梯", "梯", floorId, B1, Rect(0.70f, 0.60f, 0.80f, 0.80f), "STAIRS"),
            room(rid("rs2"), "西楼梯", "梯", floorId, B1, Rect(0.52f, 0.84f, 0.64f, 0.96f), "STAIRS")
        )
        if (level == 1) {
            rooms += room(
                rid("gate"), "1 号教学楼正门", "正门", floorId, B1,
                Rect(0.40f, 0.90f, 0.60f, 1.0f), "EXIT"
            )
        }
        FloorRec(
            B1, floorId, level, "$level 层", 1.4f, rooms,
            corridorNodes(prefix, 0.45f, listOf(0.15f, 0.35f, 0.50f, 0.65f, 0.85f)),
            chainEdges(prefix, 5)
        )
    }
    return BuildingRec(
        B1, "1 号教学楼", "1 号楼", Rect(0.08f, 0.16f, 0.36f, 0.42f), 3,
        30.65810, 104.06590, floors
    )
}

private fun building2(): BuildingRec {
    val floors = listOf(1, 2).map { level ->
        val floorId = "b2-f$level"
        val prefix = "b2f$level"
        fun rid(no: String) = "$floorId-$no"
        val rooms = mutableListOf(
            room(rid("r101"), "综合实训室 A", "实A", floorId, B2, Rect(0.06f, 0.12f, 0.42f, 0.46f), "LAB"),
            room(rid("r102"), "综合实训室 B", "实B", floorId, B2, Rect(0.06f, 0.54f, 0.42f, 0.88f), "LAB"),
            room(rid("r201"), "机房 ${level}0$level", "机${level}0$level", floorId, B2, Rect(0.54f, 0.12f, 0.94f, 0.42f), "LAB"),
            room(rid("r202"), "器材室", "器材", floorId, B2, Rect(0.54f, 0.50f, 0.80f, 0.72f), "OTHER"),
            room(rid("rps"), "卫生间", "卫", floorId, B2, Rect(0.82f, 0.50f, 0.94f, 0.72f), "RESTROOM"),
            room(rid("rs1"), "楼梯", "梯", floorId, B2, Rect(0.44f, 0.06f, 0.52f, 0.22f), "STAIRS")
        )
        if (level == 1) {
            rooms += room(
                rid("gate"), "2 号实训楼入口", "入口", floorId, B2,
                Rect(0.40f, 0.90f, 0.60f, 1.0f), "EXIT"
            )
        }
        FloorRec(
            B2, floorId, level, "$level 层", 1.3f, rooms,
            corridorNodes(prefix, 0.50f, listOf(0.20f, 0.48f, 0.70f, 0.88f)),
            chainEdges(prefix, 4)
        )
    }
    return BuildingRec(
        B2, "2 号实训楼", "2 号楼", Rect(0.44f, 0.16f, 0.70f, 0.40f), 2,
        30.65842, 104.06645, floors
    )
}

private fun campusJson(): String {
    val landmarks = listOf(
        Triple("lm-gate", "学校正门", Rect(0.44f, 0.90f, 0.56f, 1.0f)),
        Triple("lm-canteen", "第一食堂", Rect(0.76f, 0.56f, 0.94f, 0.74f)),
        Triple("lm-dorm", "学生宿舍 3 栋", Rect(0.76f, 0.20f, 0.94f, 0.40f)),
        Triple("lm-lib", "图书馆", Rect(0.08f, 0.56f, 0.30f, 0.76f)),
        Triple("lm-gym", "体育馆", Rect(0.40f, 0.60f, 0.66f, 0.84f)),
        Triple("lm-office", "行政楼", Rect(0.08f, 0.06f, 0.30f, 0.24f))
    )
    val kinds = listOf("GATE", "CANTEEN", "DORMITORY", "LIBRARY", "GYM", "OFFICE")
    val geo = listOf(
        30.65720 to 104.06610, 30.65780 to 104.06705, 30.65890 to 104.06710,
        30.65770 to 104.06520, 30.65740 to 104.06620, 30.65900 to 104.06530
    )

    return buildString {
        append("{\n")
        append("  \"name\": \"示例职业学校\",\n")
        append("  \"aspectRatio\": 1.5,\n")
        append("  \"baseImage\": \"\",\n")
        append("  \"landmarks\": [\n")
        append(
            landmarks.mapIndexed { i, (id, name, rect) ->
                "    {\n      \"id\": ${str(id)},\n      \"name\": ${str(name)},\n" +
                    "      \"rect\": ${rectJson(rect, "      ")},\n" +
                    "      \"kind\": ${str(kinds[i])},\n" +
                    "      \"latitude\": ${geo[i].first},\n" +
                    "      \"longitude\": ${geo[i].second}\n    }"
            }.joinToString(",\n")
        )
        append("\n  ],\n")
        append("  \"buildings\": [\n")
        append(listOf(building1(), building2()).joinToString(",\n") { buildingJson(it) })
        append("\n  ],\n")
        append("  \"latitude\": 30.65825,\n")
        append("  \"longitude\": 104.06610\n")
        append("}")
    }
}

private fun courseTableJson(): String {
    val courses = listOf(
        CourseRec("c-net", "计算机网络", "王建国", "b1-f3-r302", 0, "带实训手册"),
        CourseRec("c-math", "数学", "李慧", "b1-f1-r101", 1),
        CourseRec("c-en", "英语", "张敏", "b1-f1-r102", 2),
        CourseRec("c-pe", "体育", "赵强", "", 3, "操场集合"),
        CourseRec("c-pol", "职业道德与法律", "陈晓", "b1-f2-r201", 4),
        CourseRec("c-cn", "语文", "刘芳", "b1-f2-r202", 5),
        CourseRec("c-practice", "网络综合实训", "王建国", "b2-f1-r101", 6, "两节连上"),
        CourseRec("c-db", "数据库基础", "孙立", "b2-f2-r201", 7)
    )
    val entries = listOf(
        EntryRec("c-math", 1, 1), EntryRec("c-net", 1, 2), EntryRec("c-en", 1, 3),
        EntryRec("c-pol", 1, 5), EntryRec("c-practice", 1, 6), EntryRec("c-practice", 1, 7),
        EntryRec("c-cn", 2, 1), EntryRec("c-math", 2, 2), EntryRec("c-net", 2, 3),
        EntryRec("c-net", 2, 4), EntryRec("c-pe", 2, 6),
        EntryRec("c-en", 3, 1), EntryRec("c-db", 3, 2), EntryRec("c-db", 3, 3),
        EntryRec("c-pol", 3, 5), EntryRec("c-cn", 3, 8),
        EntryRec("c-math", 4, 1), EntryRec("c-en", 4, 2),
        EntryRec("c-practice", 4, 3, "b2-f1-r101"), EntryRec("c-practice", 4, 4, "b2-f1-r101"),
        EntryRec("c-pe", 4, 7),
        EntryRec("c-net", 5, 1), EntryRec("c-db", 5, 2), EntryRec("c-cn", 5, 3), EntryRec("c-pol", 5, 6)
    )

    return buildString {
        append("{\n  \"courses\": [\n")
        append(
            courses.joinToString(",\n") { c ->
                "    {\n      \"id\": ${str(c.id)},\n      \"name\": ${str(c.name)},\n" +
                    "      \"teacher\": ${str(c.teacher)},\n      \"roomId\": ${str(c.roomId)},\n" +
                    "      \"colorIndex\": ${c.colorIndex},\n      \"note\": ${str(c.note)}\n    }"
            }
        )
        append("\n  ],\n  \"entries\": [\n")
        append(
            entries.joinToString(",\n") { e ->
                "    {\n      \"courseId\": ${str(e.courseId)},\n      \"dayOfWeek\": ${e.day},\n" +
                    "      \"periodIndex\": ${e.period},\n" +
                    (if (e.roomOverride.isBlank()) "" else "      \"roomIdOverride\": ${str(e.roomOverride)},\n") +
                    "      \"weeks\": null\n    }"
            }
        )
        append("\n  ]\n}")
    }
}

private fun timeTableJson(): String {
    val periods = listOf(
        Triple("第1节", "08:00" to "08:45", "上午"),
        Triple("第2节", "08:55" to "09:40", "上午"),
        Triple("第3节", "10:00" to "10:45", "上午"),
        Triple("第4节", "10:55" to "11:40", "上午"),
        Triple("第5节", "14:00" to "14:45", "下午"),
        Triple("第6节", "14:55" to "15:40", "下午"),
        Triple("第7节", "16:00" to "16:45", "下午"),
        Triple("第8节", "19:00" to "19:45", "晚上"),
        Triple("第9节", "19:55" to "20:40", "晚上")
    )
    return buildString {
        append("{\n  \"name\": \"默认作息\",\n  \"periods\": [\n")
        append(
            periods.mapIndexed { i, (name, times, section) ->
                "    {\n      \"index\": ${i + 1},\n      \"name\": ${str(name)},\n" +
                    "      \"start\": ${str(times.first)},\n      \"end\": ${str(times.second)},\n" +
                    "      \"section\": ${str(section)}\n    }"
            }.joinToString(",\n")
        )
        append("\n  ]\n}")
    }
}

// ------------------------------------------------------------------ 入口

fun main(args: Array<String>) {
    val root = File(args.firstOrNull() ?: ".")
    val outDir = File(root, "app/src/main/assets/data")
    outDir.mkdirs()

    val monday = LocalDate.now().with(DayOfWeek.MONDAY).toString()
    val calendarJson = "{\n  \"calendar\": {\n    \"termStartDate\": ${str(monday)},\n    \"totalWeeks\": 20\n  }\n}"

    fun write(name: String, content: String) {
        val file = File(outDir, name)
        file.writeText(content + "\n")
        println("  ${file.name}  ${file.length()} 字节")
    }

    println("生成离线数据到 ${outDir.absolutePath}")
    write("campus.json", campusJson())
    write("course_table.json", courseTableJson())
    write("time_table.json", timeTableJson())
    write("calendar.json", calendarJson)
    println("完成。第 1 教学周周一 = $monday")
}
