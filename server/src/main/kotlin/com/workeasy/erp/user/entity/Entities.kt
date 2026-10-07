package com.workeasy.erp.user.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "department")
class Department(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(nullable = false)
    var name: String = "",

    @Column(name = "parent_id")
    var parentId: UUID? = null,

    var path: String? = null,

    var level: Int = 1,

    @Column(name = "manager_id")
    var managerId: UUID? = null,

    @Column(name = "sort_no")
    var sortNo: Int = 0,

    var phone: String? = null,

    @Column(name = "created_at")
    var createdAt: Instant? = null,

    @Column(name = "updated_at")
    var updatedAt: Instant? = null,

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,
)

@Entity
@Table(name = "employee")
class Employee(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "user_id")
    var userId: UUID? = null,

    @Column(name = "department_id")
    var departmentId: UUID? = null,

    @Column(name = "employee_no")
    var employeeNo: String? = null,

    @Column(name = "real_name", nullable = false)
    var realName: String = "",

    var gender: String? = null,

    var birthday: LocalDate? = null,

    var phone: String? = null,

    var email: String? = null,

    var position: String? = null,

    @Column(name = "manager_id")
    var managerId: UUID? = null,

    @Column(name = "employment_type")
    var employmentType: String = "fulltime",

    @Column(name = "hire_date")
    var hireDate: LocalDate? = null,

    @Column(name = "probation_end")
    var probationEnd: LocalDate? = null,

    @Column(name = "regular_date")
    var regularDate: LocalDate? = null,

    @Column(name = "entry_status")
    var entryStatus: String = "probation",

    @Column(name = "leave_date")
    var leaveDate: LocalDate? = null,

    var status: String = "active",

    var salary: java.math.BigDecimal? = null,

    @Column(name = "created_at")
    var createdAt: Instant? = null,

    @Column(name = "updated_at")
    var updatedAt: Instant? = null,

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,
)

@Entity
@Table(name = "users")
class UserAccount(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    var username: String? = null,

    @Column(name = "password_hash")
    var passwordHash: String? = null,

    @Column(name = "employee_id")
    var employeeId: UUID? = null,

    var status: String = "active",

    @Column(name = "last_login")
    var lastLogin: Instant? = null,

    @Column(name = "created_at")
    var createdAt: Instant? = null,

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,
)

@Entity
@Table(name = "role")
class Role(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    var code: String = "",

    var name: String = "",

    @Column(name = "data_scope")
    var dataScope: String = "self",

    var description: String? = null,
)
