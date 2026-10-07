package com.workeasy.erp.user.repository

import com.workeasy.erp.user.entity.Department
import com.workeasy.erp.user.entity.Employee
import com.workeasy.erp.user.entity.Role
import com.workeasy.erp.user.entity.UserAccount
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface DepartmentRepository : JpaRepository<Department, UUID>, JpaSpecificationExecutor<Department>

@Repository
interface EmployeeRepository : JpaRepository<Employee, UUID>, JpaSpecificationExecutor<Employee> {
    fun countByDepartmentIdAndDeletedAtIsNull(departmentId: UUID): Long
}

@Repository
interface UserRepository : JpaRepository<UserAccount, UUID> {
    fun findByUsername(username: String): UserAccount?
}

@Repository
interface RoleRepository : JpaRepository<Role, UUID> {
    fun findByCode(code: String): Role?
}
