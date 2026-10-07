package com.workeasy.erp.user

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import com.workeasy.erp.common.PageResult
import com.workeasy.erp.config.UserContext
import com.workeasy.erp.system.AuditService
import com.workeasy.erp.user.entity.Employee
import com.workeasy.erp.user.repository.DepartmentRepository
import com.workeasy.erp.user.repository.EmployeeRepository
import jakarta.persistence.criteria.Predicate
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class EmployeeDto(
    val id: String,
    val employeeNo: String? = null,
    val realName: String = "",
    val deptName: String? = null,
    val position: String? = null,
    val status: String = "active",
)

data class EmployeeCreateRequest(
    val employeeNo: String? = null,
    val realName: String = "",
    val departmentId: String? = null,
    val position: String? = null,
    val phone: String? = null,
    val hireDate: LocalDate? = null,
)

@Service
class EmployeeService(
    private val repo: EmployeeRepository,
    private val deptRepo: DepartmentRepository,
    private val scopeResolver: ScopeResolver,
    private val audit: AuditService,
) {

    /** 列表：搜索 + 数据范围 + 排序 + 分页（原型列表页五件套） */
    @Transactional(readOnly = true)
    fun list(
        q: String?,
        scope: String?,
        status: String?,
        page: Int,
        size: Int,
        sort: String?,
    ): PageResult<EmployeeDto> {
        val me = UserContext.get()
        val eff = scopeResolver.resolve(scope, me, "employee")

        val spec = Specification<Employee> { root, _, cb ->
            val preds = mutableListOf<Predicate>(cb.isNull(root.get<Instant>("deletedAt")))
            if (!q.isNullOrBlank()) {
                val like = "%${q.trim().lowercase()}%"
                preds.add(
                    cb.or(
                        cb.like(cb.lower(root.get("realName")), like),
                        cb.like(cb.lower(root.get("employeeNo")), like),
                        cb.like(cb.lower(root.get("position")), like),
                    )
                )
            }
            if (!status.isNullOrBlank()) preds.add(cb.equal(root.get<String>("status"), status))
            when (eff) {
                "mine" -> preds.add(
                    me.employeeId?.let { cb.equal(root.get<UUID>("id"), it) } ?: cb.disjunction()
                )
                "dept" -> preds.add(
                    me.deptId?.let { cb.equal(root.get<UUID>("departmentId"), it) } ?: cb.disjunction()
                )
            }
            cb.and(*preds.toTypedArray())
        }

        val paged = repo.findAll(spec, pageRequest(page, size, sort))
        val deptIds = paged.content.mapNotNull { it.departmentId }.distinct()
        val deptNames = if (deptIds.isEmpty()) {
            emptyMap<UUID, String>()
        } else {
            deptRepo.findAllById(deptIds).associate { it.id!! to it.name }
        }

        return PageResult.of(
            list = paged.content.map { it.toDto(it.departmentId?.let { id -> deptNames[id] }) },
            total = paged.totalElements,
            page = page,
            size = size,
        )
    }

    @Transactional
    fun create(req: EmployeeCreateRequest): EmployeeDto {
        val name = req.realName.trim()
        if (name.isBlank()) throw BizException(ErrorCode.BIZ_PARAM, "姓名不能为空")
        val now = Instant.now()
        val saved = repo.save(
            Employee(
                employeeNo = req.employeeNo,
                realName = name,
                departmentId = req.departmentId?.let { UUID.fromString(it) },
                position = req.position,
                phone = req.phone,
                hireDate = req.hireDate,
                status = "active",
                entryStatus = "probation",
                createdAt = now,
                updatedAt = now,
            )
        )
        audit.log("employee", "新增员工 $name", "employee", saved.id?.toString())
        return saved.toDto(null)
    }

    private fun Employee.toDto(deptName: String?) = EmployeeDto(
        id = id.toString(),
        employeeNo = employeeNo,
        realName = realName,
        deptName = deptName,
        position = position,
        status = status,
    )

    private fun pageRequest(page: Int, size: Int, sort: String?): PageRequest {
        val prop = when (sort?.substringBefore(',')) {
            "realName", "name" -> "realName"
            "hireDate" -> "hireDate"
            "status" -> "status"
            else -> "employeeNo"
        }
        val dir = if (sort?.contains("desc", ignoreCase = true) == true) Sort.Direction.DESC else Sort.Direction.ASC
        return PageRequest.of((page - 1).coerceAtLeast(0), size.coerceIn(1, 200), Sort.by(dir, prop))
    }
}
