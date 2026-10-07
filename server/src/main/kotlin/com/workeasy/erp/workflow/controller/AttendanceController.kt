package com.workeasy.erp.workflow.controller

import com.workeasy.erp.common.ApiResponse
import com.workeasy.erp.common.PageResult
import com.workeasy.erp.workflow.AttendanceService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class PunchRequest(val type: String = "in", val location: String? = null)
data class ShiftRequest(
    val name: String = "",
    val workStart: String? = null,
    val workEnd: String? = null,
    val lateThreshold: Int? = null,
)
data class ScheduleRequest(
    val employeeId: String? = null,
    val shiftId: String? = null,
    val workDate: String? = null,
)

@RestController
@RequestMapping("/attendance")
class AttendanceController(private val service: AttendanceService) {

    @PostMapping("/punch")
    fun punch(@RequestBody(required = false) req: PunchRequest?): ApiResponse<Boolean> {
        service.punch(req?.type ?: "in", req?.location)
        return ApiResponse.ok(true)
    }

    @GetMapping("/punches")
    fun punches(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) scope: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "8") size: Int,
    ): ApiResponse<PageResult<Map<String, Any?>>> =
        ApiResponse.ok(service.punches(q, scope, page, size))

    @GetMapping("/daily")
    fun daily(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) scope: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "8") size: Int,
    ): ApiResponse<PageResult<Map<String, Any?>>> =
        ApiResponse.ok(service.daily(q, scope, page, size))

    @GetMapping("/shifts")
    fun shifts(
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "8") size: Int,
    ): ApiResponse<PageResult<Map<String, Any?>>> =
        ApiResponse.ok(service.shifts(q, page, size))

    @PostMapping("/shifts")
    fun createShift(@RequestBody req: ShiftRequest): ApiResponse<Boolean> {
        service.createShift(req.name, req.workStart, req.workEnd, req.lateThreshold)
        return ApiResponse.ok(true)
    }

    @GetMapping("/schedules")
    fun schedules(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) scope: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "8") size: Int,
    ): ApiResponse<PageResult<Map<String, Any?>>> =
        ApiResponse.ok(service.schedules(q, scope, page, size))

    @PostMapping("/schedules")
    fun createSchedule(@RequestBody req: ScheduleRequest): ApiResponse<Boolean> {
        service.createSchedule(req.employeeId, req.shiftId, req.workDate)
        return ApiResponse.ok(true)
    }
}
