package com.workeasy.erp.user

import com.workeasy.erp.common.BizException
import com.workeasy.erp.common.ErrorCode
import com.workeasy.erp.common.PageResult
import com.workeasy.erp.system.AuditService
import com.workeasy.erp.user.entity.Department
import com.workeasy.erp.user.repository.DepartmentRepository
import com.workeasy.erp.user.repository.EmployeeRepository
import jakarta.persistence.criteria.Predicate
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class DepartmentDto(
    val id: String,
    val name: String,
    val managerName: String? = null,
    val count: Long = 0,
    val status: String = "active",
)

data class DepartmentCreateRequest(
    val name: String = "",
    val parentId: String? = null,
    val phone: String? = null,
    val sortNo: Int = 0,
)

@Service
class DepartmentService(
    private val repo: DepartmentRepository,
    private val empRepo: EmployeeRepository,
    private val audit: AuditService,
) {

    /** 列表：搜索 + 排序 + 分页（对应原型列表页五件套中的三项） */
    @Transactional(readOnly = true)
    fun list(q: String?, page: Int, size: Int, sort: String?): PageResult<DepartmentDto> {
        val spec = Specification<Department> { root, _, cb ->
            val preds = mutableListOf<Predicate>(cb.isNull(root.get<Instant>("deletedAt")))
            if (!q.isNullOrBlank()) {
                val like = "%${q.trim().lowercase()}%"
                preds.add(cb.like(cb.lower(root.get("name")), like))
            }
            cb.and(*preds.toTypedArray())
        }
        val paged = repo.findAll(spec, pageRequest(page, size, sort))

        // 批量补齐主管姓名与部门人数，避免 N+1
        val mgrIds = paged.content.mapNotNull { it.managerId }.distinct()
        val mgrNames = if (mgrIds.isEmpty()) {
            emptyMap<UUID, String>()
        } else {
            empRepo.findAllById(mgrIds).associate { it.id!! to it.realName }
        }

        return PageResult.of(
            list = paged.content.map {
                it.toDto(
                    managerName = it.managerId?.let { id -> mgrNames[id] },
                    count = empRepo.countByDepartmentIdAndDeletedAtIsNull(it.id!!),
                )
            },
            total = paged.totalElements,
            page = page,
            size = size,
        )
    }

    @Transactional
    fun create(req: DepartmentCreateRequest): DepartmentDto {
        val name = req.name.trim()
        if (name.isBlank()) throw BizException(ErrorCode.BIZ_PARAM, "部门名称不能为空")
        val now = Instant.now()
        val entity = Department(
            name = name,
            parentId = req.parentId?.let { UUID.fromString(it) },
            phone = req.phone,
            sortNo = req.sortNo,
            level = if (req.parentId.isNullOrBlank()) 1 else 2,
            createdAt = now,
            updatedAt = now,
        )
        val saved = repo.save(entity)
        audit.log("dept", "新增部门 $name", "department", saved.id?.toString())
        return saved.toDto(count = 0)
    }

    private fun Department.toDto(managerName: String? = null, count: Long = 0) = DepartmentDto(
        id = id.toString(),
        name = name,
        managerName = managerName,
        count = count,
        status = if (deletedAt == null) "active" else "deleted",
    )

    private fun pageRequest(page: Int, size: Int, sort: String?): PageRequest {
        val prop = when (sort?.substringBefore(',')) {
            "name" -> "name"
            "count", "sortNo" -> "sortNo"
            else -> "sortNo"
        }
        val dir = if (sort?.contains("desc", ignoreCase = true) == true) Sort.Direction.DESC else Sort.Direction.ASC
        return PageRequest.of((page - 1).coerceAtLeast(0), size.coerceIn(1, 200), Sort.by(dir, prop))
    }
}
